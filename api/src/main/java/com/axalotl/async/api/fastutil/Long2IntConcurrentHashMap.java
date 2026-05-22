package com.axalotl.async.api.fastutil;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntCollection;
import it.unimi.dsi.fastutil.longs.AbstractLong2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.ObjectIterator;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.LongFunction;

public final class Long2IntConcurrentHashMap extends AbstractLong2IntMap {

    private static final long EMPTY     = Long.MIN_VALUE;
    private static final long TOMBSTONE = Long.MIN_VALUE + 1;

    private static final int NSEG_BITS;
    private static final int NSEG;
    private static final int SEGMASK;
    static {
        int cores = Runtime.getRuntime().availableProcessors();
        int n = Math.max(8, Math.min(64, cores * 2));
        int p = Integer.highestOneBit(n - 1) << 1;
        NSEG = p;
        NSEG_BITS = Integer.numberOfTrailingZeros(p);
        SEGMASK = p - 1;
    }

    private static final VarHandle KEYS_VH = MethodHandles.arrayElementVarHandle(long[].class);
    private static final VarHandle VALS_VH = MethodHandles.arrayElementVarHandle(int[].class);

    private static int hash(long h) {
        h *= 0xC6BC279692B5C323L;
        return (int) (h ^ (h >>> 32));
    }

    private static final class State {
        final long[] keys;
        final int[]  vals;
        final int    mask;
        State(int capPow2) {
            keys = new long[capPow2];
            vals = new int[capPow2];
            mask = capPow2 - 1;
            Arrays.fill(keys, EMPTY);
        }
    }

    private static final class Segment {
        volatile State state;
        volatile int size;
        int tombstones;
        Segment(int capPow2) { state = new State(capPow2); }
    }

    private final Segment[] segments;

    public Long2IntConcurrentHashMap() { this(64); }
    public Long2IntConcurrentHashMap(int expectedSize) {
        segments = new Segment[NSEG];
        int perSegEntries = Math.max(8, expectedSize / NSEG);
        int cap = 16;
        while (cap < perSegEntries * 2) cap <<= 1;
        for (int i = 0; i < NSEG; i++) segments[i] = new Segment(cap);
    }
    public Long2IntConcurrentHashMap(int initialCapacity, float loadFactor) { this(initialCapacity); }
    public Long2IntConcurrentHashMap(int initialCapacity, float loadFactor, int concurrencyLevel) { this(initialCapacity); }
    public Long2IntConcurrentHashMap(int initialCapacity, int concurrencyLevel) { this(initialCapacity); }

    private Segment segmentFor(long key) {
        return segments[(hash(key) >>> (32 - NSEG_BITS)) & SEGMASK];
    }

    @Override public int get(long key) {
        if (key == EMPTY || key == TOMBSTONE) return defRetValue;
        Segment seg = segmentFor(key);
        State s = seg.state;
        long[] k = s.keys;
        int[]  v = s.vals;
        int mask = s.mask;
        int idx = hash(key) & mask;
        while (true) {
            long kk = (long) KEYS_VH.getAcquire(k, idx);
            if (kk == EMPTY) return defRetValue;
            if (kk == key) return (int) VALS_VH.getAcquire(v, idx);
            idx = (idx + 1) & mask;
        }
    }

    @Override public int put(long key, int value) {
        if (key == EMPTY || key == TOMBSTONE) throw new IllegalArgumentException("reserved key");
        Segment seg = segmentFor(key);
        synchronized (seg) {
            State s = seg.state;
            if (needsResize(seg, s)) s = resizeLocked(seg);
            return putLocked(seg, s, key, value, false);
        }
    }

    @Override public int putIfAbsent(long key, int value) {
        if (key == EMPTY || key == TOMBSTONE) throw new IllegalArgumentException("reserved key");
        Segment seg = segmentFor(key);
        synchronized (seg) {
            State s = seg.state;
            if (needsResize(seg, s)) s = resizeLocked(seg);
            return putLocked(seg, s, key, value, true);
        }
    }

    @Override public int remove(long key) {
        if (key == EMPTY || key == TOMBSTONE) return defRetValue;
        Segment seg = segmentFor(key);
        synchronized (seg) {
            return removeLocked(seg, seg.state, key, false, 0);
        }
    }

    @Override public boolean remove(long key, int value) {
        if (key == EMPTY || key == TOMBSTONE) return false;
        Segment seg = segmentFor(key);
        synchronized (seg) {
            return removeLocked(seg, seg.state, key, true, value) != defRetValue;
        }
    }

    @Override public boolean replace(long key, int oldValue, int newValue) {
        if (key == EMPTY || key == TOMBSTONE) return false;
        Segment seg = segmentFor(key);
        synchronized (seg) {
            State s = seg.state;
            int idx = findKey(s, key);
            if (idx < 0) return false;
            if (s.vals[idx] != oldValue) return false;
            VALS_VH.setRelease(s.vals, idx, newValue);
            return true;
        }
    }

    @Override public int replace(long key, int value) {
        if (key == EMPTY || key == TOMBSTONE) return defRetValue;
        Segment seg = segmentFor(key);
        synchronized (seg) {
            State s = seg.state;
            int idx = findKey(s, key);
            if (idx < 0) return defRetValue;
            int prev = s.vals[idx];
            VALS_VH.setRelease(s.vals, idx, value);
            return prev;
        }
    }

    @Override public boolean containsKey(long key) {
        if (key == EMPTY || key == TOMBSTONE) return false;
        Segment seg = segmentFor(key);
        State s = seg.state;
        long[] k = s.keys;
        int mask = s.mask;
        int idx = hash(key) & mask;
        while (true) {
            long kk = (long) KEYS_VH.getAcquire(k, idx);
            if (kk == EMPTY) return false;
            if (kk == key) return true;
            idx = (idx + 1) & mask;
        }
    }

    @Override public boolean containsValue(int value) {
        for (Segment seg : segments) {
            State s = seg.state;
            for (int i = 0; i <= s.mask; i++) {
                long k = s.keys[i];
                if (k != EMPTY && k != TOMBSTONE && s.vals[i] == value) return true;
            }
        }
        return false;
    }

    @Override public int getOrDefault(long key, int defaultValue) {
        if (key == EMPTY || key == TOMBSTONE) return defaultValue;
        Segment seg = segmentFor(key);
        State s = seg.state;
        long[] k = s.keys; int[] v = s.vals; int mask = s.mask;
        int idx = hash(key) & mask;
        while (true) {
            long kk = (long) KEYS_VH.getAcquire(k, idx);
            if (kk == EMPTY) return defaultValue;
            if (kk == key) return (int) VALS_VH.getAcquire(v, idx);
            idx = (idx + 1) & mask;
        }
    }

    @Override public int compute(long key, BiFunction<? super Long, ? super Integer, ? extends Integer> remappingFunction) {
        if (key == EMPTY || key == TOMBSTONE) throw new IllegalArgumentException("reserved key");
        Segment seg = segmentFor(key);
        synchronized (seg) {
            State s = seg.state;
            int idx = findKey(s, key);
            Integer oldVal = (idx >= 0) ? s.vals[idx] : null;
            Integer nv = remappingFunction.apply(key, oldVal);
            if (nv == null) {
                if (idx >= 0) {
                    KEYS_VH.setRelease(s.keys, idx, TOMBSTONE);
                    seg.size--;
                    seg.tombstones++;
                }
                return defRetValue;
            }
            if (idx >= 0) {
                VALS_VH.setRelease(s.vals, idx, nv);
            } else {
                if (needsResize(seg, s)) s = resizeLocked(seg);
                putLocked(seg, s, key, nv, false);
            }
            return nv;
        }
    }

    @Override public int computeIfPresent(long key, BiFunction<? super Long, ? super Integer, ? extends Integer> remappingFunction) {
        if (key == EMPTY || key == TOMBSTONE) return defRetValue;
        Segment seg = segmentFor(key);
        synchronized (seg) {
            State s = seg.state;
            int idx = findKey(s, key);
            if (idx < 0) return defRetValue;
            Integer nv = remappingFunction.apply(key, s.vals[idx]);
            if (nv == null) {
                KEYS_VH.setRelease(s.keys, idx, TOMBSTONE);
                seg.size--;
                seg.tombstones++;
                return defRetValue;
            }
            VALS_VH.setRelease(s.vals, idx, nv);
            return nv;
        }
    }

    public int computeIfAbsent(long key, LongFunction<? extends Integer> mappingFunction) {
        if (key == EMPTY || key == TOMBSTONE) throw new IllegalArgumentException("reserved key");
        int existing = get(key);
        if (existing != defRetValue) return existing;
        Segment seg = segmentFor(key);
        synchronized (seg) {
            State s = seg.state;
            int idx = findKey(s, key);
            if (idx >= 0) return s.vals[idx];
            Integer nv = mappingFunction.apply(key);
            if (nv == null) return defRetValue;
            if (needsResize(seg, s)) s = resizeLocked(seg);
            putLocked(seg, s, key, nv, false);
            return nv;
        }
    }

    @Override public int merge(long key, int value, BiFunction<? super Integer, ? super Integer, ? extends Integer> remappingFunction) {
        if (key == EMPTY || key == TOMBSTONE) throw new IllegalArgumentException("reserved key");
        Segment seg = segmentFor(key);
        synchronized (seg) {
            State s = seg.state;
            int idx = findKey(s, key);
            int nv;
            if (idx >= 0) {
                Integer res = remappingFunction.apply(s.vals[idx], value);
                if (res == null) {
                    KEYS_VH.setRelease(s.keys, idx, TOMBSTONE);
                    seg.size--;
                    seg.tombstones++;
                    return defRetValue;
                }
                nv = res;
                VALS_VH.setRelease(s.vals, idx, nv);
            } else {
                nv = value;
                if (needsResize(seg, s)) s = resizeLocked(seg);
                putLocked(seg, s, key, nv, false);
            }
            return nv;
        }
    }

    public int addTo(long key, int increment) {
        if (key == EMPTY || key == TOMBSTONE) throw new IllegalArgumentException("reserved key");
        Segment seg = segmentFor(key);
        synchronized (seg) {
            State s = seg.state;
            int idx = findKey(s, key);
            if (idx >= 0) {
                int newVal = s.vals[idx] + increment;
                VALS_VH.setRelease(s.vals, idx, newVal);
                return newVal;
            }
            if (needsResize(seg, s)) s = resizeLocked(seg);
            putLocked(seg, s, key, increment, false);
            return increment;
        }
    }

    @Override public void putAll(Map<? extends Long, ? extends Integer> m) {
        for (Map.Entry<? extends Long, ? extends Integer> e : m.entrySet())
            put(e.getKey().longValue(), e.getValue().intValue());
    }

    @Override public void clear() {
        for (Segment seg : segments) {
            synchronized (seg) {
                State s = seg.state;
                Arrays.fill(s.keys, EMPTY);
                seg.size = 0;
                seg.tombstones = 0;
            }
        }
    }

    @Override public int size() {
        long total = 0;
        for (Segment seg : segments) total += seg.size;
        return (int) Math.max(0L, Math.min(total, Integer.MAX_VALUE));
    }

    @Override public boolean isEmpty() { return size() == 0; }

    private static int findKey(State s, long key) {
        long[] k = s.keys;
        int mask = s.mask;
        int idx = hash(key) & mask;
        while (true) {
            long kk = k[idx];
            if (kk == EMPTY) return -1;
            if (kk == key) return idx;
            idx = (idx + 1) & mask;
        }
    }

    private int putLocked(Segment seg, State s, long key, int value, boolean ifAbsent) {
        long[] k = s.keys;
        int[]  v = s.vals;
        int mask = s.mask;
        int idx = hash(key) & mask;
        int firstTomb = -1;
        while (true) {
            long kk = k[idx];
            if (kk == EMPTY) {
                int writeIdx = firstTomb >= 0 ? firstTomb : idx;
                if (firstTomb >= 0) seg.tombstones--;
                KEYS_VH.setRelease(k, writeIdx, key);
                VALS_VH.setRelease(v, writeIdx, value);
                seg.size++;
                return defRetValue;
            }
            if (kk == TOMBSTONE) {
                if (firstTomb < 0) firstTomb = idx;
            } else if (kk == key) {
                int prev = v[idx];
                if (!ifAbsent) {
                    VALS_VH.setRelease(v, idx, value);
                }
                return prev;
            }
            idx = (idx + 1) & mask;
        }
    }

    private int removeLocked(Segment seg, State s, long key, boolean checkValue, int expVal) {
        long[] k = s.keys;
        int[]  v = s.vals;
        int mask = s.mask;
        int idx = hash(key) & mask;
        while (true) {
            long kk = k[idx];
            if (kk == EMPTY) return defRetValue;
            if (kk == key) {
                int prev = v[idx];
                if (checkValue && prev != expVal) return defRetValue;
                KEYS_VH.setRelease(k, idx, TOMBSTONE);
                seg.size--;
                seg.tombstones++;
                return prev;
            }
            idx = (idx + 1) & mask;
        }
    }

    private boolean needsResize(Segment seg, State s) {
        return (seg.size + seg.tombstones + 1) > ((s.mask + 1) >> 1);
    }

    private State resizeLocked(Segment seg) {
        State old = seg.state;
        int newCap;
        if (seg.tombstones > seg.size) newCap = old.mask + 1;
        else                            newCap = (old.mask + 1) << 1;
        State ns = new State(newCap);
        long[] ok = old.keys; int[] ov = old.vals;
        long[] nk = ns.keys;  int[] nv = ns.vals;
        int nmask = ns.mask;
        for (int i = 0; i <= old.mask; i++) {
            long kk = ok[i];
            if (kk == EMPTY || kk == TOMBSTONE) continue;
            int idx = hash(kk) & nmask;
            while (nk[idx] != EMPTY) idx = (idx + 1) & nmask;
            nk[idx] = kk;
            nv[idx] = ov[i];
        }
        seg.state = ns;
        seg.tombstones = 0;
        return ns;
    }

    @Override public LongSet keySet() {
        LongOpenHashSet set = new LongOpenHashSet(size());
        for (Segment seg : segments) {
            State s = seg.state;
            for (int i = 0; i <= s.mask; i++) {
                long k = s.keys[i];
                if (k != EMPTY && k != TOMBSTONE) set.add(k);
            }
        }
        return set;
    }

    @Override public IntCollection values() {
        IntArrayList list = new IntArrayList(size());
        for (Segment seg : segments) {
            State s = seg.state;
            for (int i = 0; i <= s.mask; i++) {
                long k = s.keys[i];
                if (k != EMPTY && k != TOMBSTONE) list.add(s.vals[i]);
            }
        }
        return list;
    }

    @Override public FastEntrySet long2IntEntrySet() { return new SnapshotFastEntrySet(); }

    private List<Entry> snapshotEntries() {
        List<Entry> snap = new ArrayList<>(size());
        for (Segment seg : segments) {
            State s = seg.state;
            for (int i = 0; i <= s.mask; i++) {
                long k = s.keys[i];
                if (k != EMPTY && k != TOMBSTONE) snap.add(new ImmutableEntry(k, s.vals[i]));
            }
        }
        return snap;
    }

    private final class SnapshotFastEntrySet implements FastEntrySet {
        @Override public ObjectIterator<Entry> iterator() {
            List<Entry> snap = snapshotEntries();
            return new ObjectIterator<>() {
                final Iterator<Entry> it = snap.iterator();
                Entry last;
                @Override public boolean hasNext() { return it.hasNext(); }
                @Override public Entry next() { last = it.next(); return last; }
                @Override public void remove() {
                    if (last == null) throw new IllegalStateException();
                    Long2IntConcurrentHashMap.this.remove(last.getLongKey());
                    last = null;
                }
            };
        }
        @Override public ObjectIterator<Entry> fastIterator() { return iterator(); }
        @Override public void fastForEach(Consumer<? super Entry> action) {
            for (Entry e : snapshotEntries()) action.accept(e);
        }
        @Override public int size() { return Long2IntConcurrentHashMap.this.size(); }
        @Override public boolean isEmpty() { return Long2IntConcurrentHashMap.this.isEmpty(); }
        @Override public void clear() { Long2IntConcurrentHashMap.this.clear(); }
        @Override public boolean contains(Object o) {
            if (!(o instanceof Entry en)) return false;
            int v = get(en.getLongKey());
            return v != defRetValue && v == en.getIntValue();
        }
        @Override public boolean remove(Object o) {
            if (!(o instanceof Entry en)) return false;
            return Long2IntConcurrentHashMap.this.remove(en.getLongKey(), en.getIntValue());
        }
        @Override public boolean add(Entry e) {
            int prev = put(e.getLongKey(), e.getIntValue());
            return prev != e.getIntValue();
        }
        @Override public Object[] toArray() { return snapshotEntries().toArray(); }
        @Override public <T> T[] toArray(T[] a) { return snapshotEntries().toArray(a); }
        @Override public boolean containsAll(Collection<?> c) { for (Object o : c) if (!contains(o)) return false; return true; }
        @Override public boolean addAll(Collection<? extends Entry> c) { boolean m = false; for (Entry e : c) m |= add(e); return m; }
        @Override public boolean removeAll(Collection<?> c) { boolean m = false; for (Object o : c) m |= remove(o); return m; }
        @Override public boolean retainAll(Collection<?> c) { throw new UnsupportedOperationException(); }
    }

    private record ImmutableEntry(long key, int value) implements Entry {
        @Override public long getLongKey() { return key; }
        @Override public int getIntValue() { return value; }
        @Override public int setValue(int value) { throw new UnsupportedOperationException(); }
        @Override public boolean equals(Object o) {
            if (!(o instanceof Entry e)) return false;
            return key == e.getLongKey() && value == e.getIntValue();
        }
        @Override public int hashCode() { return Long.hashCode(key) ^ Integer.hashCode(value); }
    }

    @Override public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof Map<?, ?> other)) return false;
        if (other.size() != size()) return false;
        for (Entry e : long2IntEntrySet()) {
            Object ov = other.get(e.getLongKey());
            if (!(ov instanceof Integer) || ((Integer) ov) != e.getIntValue()) return false;
        }
        return true;
    }
    @Override public int hashCode() {
        int h = 0;
        for (Entry e : long2IntEntrySet())
            h += Long.hashCode(e.getLongKey()) ^ Integer.hashCode(e.getIntValue());
        return h;
    }
    @Override public String toString() { return "Long2IntConcurrentHashMap[size=" + size() + "]"; }
}
