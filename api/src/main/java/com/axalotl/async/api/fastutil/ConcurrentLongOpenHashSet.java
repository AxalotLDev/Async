package com.axalotl.async.api.fastutil;

import it.unimi.dsi.fastutil.longs.AbstractLongSet;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongSet;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Arrays;

public final class ConcurrentLongOpenHashSet extends AbstractLongSet {

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

    private static int hash(long h) {
        h *= 0xC6BC279692B5C323L;
        return (int) (h ^ (h >>> 32));
    }

    private static final class State {
        final long[] keys;
        final int    mask;
        State(int capPow2) {
            keys = new long[capPow2];
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

    public ConcurrentLongOpenHashSet() { this(64); }
    public ConcurrentLongOpenHashSet(int expectedSize) {
        segments = new Segment[NSEG];
        int perSegEntries = Math.max(8, expectedSize / NSEG);
        int cap = 16;
        while (cap < perSegEntries * 2) cap <<= 1;
        for (int i = 0; i < NSEG; i++) segments[i] = new Segment(cap);
    }

    private Segment segmentFor(long key) {
        return segments[(hash(key) >>> (32 - NSEG_BITS)) & SEGMASK];
    }

    private static void checkKey(long key) {
        if (key == EMPTY || key == TOMBSTONE)
            throw new IllegalArgumentException("reserved key");
    }

    @Override public boolean contains(long key) {
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

    @Override public boolean add(long key) {
        checkKey(key);
        Segment seg = segmentFor(key);
        synchronized (seg) {
            State s = seg.state;
            if (needsResize(seg, s)) s = resizeLocked(seg);
            return addLocked(seg, s, key);
        }
    }

    @Override public boolean remove(long key) {
        if (key == EMPTY || key == TOMBSTONE) return false;
        Segment seg = segmentFor(key);
        synchronized (seg) {
            return removeLocked(seg, seg.state, key);
        }
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

    private boolean addLocked(Segment seg, State s, long key) {
        long[] k = s.keys;
        int mask = s.mask;
        int idx = hash(key) & mask;
        int firstTomb = -1;
        while (true) {
            long kk = k[idx];
            if (kk == EMPTY) {
                int writeIdx = firstTomb >= 0 ? firstTomb : idx;
                if (firstTomb >= 0) seg.tombstones--;
                KEYS_VH.setRelease(k, writeIdx, key);
                seg.size++;
                return true;
            }
            if (kk == TOMBSTONE) {
                if (firstTomb < 0) firstTomb = idx;
            } else if (kk == key) {
                return false;
            }
            idx = (idx + 1) & mask;
        }
    }

    private boolean removeLocked(Segment seg, State s, long key) {
        long[] k = s.keys;
        int mask = s.mask;
        int idx = hash(key) & mask;
        while (true) {
            long kk = k[idx];
            if (kk == EMPTY) return false;
            if (kk == key) {
                KEYS_VH.setRelease(k, idx, TOMBSTONE);
                seg.size--; seg.tombstones++;
                return true;
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
        long[] ok = old.keys;
        long[] nk = ns.keys;
        int nmask = ns.mask;
        for (int i = 0; i <= old.mask; i++) {
            long kk = ok[i];
            if (kk == EMPTY || kk == TOMBSTONE) continue;
            int idx = hash(kk) & nmask;
            while (nk[idx] != EMPTY) idx = (idx + 1) & nmask;
            nk[idx] = kk;
        }
        seg.state = ns;
        seg.tombstones = 0;
        return ns;
    }

    @Override public LongIterator iterator() {
        long[] snap = toLongArray();
        return new LongIterator() {
            int i = 0;
            long last = EMPTY;
            boolean hasLast = false;
            @Override public boolean hasNext() { return i < snap.length; }
            @Override public long nextLong() { last = snap[i++]; hasLast = true; return last; }
            @Override public void remove() {
                if (!hasLast) throw new IllegalStateException();
                ConcurrentLongOpenHashSet.this.remove(last);
                hasLast = false;
            }
        };
    }

    @Override public long[] toLongArray() {
        int n = size();
        long[] arr = new long[n];
        int j = 0;
        for (Segment seg : segments) {
            State s = seg.state;
            for (int i = 0; i <= s.mask && j < arr.length; i++) {
                long k = s.keys[i];
                if (k != EMPTY && k != TOMBSTONE) arr[j++] = k;
            }
        }
        if (j < arr.length) {
            long[] trimmed = new long[j];
            System.arraycopy(arr, 0, trimmed, 0, j);
            return trimmed;
        }
        return arr;
    }

    @Override public long[] toArray(long[] a) {
        long[] arr = toLongArray();
        if (a.length < arr.length) return arr;
        System.arraycopy(arr, 0, a, 0, arr.length);
        if (a.length > arr.length) a[arr.length] = 0L;
        return a;
    }

    @Override public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof LongSet)) return false;
        LongSet other = (LongSet) o;
        if (other.size() != size()) return false;
        for (LongIterator it = iterator(); it.hasNext(); ) {
            if (!other.contains(it.nextLong())) return false;
        }
        return true;
    }
    @Override public int hashCode() {
        int h = 0;
        for (LongIterator it = iterator(); it.hasNext(); ) h += Long.hashCode(it.nextLong());
        return h;
    }
}
