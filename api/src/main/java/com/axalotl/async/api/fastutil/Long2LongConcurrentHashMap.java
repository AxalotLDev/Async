package com.axalotl.async.api.fastutil;

import it.unimi.dsi.fastutil.longs.AbstractLong2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongCollection;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.ObjectSet;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.LongUnaryOperator;

public final class Long2LongConcurrentHashMap extends AbstractLong2LongMap {

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
    private static final VarHandle VALS_VH = MethodHandles.arrayElementVarHandle(long[].class);

    private static int hash(long h) {
        h *= 0xC6BC279692B5C323L;
        return (int) (h ^ (h >>> 32));
    }

    private static final class State {
        final long[] keys;
        final long[] vals;
        final int    mask;
        State(int capPow2) {
            keys = new long[capPow2];
            vals = new long[capPow2];
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

    public Long2LongConcurrentHashMap() { this(64); }
    public Long2LongConcurrentHashMap(int expectedSize) {
        segments = new Segment[NSEG];
        int perSegEntries = Math.max(8, expectedSize / NSEG);
        int cap = 16;
        while (cap < perSegEntries * 2) cap <<= 1;
        for (int i = 0; i < NSEG; i++) segments[i] = new Segment(cap);
    }
    @SuppressWarnings("unused")
    public Long2LongConcurrentHashMap(int initialCapacity, float loadFactor) { this(initialCapacity); }

    private Segment segmentFor(long key) {
        return segments[(hash(key) >>> (32 - NSEG_BITS)) & SEGMASK];
    }

    @Override public long get(long key) {
        if (key == EMPTY || key == TOMBSTONE) return defaultReturnValue();
        Segment seg = segmentFor(key);
        State s = seg.state;
        long[] k = s.keys;
        long[] v = s.vals;
        int mask = s.mask;
        int idx = hash(key) & mask;
        while (true) {
            long kk = (long) KEYS_VH.getAcquire(k, idx);
            if (kk == EMPTY) return defaultReturnValue();
            if (kk == key) return (long) VALS_VH.getAcquire(v, idx);
            idx = (idx + 1) & mask;
        }
    }

    @Override public long put(long key, long value) {
        if (key == EMPTY || key == TOMBSTONE) throw new IllegalArgumentException("reserved key");
        Segment seg = segmentFor(key);
        synchronized (seg) {
            State s = seg.state;
            if (needsResize(seg, s)) s = resizeLocked(seg);
            return putLocked(seg, s, key, value, false);
        }
    }

    @Override public long putIfAbsent(long key, long value) {
        if (key == EMPTY || key == TOMBSTONE) throw new IllegalArgumentException("reserved key");
        Segment seg = segmentFor(key);
        synchronized (seg) {
            State s = seg.state;
            if (needsResize(seg, s)) s = resizeLocked(seg);
            return putLocked(seg, s, key, value, true);
        }
    }

    @Override public long remove(long key) {
        if (key == EMPTY || key == TOMBSTONE) return defaultReturnValue();
        Segment seg = segmentFor(key);
        synchronized (seg) {
            return removeLocked(seg, seg.state, key, false, 0L);
        }
    }

    @Override public boolean remove(long key, long value) {
        if (key == EMPTY || key == TOMBSTONE) return false;
        Segment seg = segmentFor(key);
        synchronized (seg) {
            return removeLocked(seg, seg.state, key, true, value) != defaultReturnValue();
        }
    }

    @Override public boolean replace(long key, long oldValue, long newValue) {
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

    @Override public long replace(long key, long value) {
        if (key == EMPTY || key == TOMBSTONE) return defaultReturnValue();
        Segment seg = segmentFor(key);
        synchronized (seg) {
            State s = seg.state;
            int idx = findKey(s, key);
            if (idx < 0) return defaultReturnValue();
            long prev = s.vals[idx];
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

    @Override public boolean containsValue(long value) {
        for (Segment seg : segments) {
            State s = seg.state;
            for (int i = 0; i <= s.mask; i++) {
                long k = s.keys[i];
                if (k != EMPTY && k != TOMBSTONE && s.vals[i] == value) return true;
            }
        }
        return false;
    }

    @Override public long getOrDefault(long key, long defaultValue) {
        if (key == EMPTY || key == TOMBSTONE) return defaultValue;
        Segment seg = segmentFor(key);
        State s = seg.state;
        long[] k = s.keys; long[] v = s.vals; int mask = s.mask;
        int idx = hash(key) & mask;
        while (true) {
            long kk = (long) KEYS_VH.getAcquire(k, idx);
            if (kk == EMPTY) return defaultValue;
            if (kk == key) return (long) VALS_VH.getAcquire(v, idx);
            idx = (idx + 1) & mask;
        }
    }

    @Override public long compute(long key, BiFunction<? super Long, ? super Long, ? extends Long> remappingFunction) {
        if (key == EMPTY || key == TOMBSTONE) throw new IllegalArgumentException("reserved key");
        Segment seg = segmentFor(key);
        synchronized (seg) {
            State s = seg.state;
            int idx = findKey(s, key);
            Long old = (idx >= 0) ? s.vals[idx] : null;
            Long nv = remappingFunction.apply(key, old);
            if (nv == null) {
                if (idx >= 0) {
                    KEYS_VH.setRelease(s.keys, idx, TOMBSTONE);
                    seg.size--; seg.tombstones++;
                }
                return defaultReturnValue();
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

    @Override public long computeIfAbsent(long key, LongUnaryOperator mappingFunction) {
        if (key == EMPTY || key == TOMBSTONE) throw new IllegalArgumentException("reserved key");
        long existing = get(key);
        if (existing != defaultReturnValue()) return existing;
        Segment seg = segmentFor(key);
        synchronized (seg) {
            State s = seg.state;
            int idx = findKey(s, key);
            if (idx >= 0) return s.vals[idx];
            long nv = mappingFunction.applyAsLong(key);
            if (needsResize(seg, s)) s = resizeLocked(seg);
            putLocked(seg, s, key, nv, false);
            return nv;
        }
    }

    @Override public long computeIfPresent(long key, BiFunction<? super Long, ? super Long, ? extends Long> remappingFunction) {
        if (key == EMPTY || key == TOMBSTONE) return defaultReturnValue();
        Segment seg = segmentFor(key);
        synchronized (seg) {
            State s = seg.state;
            int idx = findKey(s, key);
            if (idx < 0) return defaultReturnValue();
            Long nv = remappingFunction.apply(key, s.vals[idx]);
            if (nv == null) {
                KEYS_VH.setRelease(s.keys, idx, TOMBSTONE);
                seg.size--; seg.tombstones++;
                return defaultReturnValue();
            }
            VALS_VH.setRelease(s.vals, idx, nv);
            return nv;
        }
    }

    @Override public long merge(long key, long value, BiFunction<? super Long, ? super Long, ? extends Long> remappingFunction) {
        if (key == EMPTY || key == TOMBSTONE) throw new IllegalArgumentException("reserved key");
        Segment seg = segmentFor(key);
        synchronized (seg) {
            State s = seg.state;
            int idx = findKey(s, key);
            long nv;
            if (idx >= 0) {
                Long res = remappingFunction.apply(s.vals[idx], value);
                if (res == null) {
                    KEYS_VH.setRelease(s.keys, idx, TOMBSTONE);
                    seg.size--; seg.tombstones++;
                    return defaultReturnValue();
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

    @Override public void putAll(Map<? extends Long, ? extends Long> m) {
        for (Map.Entry<? extends Long, ? extends Long> e : m.entrySet())
            put(e.getKey().longValue(), e.getValue().longValue());
    }

    @Override public void clear() {
        for (Segment seg : segments) {
            synchronized (seg) {
                Arrays.fill(seg.state.keys, EMPTY);
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

    private long putLocked(Segment seg, State s, long key, long value, boolean ifAbsent) {
        long[] k = s.keys;
        long[] v = s.vals;
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
                return defaultReturnValue();
            }
            if (kk == TOMBSTONE) {
                if (firstTomb < 0) firstTomb = idx;
            } else if (kk == key) {
                long prev = v[idx];
                if (!ifAbsent) {
                    VALS_VH.setRelease(v, idx, value);
                }
                return prev;
            }
            idx = (idx + 1) & mask;
        }
    }

    private long removeLocked(Segment seg, State s, long key, boolean checkValue, long expVal) {
        long[] k = s.keys;
        long[] v = s.vals;
        int mask = s.mask;
        int idx = hash(key) & mask;
        while (true) {
            long kk = k[idx];
            if (kk == EMPTY) return defaultReturnValue();
            if (kk == key) {
                long prev = v[idx];
                if (checkValue && prev != expVal) return defaultReturnValue();
                KEYS_VH.setRelease(k, idx, TOMBSTONE);
                seg.size--; seg.tombstones++;
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
        long[] ok = old.keys; long[] ov = old.vals;
        long[] nk = ns.keys;  long[] nv = ns.vals;
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

    @Override public LongCollection values() {
        LongArrayList list = new LongArrayList(size());
        for (Segment seg : segments) {
            State s = seg.state;
            for (int i = 0; i <= s.mask; i++) {
                long k = s.keys[i];
                if (k != EMPTY && k != TOMBSTONE) list.add(s.vals[i]);
            }
        }
        return list;
    }

    @Override public ObjectSet<Long2LongMap.Entry> long2LongEntrySet() {
        List<Long2LongMap.Entry> snap = new ArrayList<>(size());
        for (Segment seg : segments) {
            State s = seg.state;
            for (int i = 0; i <= s.mask; i++) {
                long k = s.keys[i];
                if (k != EMPTY && k != TOMBSTONE) {
                    snap.add(new AbstractLong2LongMap.BasicEntry(k, s.vals[i]));
                }
            }
        }
        return new ObjectSet<>() {
            @Override public int size() { return snap.size(); }
            @Override public boolean isEmpty() { return snap.isEmpty(); }
            @Override public boolean contains(Object o) {
                if (!(o instanceof Long2LongMap.Entry en)) return false;
                long v = get(en.getLongKey());
                return v != defaultReturnValue() && v == en.getLongValue();
            }
            @Override public ObjectIterator<Long2LongMap.Entry> iterator() {
                Iterator<Long2LongMap.Entry> it = snap.iterator();
                return new ObjectIterator<>() {
                    private Long2LongMap.Entry last;
                    @Override public boolean hasNext() { return it.hasNext(); }
                    @Override public Long2LongMap.Entry next() { last = it.next(); return last; }
                    @Override public void remove() {
                        if (last == null) throw new IllegalStateException();
                        Long2LongConcurrentHashMap.this.remove(last.getLongKey(), last.getLongValue());
                        last = null;
                    }
                };
            }
            @Override public boolean add(Long2LongMap.Entry e) { throw new UnsupportedOperationException(); }
            @Override public boolean remove(Object o) {
                if (!(o instanceof Long2LongMap.Entry en)) return false;
                return Long2LongConcurrentHashMap.this.remove(en.getLongKey(), en.getLongValue());
            }
            @Override public void clear() { Long2LongConcurrentHashMap.this.clear(); }
            @Override public Object[] toArray() { return snap.toArray(); }
            @Override public <T> T[] toArray(T[] a) { return snap.toArray(a); }
            @Override public boolean containsAll(java.util.Collection<?> c) { for (Object o : c) if (!contains(o)) return false; return true; }
            @Override public boolean addAll(java.util.Collection<? extends Long2LongMap.Entry> c) { boolean m = false; for (Long2LongMap.Entry e : c) m |= add(e); return m; }
            @Override public boolean removeAll(java.util.Collection<?> c) { boolean m = false; for (Object o : c) m |= remove(o); return m; }
            @Override public boolean retainAll(java.util.Collection<?> c) { throw new UnsupportedOperationException(); }
        };
    }
}
