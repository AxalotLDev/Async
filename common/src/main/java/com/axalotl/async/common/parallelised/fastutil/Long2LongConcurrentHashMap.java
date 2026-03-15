package com.axalotl.async.common.parallelised.fastutil;

import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.longs.*;
import it.unimi.dsi.fastutil.objects.AbstractObjectSet;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import it.unimi.dsi.fastutil.objects.ObjectSpliterator;
import it.unimi.dsi.fastutil.objects.ObjectSpliterators;

import java.util.*;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.StampedLock;

/**
 * High-performance striped concurrent Long2LongMap.
 * Zero autoboxing — uses striped Long2LongOpenHashMap with StampedLock optimistic reads.
 * <p>
 * Segment count scales with available processors to maintain write throughput
 * on high-core-count machines (32+ cores).
 */
public final class Long2LongConcurrentHashMap extends AbstractLong2LongMap {

    private static final int DEFAULT_SEGMENTS = defaultSegmentCount();

    private final int segmentCount;
    private final int segmentMask;
    private final Long2LongOpenHashMap[] segments;
    private final StampedLock[] locks;
    private final LongAdder totalSize = new LongAdder();

    public Long2LongConcurrentHashMap() { this(256 * DEFAULT_SEGMENTS, DEFAULT_SEGMENTS); }

    public Long2LongConcurrentHashMap(int expectedSize) { this(expectedSize, DEFAULT_SEGMENTS); }

    public Long2LongConcurrentHashMap(int expectedSize, int concurrencyLevel) {
        this.segmentCount = nextPowerOf2(Math.max(16, concurrencyLevel));
        this.segmentMask = segmentCount - 1;
        int perSegment = Math.max(16, expectedSize / segmentCount);
        segments = new Long2LongOpenHashMap[segmentCount];
        locks = new StampedLock[segmentCount];
        for (int i = 0; i < segmentCount; i++) {
            segments[i] = new Long2LongOpenHashMap(perSegment, 0.75f);
            locks[i] = new StampedLock();
        }
    }

    static int defaultSegmentCount() {
        return nextPowerOf2(Math.max(32, Runtime.getRuntime().availableProcessors() * 4));
    }

    private static int nextPowerOf2(int v) {
        return Integer.highestOneBit(Math.max(1, v - 1)) << 1;
    }

    private static int spread(long key) {
        key = (key ^ (key >>> 30)) * 0xbf58476d1ce4e5b9L;
        key = (key ^ (key >>> 27)) * 0x94d049bb133111ebL;
        return (int) (key ^ (key >>> 31));
    }

    private int segmentFor(long key) { return spread(key) & segmentMask; }

    @Override public long get(long key) {
        int seg = segmentFor(key);
        StampedLock lock = locks[seg];
        long stamp = lock.tryOptimisticRead();
        long v = segments[seg].get(key);
        if (lock.validate(stamp)) return v;
        stamp = lock.readLock();
        try { return segments[seg].get(key); }
        finally { lock.unlockRead(stamp); }
    }

    @Override public long getOrDefault(long key, long defaultValue) {
        int seg = segmentFor(key);
        StampedLock lock = locks[seg];
        long stamp = lock.tryOptimisticRead();
        long v = segments[seg].getOrDefault(key, defaultValue);
        if (lock.validate(stamp)) return v;
        stamp = lock.readLock();
        try { return segments[seg].getOrDefault(key, defaultValue); }
        finally { lock.unlockRead(stamp); }
    }

    @Override public boolean containsKey(long key) {
        int seg = segmentFor(key);
        StampedLock lock = locks[seg];
        long stamp = lock.tryOptimisticRead();
        boolean r = segments[seg].containsKey(key);
        if (lock.validate(stamp)) return r;
        stamp = lock.readLock();
        try { return segments[seg].containsKey(key); }
        finally { lock.unlockRead(stamp); }
    }

    @Override public boolean containsValue(long value) {
        for (int i = 0; i < segmentCount; i++) {
            long stamp = locks[i].readLock();
            try { if (segments[i].containsValue(value)) return true; }
            finally { locks[i].unlockRead(stamp); }
        }
        return false;
    }

    @Override public long put(long key, long value) {
        int seg = segmentFor(key);
        long stamp = locks[seg].writeLock();
        try {
            int sb = segments[seg].size();
            long prev = segments[seg].put(key, value);
            if (segments[seg].size() > sb) totalSize.increment();
            return prev;
        } finally { locks[seg].unlockWrite(stamp); }
    }

    @Override public long putIfAbsent(long key, long value) {
        int seg = segmentFor(key);
        long stamp = locks[seg].writeLock();
        try {
            if (segments[seg].containsKey(key)) return segments[seg].get(key);
            segments[seg].put(key, value);
            totalSize.increment();
            return defRetValue;
        } finally { locks[seg].unlockWrite(stamp); }
    }

    @Override public long remove(long key) {
        int seg = segmentFor(key);
        long stamp = locks[seg].writeLock();
        try {
            int sb = segments[seg].size();
            long prev = segments[seg].remove(key);
            if (segments[seg].size() < sb) totalSize.decrement();
            return prev;
        } finally { locks[seg].unlockWrite(stamp); }
    }

    @Override public boolean remove(long key, long value) {
        int seg = segmentFor(key);
        long stamp = locks[seg].writeLock();
        try {
            if (segments[seg].containsKey(key) && segments[seg].get(key) == value) {
                segments[seg].remove(key);
                totalSize.decrement();
                return true;
            }
            return false;
        } finally { locks[seg].unlockWrite(stamp); }
    }

    @Override public long replace(long key, long value) {
        int seg = segmentFor(key);
        long stamp = locks[seg].writeLock();
        try {
            if (segments[seg].containsKey(key)) return segments[seg].put(key, value);
            return defRetValue;
        } finally { locks[seg].unlockWrite(stamp); }
    }

    @Override public boolean replace(long key, long oldValue, long newValue) {
        int seg = segmentFor(key);
        long stamp = locks[seg].writeLock();
        try {
            if (segments[seg].containsKey(key) && segments[seg].get(key) == oldValue) {
                segments[seg].put(key, newValue);
                return true;
            }
            return false;
        } finally { locks[seg].unlockWrite(stamp); }
    }

    /**
     * Adds {@code delta} to the value currently associated with {@code key}.
     * If the key is not present, inserts it with value {@code delta}.
     * Returns the new value.
     */
    public long addTo(long key, long delta) {
        int seg = segmentFor(key);
        long stamp = locks[seg].writeLock();
        try {
            int sb = segments[seg].size();
            long cur = segments[seg].getOrDefault(key, 0L);
            long newVal = cur + delta;
            segments[seg].put(key, newVal);
            if (segments[seg].size() > sb) totalSize.increment();
            return newVal;
        } finally { locks[seg].unlockWrite(stamp); }
    }

    @Override public long computeIfAbsent(long key, java.util.function.LongUnaryOperator mappingFunction) {
        int seg = segmentFor(key);
        StampedLock lock = locks[seg];
        long stamp = lock.tryOptimisticRead();
        boolean has = segments[seg].containsKey(key);
        long existing = has ? segments[seg].get(key) : defRetValue;
        if (lock.validate(stamp) && has) return existing;

        stamp = lock.writeLock();
        try {
            if (segments[seg].containsKey(key)) return segments[seg].get(key);
            long computed = mappingFunction.applyAsLong(key);
            segments[seg].put(key, computed);
            totalSize.increment();
            return computed;
        } finally { lock.unlockWrite(stamp); }
    }

    @Override public long computeIfPresent(long key, java.util.function.BiFunction<? super Long, ? super Long, ? extends Long> remappingFunction) {
        int seg = segmentFor(key);
        long stamp = locks[seg].writeLock();
        try {
            if (!segments[seg].containsKey(key)) return defRetValue;
            long oldVal = segments[seg].get(key);
            Long newVal = remappingFunction.apply(key, oldVal);
            if (newVal != null) {
                long nv = newVal.longValue();
                segments[seg].put(key, nv);
                return nv;
            } else {
                segments[seg].remove(key);
                totalSize.decrement();
                return defRetValue;
            }
        } finally { locks[seg].unlockWrite(stamp); }
    }

    @Override public long compute(long key, java.util.function.BiFunction<? super Long, ? super Long, ? extends Long> remappingFunction) {
        int seg = segmentFor(key);
        long stamp = locks[seg].writeLock();
        try {
            boolean had = segments[seg].containsKey(key);
            Long oldVal = had ? Long.valueOf(segments[seg].get(key)) : null;
            Long newVal = remappingFunction.apply(key, oldVal);
            if (newVal != null) {
                long nv = newVal.longValue();
                int sb = segments[seg].size();
                segments[seg].put(key, nv);
                if (segments[seg].size() > sb) totalSize.increment();
                return nv;
            } else if (had) {
                segments[seg].remove(key);
                totalSize.decrement();
            }
            return defRetValue;
        } finally { locks[seg].unlockWrite(stamp); }
    }

    @Override public long merge(long key, long value, java.util.function.BiFunction<? super Long, ? super Long, ? extends Long> remappingFunction) {
        int seg = segmentFor(key);
        long stamp = locks[seg].writeLock();
        try {
            boolean had = segments[seg].containsKey(key);
            if (!had) {
                int sb = segments[seg].size();
                segments[seg].put(key, value);
                if (segments[seg].size() > sb) totalSize.increment();
                return value;
            }
            long oldVal = segments[seg].get(key);
            Long newVal = remappingFunction.apply(oldVal, value);
            if (newVal != null) {
                long nv = newVal.longValue();
                segments[seg].put(key, nv);
                return nv;
            } else {
                segments[seg].remove(key);
                totalSize.decrement();
                return defRetValue;
            }
        } finally { locks[seg].unlockWrite(stamp); }
    }

    public long mergeLong(long key, long value, java.util.function.LongBinaryOperator remappingFunction) {
        int seg = segmentFor(key);
        long stamp = locks[seg].writeLock();
        try {
            int sb = segments[seg].size();
            if (segments[seg].containsKey(key)) {
                long oldVal = segments[seg].get(key);
                long newVal = remappingFunction.applyAsLong(oldVal, value);
                segments[seg].put(key, newVal);
                return newVal;
            } else {
                segments[seg].put(key, value);
                if (segments[seg].size() > sb) totalSize.increment();
                return value;
            }
        } finally { locks[seg].unlockWrite(stamp); }
    }

    @Override public void putAll(Map<? extends Long, ? extends Long> m) {
        if (m instanceof Long2LongMap l2l) {
            for (Long2LongMap.Entry e : l2l.long2LongEntrySet()) put(e.getLongKey(), e.getLongValue());
        } else {
            for (Map.Entry<? extends Long, ? extends Long> e : m.entrySet()) put(e.getKey().longValue(), e.getValue().longValue());
        }
    }

    @Override public int size() {
        long s = totalSize.sum();
        return (int) Math.max(0L, Math.min(s, Integer.MAX_VALUE));
    }

    @Override public boolean isEmpty() { return totalSize.sum() == 0; }

    @Override public void clear() {
        for (int i = 0; i < segmentCount; i++) {
            long stamp = locks[i].writeLock();
            try { segments[i].clear(); }
            finally { locks[i].unlockWrite(stamp); }
        }
        totalSize.reset();
    }

    @Override public void defaultReturnValue(long rv) {
        super.defaultReturnValue(rv);
        for (int i = 0; i < segmentCount; i++) {
            long stamp = locks[i].writeLock();
            try { segments[i].defaultReturnValue(rv); }
            finally { locks[i].unlockWrite(stamp); }
        }
    }

    @FunctionalInterface
    public interface LongLongConsumer { void accept(long key, long value); }

    public void forEach(LongLongConsumer action) {
        for (int i = 0; i < segmentCount; i++) {
            long stamp = locks[i].readLock();
            try {
                for (Long2LongMap.Entry e : segments[i].long2LongEntrySet())
                    action.accept(e.getLongKey(), e.getLongValue());
            } finally { locks[i].unlockRead(stamp); }
        }
    }

    @Override public ObjectSet<Long2LongMap.Entry> long2LongEntrySet() { return new SnapshotEntrySet(); }

    @Override public LongSet keySet() {
        LongOpenHashSet keys = new LongOpenHashSet(size());
        for (int i = 0; i < segmentCount; i++) {
            long stamp = locks[i].readLock();
            try { keys.addAll(segments[i].keySet()); }
            finally { locks[i].unlockRead(stamp); }
        }
        return keys;
    }

    @Override public LongCollection values() {
        LongArrayList vals = new LongArrayList(size());
        for (int i = 0; i < segmentCount; i++) {
            long stamp = locks[i].readLock();
            try { for (long v : segments[i].values()) vals.add(v); }
            finally { locks[i].unlockRead(stamp); }
        }
        return vals;
    }

    private List<Long2LongMap.Entry> snapshotEntries() {
        List<Long2LongMap.Entry> snap = new ArrayList<>(size());
        for (int i = 0; i < segmentCount; i++) {
            long stamp = locks[i].readLock();
            try {
                for (Long2LongMap.Entry e : segments[i].long2LongEntrySet())
                    snap.add(new ImmutableEntry(e.getLongKey(), e.getLongValue()));
            } finally { locks[i].unlockRead(stamp); }
        }
        return snap;
    }

    private final class SnapshotEntrySet extends AbstractObjectSet<Long2LongMap.Entry> {
        @Override public ObjectIterator<Long2LongMap.Entry> iterator() {
            List<Long2LongMap.Entry> snap = snapshotEntries();
            return new ObjectIterator<>() {
                private final Iterator<Long2LongMap.Entry> it = snap.iterator();
                private Long2LongMap.Entry last;
                @Override public boolean hasNext() { return it.hasNext(); }
                @Override public Long2LongMap.Entry next() { last = it.next(); return last; }
                @Override public void remove() {
                    if (last == null) throw new IllegalStateException();
                    Long2LongConcurrentHashMap.this.remove(last.getLongKey());
                    last = null;
                }
            };
        }
        @Override public ObjectSpliterator<Long2LongMap.Entry> spliterator() {
            return ObjectSpliterators.asSpliterator(iterator(), size(), ObjectSpliterators.SET_SPLITERATOR_CHARACTERISTICS);
        }
        @Override public int size() { return Long2LongConcurrentHashMap.this.size(); }
        @Override public void clear() { Long2LongConcurrentHashMap.this.clear(); }
        @Override public boolean contains(Object o) {
            if (!(o instanceof Long2LongMap.Entry e)) return false;
            long key = e.getLongKey();
            int seg = segmentFor(key);
            long stamp = locks[seg].readLock();
            try { return segments[seg].containsKey(key) && segments[seg].get(key) == e.getLongValue(); }
            finally { locks[seg].unlockRead(stamp); }
        }
    }

    private record ImmutableEntry(long key, long value) implements Long2LongMap.Entry {
        @Override public long getLongKey() { return key; }
        @Override public long getLongValue() { return value; }
        @Override public long setValue(long value) { throw new UnsupportedOperationException(); }
        @Override public boolean equals(Object o) {
            return o instanceof Long2LongMap.Entry e && key == e.getLongKey() && value == e.getLongValue();
        }
        @Override public int hashCode() {
            return HashCommon.long2int(key) ^ HashCommon.long2int(value);
        }
        @Override public String toString() { return key + "=>" + value; }
    }
}
