package com.axalotl.async.common.parallelised.fastutil;

import it.unimi.dsi.fastutil.ints.AbstractInt2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectCollection;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;

import java.util.*;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.StampedLock;
import java.util.function.BiFunction;

/**
 * High-performance striped concurrent Int2ObjectMap.
 * <p>
 * Uses striped fastutil Int2ObjectOpenHashMap with StampedLock.
 * Primitive int keys throughout — zero autoboxing.
 *
 * @param <V> value type
 */
public final class Int2ObjectConcurrentHashMap<V> extends AbstractInt2ObjectMap<V> {

    private static final int DEFAULT_SEGMENTS = defaultSegmentCount();

    private final int segmentCount;
    private final int segmentMask;
    private final Int2ObjectOpenHashMap<V>[] segments;
    private final StampedLock[] locks;
    private final LongAdder totalSize = new LongAdder();

    public Int2ObjectConcurrentHashMap() { this(64 * DEFAULT_SEGMENTS, DEFAULT_SEGMENTS); }

    public Int2ObjectConcurrentHashMap(int expectedSize) { this(expectedSize, DEFAULT_SEGMENTS); }

    @SuppressWarnings("unchecked")
    public Int2ObjectConcurrentHashMap(int expectedSize, int concurrencyLevel) {
        this.segmentCount = nextPowerOf2(Math.max(16, concurrencyLevel));
        this.segmentMask = segmentCount - 1;
        int perSegment = Math.max(16, expectedSize / segmentCount);
        segments = new Int2ObjectOpenHashMap[segmentCount];
        locks = new StampedLock[segmentCount];
        for (int i = 0; i < segmentCount; i++) {
            segments[i] = new Int2ObjectOpenHashMap<>(perSegment, 0.75f);
            locks[i] = new StampedLock();
        }
    }

    static int defaultSegmentCount() {
        return nextPowerOf2(Math.max(32, Runtime.getRuntime().availableProcessors() * 4));
    }

    private static int nextPowerOf2(int v) {
        return Integer.highestOneBit(Math.max(1, v - 1)) << 1;
    }

    private static int spread(int key) {
        key = key ^ (key >>> 16);
        return key;
    }

    private int segmentFor(int key) {
        return spread(key) & segmentMask;
    }

    @Override
    public V get(int key) {
        int seg = segmentFor(key);
        StampedLock lock = locks[seg];
        long stamp = lock.tryOptimisticRead();
        V v = segments[seg].get(key);
        if (lock.validate(stamp)) return v == null ? defaultReturnValue() : v;
        stamp = lock.readLock();
        try {
            v = segments[seg].get(key);
            return v == null ? defaultReturnValue() : v;
        } finally { lock.unlockRead(stamp); }
    }

    @Override
    public boolean containsKey(int key) {
        int seg = segmentFor(key);
        StampedLock lock = locks[seg];
        long stamp = lock.tryOptimisticRead();
        boolean r = segments[seg].containsKey(key);
        if (lock.validate(stamp)) return r;
        stamp = lock.readLock();
        try { return segments[seg].containsKey(key); }
        finally { lock.unlockRead(stamp); }
    }

    @Override
    public boolean containsValue(Object value) {
        if (value == null) return false;
        for (int i = 0; i < segmentCount; i++) {
            long stamp = locks[i].readLock();
            try { if (segments[i].containsValue(value)) return true; }
            finally { locks[i].unlockRead(stamp); }
        }
        return false;
    }

    @Override
    public V put(int key, V value) {
        int seg = segmentFor(key);
        long stamp = locks[seg].writeLock();
        try {
            int sizeBefore = segments[seg].size();
            V prev = segments[seg].put(key, value);
            if (segments[seg].size() > sizeBefore) totalSize.increment();
            return prev;
        } finally { locks[seg].unlockWrite(stamp); }
    }

    @Override
    public V remove(int key) {
        int seg = segmentFor(key);
        long stamp = locks[seg].writeLock();
        try {
            int sizeBefore = segments[seg].size();
            V prev = segments[seg].remove(key);
            if (segments[seg].size() < sizeBefore) totalSize.decrement();
            return prev;
        } finally { locks[seg].unlockWrite(stamp); }
    }

    @Override
    public V putIfAbsent(int key, V value) {
        int seg = segmentFor(key);
        long stamp = locks[seg].writeLock();
        try {
            V existing = segments[seg].get(key);
            if (existing != null) return existing;
            segments[seg].put(key, value);
            totalSize.increment();
            return defaultReturnValue();
        } finally { locks[seg].unlockWrite(stamp); }
    }

    public V compute(int key, BiFunction<? super Integer, ? super V, ? extends V> remappingFunction) {
        int seg = segmentFor(key);
        long stamp = locks[seg].writeLock();
        try {
            V oldVal = segments[seg].get(key);
            V newVal = remappingFunction.apply(key, oldVal);
            if (newVal != null) {
                int sizeBefore = segments[seg].size();
                segments[seg].put(key, newVal);
                if (segments[seg].size() > sizeBefore) totalSize.increment();
            } else if (oldVal != null) {
                segments[seg].remove(key);
                totalSize.decrement();
            }
            return newVal;
        } finally { locks[seg].unlockWrite(stamp); }
    }

    public V getOrDefault(int key, V defaultValue) {
        int seg = segmentFor(key);
        StampedLock lock = locks[seg];
        long stamp = lock.tryOptimisticRead();
        V v = segments[seg].getOrDefault(key, defaultValue);
        if (lock.validate(stamp)) return v;
        stamp = lock.readLock();
        try { return segments[seg].getOrDefault(key, defaultValue); }
        finally { lock.unlockRead(stamp); }
    }

    public boolean replace(int key, V oldValue, V newValue) {
        int seg = segmentFor(key);
        long stamp = locks[seg].writeLock();
        try {
            V existing = segments[seg].get(key);
            if (existing != null && existing.equals(oldValue)) {
                segments[seg].put(key, newValue);
                return true;
            }
            return false;
        } finally { locks[seg].unlockWrite(stamp); }
    }

    public V replace(int key, V value) {
        int seg = segmentFor(key);
        long stamp = locks[seg].writeLock();
        try {
            if (segments[seg].containsKey(key)) return segments[seg].put(key, value);
            return defaultReturnValue();
        } finally { locks[seg].unlockWrite(stamp); }
    }

    public boolean remove(int key, Object value) {
        int seg = segmentFor(key);
        long stamp = locks[seg].writeLock();
        try {
            V existing = segments[seg].get(key);
            if (existing != null && existing.equals(value)) {
                segments[seg].remove(key);
                totalSize.decrement();
                return true;
            }
            return false;
        } finally { locks[seg].unlockWrite(stamp); }
    }

    public V computeIfAbsent(int key, java.util.function.IntFunction<? extends V> mappingFunction) {
        int seg = segmentFor(key);
        StampedLock lock = locks[seg];
        long stamp = lock.tryOptimisticRead();
        boolean has = segments[seg].containsKey(key);
        V existing = has ? segments[seg].get(key) : null;
        if (lock.validate(stamp) && has) return existing;

        stamp = lock.writeLock();
        try {
            V val = segments[seg].get(key);
            if (val != null) return val;
            V computed = mappingFunction.apply(key);
            if (computed != null) {
                segments[seg].put(key, computed);
                totalSize.increment();
            }
            return computed;
        } finally { lock.unlockWrite(stamp); }
    }

    public V computeIfPresent(int key, BiFunction<? super Integer, ? super V, ? extends V> remappingFunction) {
        int seg = segmentFor(key);
        long stamp = locks[seg].writeLock();
        try {
            V existing = segments[seg].get(key);
            if (existing == null) return defaultReturnValue();
            V newVal = remappingFunction.apply(key, existing);
            if (newVal != null) {
                segments[seg].put(key, newVal);
            } else {
                segments[seg].remove(key);
                totalSize.decrement();
            }
            return newVal == null ? defaultReturnValue() : newVal;
        } finally { locks[seg].unlockWrite(stamp); }
    }

    public V merge(int key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
        int seg = segmentFor(key);
        long stamp = locks[seg].writeLock();
        try {
            V existing = segments[seg].get(key);
            V newVal = existing == null ? value : remappingFunction.apply(existing, value);
            if (newVal != null) {
                int sizeBefore = segments[seg].size();
                segments[seg].put(key, newVal);
                if (segments[seg].size() > sizeBefore) totalSize.increment();
            } else {
                segments[seg].remove(key);
                totalSize.decrement();
            }
            return newVal == null ? defaultReturnValue() : newVal;
        } finally { locks[seg].unlockWrite(stamp); }
    }

    @Override
    public void putAll(Map<? extends Integer, ? extends V> m) {
        for (Map.Entry<? extends Integer, ? extends V> e : m.entrySet()) put(e.getKey().intValue(), e.getValue());
    }

    @Override
    public int size() {
        long s = totalSize.sum();
        return (int) Math.max(0L, Math.min(s, Integer.MAX_VALUE));
    }

    @Override public boolean isEmpty() { return totalSize.sum() == 0; }

    @Override
    public void clear() {
        for (int i = 0; i < segmentCount; i++) {
            long stamp = locks[i].writeLock();
            try { segments[i].clear(); }
            finally { locks[i].unlockWrite(stamp); }
        }
        totalSize.reset();
    }

    @FunctionalInterface
    public interface IntObjectConsumer<V> {
        void accept(int key, V value);
    }

    public void forEach(IntObjectConsumer<V> action) {
        for (int i = 0; i < segmentCount; i++) {
            long stamp = locks[i].readLock();
            try {
                for (Int2ObjectMap.Entry<V> e : segments[i].int2ObjectEntrySet()) {
                    action.accept(e.getIntKey(), e.getValue());
                }
            } finally { locks[i].unlockRead(stamp); }
        }
    }

    @Override
    public ObjectSet<Entry<V>> int2ObjectEntrySet() {
        List<Entry<V>> snapshot = new ArrayList<>(size());
        for (int i = 0; i < segmentCount; i++) {
            long stamp = locks[i].readLock();
            try {
                for (Int2ObjectMap.Entry<V> e : segments[i].int2ObjectEntrySet())
                    snapshot.add(new ImmutableEntry<>(e.getIntKey(), e.getValue()));
            } finally { locks[i].unlockRead(stamp); }
        }
        return new ObjectOpenHashSet<>(snapshot);
    }

    @Override
    public IntSet keySet() {
        IntOpenHashSet keys = new IntOpenHashSet(size());
        for (int i = 0; i < segmentCount; i++) {
            long stamp = locks[i].readLock();
            try { keys.addAll(segments[i].keySet()); }
            finally { locks[i].unlockRead(stamp); }
        }
        return keys;
    }

    @Override
    public ObjectCollection<V> values() {
        List<V> vals = new ArrayList<>(size());
        for (int i = 0; i < segmentCount; i++) {
            long stamp = locks[i].readLock();
            try { vals.addAll(segments[i].values()); }
            finally { locks[i].unlockRead(stamp); }
        }
        return new ObjectArrayList<>(vals);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Int2ObjectMap<?> that)) return false;
        return size() == that.size() && int2ObjectEntrySet().containsAll(that.int2ObjectEntrySet());
    }

    @Override
    public int hashCode() {
        int h = 0;
        for (int i = 0; i < segmentCount; i++) {
            long stamp = locks[i].readLock();
            try {
                for (Int2ObjectMap.Entry<V> e : segments[i].int2ObjectEntrySet())
                    h += Integer.hashCode(e.getIntKey()) ^ Objects.hashCode(e.getValue());
            } finally { locks[i].unlockRead(stamp); }
        }
        return h;
    }

    @Override
    public String toString() {
        return "Int2ObjectConcurrentHashMap[size=" + size() + "]";
    }

    private record ImmutableEntry<V>(int key, V value) implements Entry<V> {
        @Override public int getIntKey() { return key; }
        @Override public V getValue() { return value; }
        @Override public V setValue(V value) { throw new UnsupportedOperationException(); }
        @Override public boolean equals(Object o) {
            if (!(o instanceof Int2ObjectMap.Entry<?> e)) return false;
            return key == e.getIntKey() && Objects.equals(this.value, e.getValue());
        }
        @Override public int hashCode() { return Integer.hashCode(key) ^ Objects.hashCode(value); }
        @Override public String toString() { return key + "=" + value; }
    }
}
