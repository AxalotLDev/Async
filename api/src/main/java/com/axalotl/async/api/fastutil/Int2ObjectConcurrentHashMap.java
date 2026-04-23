package com.axalotl.async.api.fastutil;

import it.unimi.dsi.fastutil.ints.Int2ObjectFunction;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectCollection;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.jspecify.annotations.NonNull;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.StampedLock;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.IntFunction;

/**
 * 32+-stripe concurrent {@link Int2ObjectOpenHashMap}.
 * <p>
 * <b>Extends</b> {@code Int2ObjectOpenHashMap} (not just {@code AbstractInt2ObjectMap})
 * so this class is a drop-in replacement for fastutil fields declared with
 * the concrete type — VerifyError-safe for {@code PUTFIELD} and
 * {@code instanceof} checks. The parent's internal arrays stay empty; all
 * state is held by the stripe array. Every public method of the parent is
 * overridden to route through the stripes, otherwise inherited methods would
 * read the unused parent arrays and silently return empty/default values.
 * <p>
 * Reads take the per-stripe {@link StampedLock} optimistic path (~5 ns when
 * uncontended). Writes take the stripe's write lock. {@code size()} is an
 * amortized {@link LongAdder} sum, so updates don't contend across stripes.
 */
public final class Int2ObjectConcurrentHashMap<V> extends Int2ObjectOpenHashMap<V> {

    private static final int DEFAULT_SEGMENTS = defaultSegmentCount();

    private final int segmentCount;
    private final int segmentMask;
    private final Int2ObjectOpenHashMap<V>[] segments;
    private final StampedLock[] locks;
    private final LongAdder totalSize = new LongAdder();

    public Int2ObjectConcurrentHashMap() { this(64 * DEFAULT_SEGMENTS, DEFAULT_SEGMENTS); }
    public Int2ObjectConcurrentHashMap(int expectedSize) { this(expectedSize, DEFAULT_SEGMENTS); }
    public Int2ObjectConcurrentHashMap(int expected, float f) { this(expected, DEFAULT_SEGMENTS); }
    public Int2ObjectConcurrentHashMap(Map<? extends Integer, ? extends V> m) {
        this(Math.max(16, m.size()), DEFAULT_SEGMENTS);
        putAll(m);
    }
    public Int2ObjectConcurrentHashMap(Int2ObjectMap<V> m) {
        this(Math.max(16, m.size()), DEFAULT_SEGMENTS);
        for (Int2ObjectMap.Entry<V> e : m.int2ObjectEntrySet()) put(e.getIntKey(), e.getValue());
    }

    @SuppressWarnings("unchecked")
    public Int2ObjectConcurrentHashMap(int expectedSize, int concurrencyLevel) {
        super();
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
        return nextPowerOf2(Math.max(16, Runtime.getRuntime().availableProcessors()));
    }

    private static int nextPowerOf2(int v) {
        return Integer.highestOneBit(Math.max(1, v - 1)) << 1;
    }

    private static int spread(int key) {
        return key ^ (key >>> 16);
    }

    private int segmentFor(int key) { return spread(key) & segmentMask; }

    // ============================== READS ==============================

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

    @Override public V get(Object k) { return k instanceof Integer i ? get(i.intValue()) : defaultReturnValue(); }

    @Override
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

    @Override public V getOrDefault(Object k, V d) { return k instanceof Integer i ? getOrDefault(i.intValue(), d) : d; }

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

    @Override public boolean containsKey(Object k) { return k instanceof Integer i && containsKey(i.intValue()); }

    @Override
    public boolean containsValue(Object value) {
        for (int i = 0; i < segmentCount; i++) {
            long stamp = locks[i].readLock();
            try { if (segments[i].containsValue(value)) return true; }
            finally { locks[i].unlockRead(stamp); }
        }
        return false;
    }

    @Override public int size() { return (int) Math.clamp(totalSize.sum(), 0L, (long) Integer.MAX_VALUE); }
    @Override public boolean isEmpty() { return totalSize.sum() == 0; }

    // ============================== WRITES ==============================

    @Override
    public V put(int key, V value) {
        int seg = segmentFor(key);
        long stamp = locks[seg].writeLock();
        try {
            int sb = segments[seg].size();
            V prev = segments[seg].put(key, value);
            if (segments[seg].size() > sb) totalSize.increment();
            return prev == null ? defaultReturnValue() : prev;
        } finally { locks[seg].unlockWrite(stamp); }
    }

    @Override public V put(Integer k, V v) { return put(k.intValue(), v); }

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

    @Override
    public V remove(int key) {
        int seg = segmentFor(key);
        long stamp = locks[seg].writeLock();
        try {
            int sb = segments[seg].size();
            V prev = segments[seg].remove(key);
            if (segments[seg].size() < sb) totalSize.decrement();
            return prev == null ? defaultReturnValue() : prev;
        } finally { locks[seg].unlockWrite(stamp); }
    }

    @Override public V remove(Object k) { return k instanceof Integer i ? remove(i.intValue()) : defaultReturnValue(); }

    @Override
    public boolean remove(int key, Object value) {
        int seg = segmentFor(key);
        long stamp = locks[seg].writeLock();
        try {
            V existing = segments[seg].get(key);
            if (existing != null && Objects.equals(existing, value)) {
                segments[seg].remove(key);
                totalSize.decrement();
                return true;
            }
            return false;
        } finally { locks[seg].unlockWrite(stamp); }
    }

    @Override
    public V replace(int key, V value) {
        int seg = segmentFor(key);
        long stamp = locks[seg].writeLock();
        try {
            if (segments[seg].containsKey(key)) return segments[seg].put(key, value);
            return defaultReturnValue();
        } finally { locks[seg].unlockWrite(stamp); }
    }

    @Override
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

    @Override
    public V computeIfAbsent(int key, IntFunction<? extends V> mappingFunction) {
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
            if (computed != null) { segments[seg].put(key, computed); totalSize.increment(); }
            return computed == null ? defaultReturnValue() : computed;
        } finally { lock.unlockWrite(stamp); }
    }

    @Override
    public V computeIfAbsent(int key, Int2ObjectFunction<? extends V> mappingFunction) {
        return computeIfAbsent(key, (IntFunction<? extends V>) mappingFunction::get);
    }

    @Override
    public V computeIfPresent(int key, BiFunction<? super Integer, ? super V, ? extends V> remappingFunction) {
        int seg = segmentFor(key);
        long stamp = locks[seg].writeLock();
        try {
            V existing = segments[seg].get(key);
            if (existing == null) return defaultReturnValue();
            V newVal = remappingFunction.apply(key, existing);
            if (newVal != null) segments[seg].put(key, newVal);
            else { segments[seg].remove(key); totalSize.decrement(); }
            return newVal == null ? defaultReturnValue() : newVal;
        } finally { locks[seg].unlockWrite(stamp); }
    }

    @Override
    public V compute(int key, BiFunction<? super Integer, ? super V, ? extends V> remappingFunction) {
        int seg = segmentFor(key);
        long stamp = locks[seg].writeLock();
        try {
            V oldVal = segments[seg].get(key);
            V newVal = remappingFunction.apply(key, oldVal);
            if (newVal != null) {
                int sb = segments[seg].size();
                segments[seg].put(key, newVal);
                if (segments[seg].size() > sb) totalSize.increment();
            } else if (oldVal != null) {
                segments[seg].remove(key);
                totalSize.decrement();
            }
            return newVal == null ? defaultReturnValue() : newVal;
        } finally { locks[seg].unlockWrite(stamp); }
    }

    @Override
    public V merge(int key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
        int seg = segmentFor(key);
        long stamp = locks[seg].writeLock();
        try {
            V existing = segments[seg].get(key);
            V newVal = existing == null ? value : remappingFunction.apply(existing, value);
            if (newVal != null) {
                int sb = segments[seg].size();
                segments[seg].put(key, newVal);
                if (segments[seg].size() > sb) totalSize.increment();
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
    public void clear() {
        for (int i = 0; i < segmentCount; i++) {
            long stamp = locks[i].writeLock();
            try { segments[i].clear(); }
            finally { locks[i].unlockWrite(stamp); }
        }
        totalSize.reset();
    }

    @Override
    public void defaultReturnValue(V rv) {
        super.defaultReturnValue(rv);
        for (int i = 0; i < segmentCount; i++) {
            long stamp = locks[i].writeLock();
            try { segments[i].defaultReturnValue(rv); }
            finally { locks[i].unlockWrite(stamp); }
        }
    }

    // ============================== VIEWS ==============================

    @Override public Int2ObjectMap.FastEntrySet<V> int2ObjectEntrySet() {
        Int2ObjectOpenHashMap<V> snap = new Int2ObjectOpenHashMap<>(size());
        for (int i = 0; i < segmentCount; i++) {
            long stamp = locks[i].readLock();
            try {
                for (Int2ObjectMap.Entry<V> e : segments[i].int2ObjectEntrySet())
                    snap.put(e.getIntKey(), e.getValue());
            } finally { locks[i].unlockRead(stamp); }
        }
        return snap.int2ObjectEntrySet();
    }

    @Override
    public @NonNull IntSet keySet() {
        IntOpenHashSet keys = new IntOpenHashSet(size());
        for (int i = 0; i < segmentCount; i++) {
            long stamp = locks[i].readLock();
            try {
                IntIterator it = segments[i].keySet().iterator();
                while (it.hasNext()) keys.add(it.nextInt());
            } finally { locks[i].unlockRead(stamp); }
        }
        return keys;
    }

    @Override
    public @NonNull ObjectCollection<V> values() {
        ObjectArrayList<V> vals = new ObjectArrayList<>(size());
        for (int i = 0; i < segmentCount; i++) {
            long stamp = locks[i].readLock();
            try {
                Int2ObjectOpenHashMap<V> seg = segments[i];
                if (seg.isEmpty()) continue;
                vals.addAll(seg.values());
            } finally { locks[i].unlockRead(stamp); }
        }
        return vals;
    }

    // ============================== ITERATION ==============================

    @FunctionalInterface
    public interface IntObjectConsumer<V> { void accept(int key, V value); }

    public void forEach(IntObjectConsumer<V> action) {
        for (int i = 0; i < segmentCount; i++) {
            long stamp = locks[i].readLock();
            try {
                for (Int2ObjectMap.Entry<V> e : segments[i].int2ObjectEntrySet())
                    action.accept(e.getIntKey(), e.getValue());
            } finally { locks[i].unlockRead(stamp); }
        }
    }

    @Override
    public void forEach(BiConsumer<? super Integer, ? super V> action) {
        for (int i = 0; i < segmentCount; i++) {
            long stamp = locks[i].readLock();
            try {
                for (Int2ObjectMap.Entry<V> e : segments[i].int2ObjectEntrySet())
                    action.accept(e.getIntKey(), e.getValue());
            } finally { locks[i].unlockRead(stamp); }
        }
    }

    // ============================== MISC ==============================

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

    @Override public String toString() { return "Int2ObjectConcurrentHashMap[size=" + size() + "]"; }

    @Override
    public Int2ObjectOpenHashMap<V> clone() {
        Int2ObjectConcurrentHashMap<V> c = new Int2ObjectConcurrentHashMap<>(size(), segmentCount);
        c.defaultReturnValue(defaultReturnValue());
        for (int i = 0; i < segmentCount; i++) {
            long stamp = locks[i].readLock();
            try {
                for (Int2ObjectMap.Entry<V> e : segments[i].int2ObjectEntrySet())
                    c.put(e.getIntKey(), e.getValue());
            } finally { locks[i].unlockRead(stamp); }
        }
        return c;
    }

    @Override public boolean trim() { return trim(-1); }

    @Override
    public boolean trim(int n) {
        boolean all = true;
        for (int i = 0; i < segmentCount; i++) {
            long stamp = locks[i].writeLock();
            try {
                int localN = n <= 0 ? segments[i].size() : Math.max(n / segmentCount, segments[i].size());
                all &= segments[i].trim(localN);
            } finally { locks[i].unlockWrite(stamp); }
        }
        return all;
    }
}
