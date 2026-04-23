package com.axalotl.async.api.fastutil;

import it.unimi.dsi.fastutil.longs.*;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectCollection;
import it.unimi.dsi.fastutil.objects.ObjectIterator;

import java.util.*;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.StampedLock;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.LongFunction;

/**
 * High-performance striped concurrent Long2ObjectMap.
 * Uses StampedLock with optimistic reads for maximum read throughput.
 * Primitive long keys — zero autoboxing.
 * <p>
 * Intentionally {@code implements Long2ObjectMap<V>} rather than
 * {@code extends Long2ObjectOpenHashMap<V>}: a previous attempt to make
 * this a drop-in via {@code extends} caused mob-cap miscalculation
 * (trillion-mob runaway spawn). Parent's inherited primitive-specialized
 * {@code computeIfAbsent(long, Long2ObjectFunction)} is not overridden
 * here, and when it fires it reads the parent's unused internal arrays
 * and writes the computed value there — our stripe-backed {@code get}
 * never sees it, so the caller thinks the key is absent every time and
 * re-applies the computing function, producing duplicates.
 *
 * @param <V> value type
 */
public final class Long2ObjectConcurrentHashMap<V> implements Long2ObjectMap<V> {

    private static final int DEFAULT_SEGMENTS = defaultSegmentCount();

    private final int segmentCount;
    private final int segmentMask;
    private final Long2ObjectOpenHashMap<V>[] segments;
    private final StampedLock[] locks;
    private final LongAdder totalSize = new LongAdder();
    private volatile V defaultReturnValue;

    public Long2ObjectConcurrentHashMap() { this(256 * DEFAULT_SEGMENTS, DEFAULT_SEGMENTS); }

    public Long2ObjectConcurrentHashMap(int expectedSize) { this(expectedSize, DEFAULT_SEGMENTS); }

    @SuppressWarnings("unchecked")
    public Long2ObjectConcurrentHashMap(int expectedSize, int concurrencyLevel) {
        this.segmentCount = nextPowerOf2(Math.max(16, concurrencyLevel));
        this.segmentMask = segmentCount - 1;
        int perSegment = Math.max(16, expectedSize / segmentCount);
        segments = new Long2ObjectOpenHashMap[segmentCount];
        locks = new StampedLock[segmentCount];
        for (int i = 0; i < segmentCount; i++) {
            segments[i] = new Long2ObjectOpenHashMap<>(perSegment, 0.75f);
            locks[i] = new StampedLock();
        }
    }

    @SuppressWarnings("unused")
    public Long2ObjectConcurrentHashMap(int initialCapacity, float loadFactor) { this(initialCapacity); }
    @SuppressWarnings("unused")
    public Long2ObjectConcurrentHashMap(int initialCapacity, float loadFactor, int concurrencyLevel) { this(initialCapacity, concurrencyLevel); }

    static int defaultSegmentCount() {
        return nextPowerOf2(Math.max(16, Runtime.getRuntime().availableProcessors()));
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

    @Override public V get(long key) {
        int seg = segmentFor(key);
        StampedLock lock = locks[seg];
        long stamp = lock.tryOptimisticRead();
        V v = segments[seg].get(key);
        if (lock.validate(stamp)) return v == null ? defaultReturnValue : v;
        stamp = lock.readLock();
        try { v = segments[seg].get(key); return v == null ? defaultReturnValue : v; }
        finally { lock.unlockRead(stamp); }
    }

    @Override public V put(long key, V value) {
        int seg = segmentFor(key);
        long stamp = locks[seg].writeLock();
        try {
            int sb = segments[seg].size();
            V prev = segments[seg].put(key, value);
            if (segments[seg].size() > sb) totalSize.increment();
            return prev == null ? defaultReturnValue : prev;
        } finally { locks[seg].unlockWrite(stamp); }
    }

    @Override public V remove(long key) {
        int seg = segmentFor(key);
        long stamp = locks[seg].writeLock();
        try {
            int sb = segments[seg].size();
            V prev = segments[seg].remove(key);
            if (segments[seg].size() < sb) totalSize.decrement();
            return prev == null ? defaultReturnValue : prev;
        } finally { locks[seg].unlockWrite(stamp); }
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

    @Override public boolean containsValue(Object value) {
        if (value == null) return false;
        for (int i = 0; i < segmentCount; i++) {
            long stamp = locks[i].readLock();
            try { if (segments[i].containsValue(value)) return true; }
            finally { locks[i].unlockRead(stamp); }
        }
        return false;
    }

    @Override public V getOrDefault(long key, V defaultValue) {
        int seg = segmentFor(key);
        StampedLock lock = locks[seg];
        long stamp = lock.tryOptimisticRead();
        V v = segments[seg].getOrDefault(key, defaultValue);
        if (lock.validate(stamp)) return v;
        stamp = lock.readLock();
        try { return segments[seg].getOrDefault(key, defaultValue); }
        finally { lock.unlockRead(stamp); }
    }

    @Override public V putIfAbsent(long key, V value) {
        int seg = segmentFor(key);
        long stamp = locks[seg].writeLock();
        try {
            V existing = segments[seg].get(key);
            if (existing != null) return existing;
            segments[seg].put(key, value);
            totalSize.increment();
            return defaultReturnValue;
        } finally { locks[seg].unlockWrite(stamp); }
    }

    @Override public boolean remove(long key, Object value) {
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

    @Override public boolean replace(long key, V oldValue, V newValue) {
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

    @Override public V replace(long key, V value) {
        int seg = segmentFor(key);
        long stamp = locks[seg].writeLock();
        try {
            if (segments[seg].containsKey(key)) return segments[seg].put(key, value);
            return defaultReturnValue;
        } finally { locks[seg].unlockWrite(stamp); }
    }

    @Override public V compute(long key, BiFunction<? super Long, ? super V, ? extends V> remappingFunction) {
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
            return newVal == null ? defaultReturnValue : newVal;
        } finally { locks[seg].unlockWrite(stamp); }
    }

    @Override public V computeIfAbsent(long key, LongFunction<? extends V> mappingFunction) {
        int seg = segmentFor(key);
        StampedLock lock = locks[seg];
        long stamp = lock.tryOptimisticRead();
        boolean has = segments[seg].containsKey(key);
        V existing = has ? segments[seg].get(key) : null;
        if (lock.validate(stamp) && has) return existing;

        stamp = lock.writeLock();
        try {
            if (segments[seg].containsKey(key)) return segments[seg].get(key);
            V computed = mappingFunction.apply(key);
            if (computed != null) { segments[seg].put(key, computed); totalSize.increment(); }
            return computed == null ? defaultReturnValue : computed;
        } finally { lock.unlockWrite(stamp); }
    }

    @Override public V computeIfPresent(long key, BiFunction<? super Long, ? super V, ? extends V> remappingFunction) {
        int seg = segmentFor(key);
        long stamp = locks[seg].writeLock();
        try {
            V existing = segments[seg].get(key);
            if (existing == null) return defaultReturnValue;
            V newVal = remappingFunction.apply(key, existing);
            if (newVal != null) { segments[seg].put(key, newVal); }
            else { segments[seg].remove(key); totalSize.decrement(); }
            return newVal == null ? defaultReturnValue : newVal;
        } finally { locks[seg].unlockWrite(stamp); }
    }

    @Override public V merge(long key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
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
            return newVal == null ? defaultReturnValue : newVal;
        } finally { locks[seg].unlockWrite(stamp); }
    }

    @Override public void putAll(Map<? extends Long, ? extends V> m) {
        for (Map.Entry<? extends Long, ? extends V> e : m.entrySet()) put(e.getKey().longValue(), e.getValue());
    }

    @Override public void clear() {
        for (int i = 0; i < segmentCount; i++) {
            long stamp = locks[i].writeLock();
            try { segments[i].clear(); }
            finally { locks[i].unlockWrite(stamp); }
        }
        totalSize.reset();
    }

    @Override public int size() {
        long s = totalSize.sum();
        return (int) Math.max(0L, Math.min(s, Integer.MAX_VALUE));
    }
    @Override public boolean isEmpty() { return totalSize.sum() == 0; }

    @Override public void defaultReturnValue(V rv) {
        this.defaultReturnValue = rv;
        for (int i = 0; i < segmentCount; i++) {
            long stamp = locks[i].writeLock();
            try { segments[i].defaultReturnValue(rv); }
            finally { locks[i].unlockWrite(stamp); }
        }
    }
    @Override public V defaultReturnValue() { return defaultReturnValue; }

    @Override public FastEntrySet<V> long2ObjectEntrySet() { return new SnapshotFastEntrySet(); }

    @Override public LongSet keySet() {
        LongOpenHashSet keys = new LongOpenHashSet(size());
        for (int i = 0; i < segmentCount; i++) {
            long stamp = locks[i].readLock();
            try { keys.addAll(segments[i].keySet()); }
            finally { locks[i].unlockRead(stamp); }
        }
        return keys;
    }

    @Override public ObjectCollection<V> values() {
        List<V> vals = new ArrayList<>(size());
        for (int i = 0; i < segmentCount; i++) {
            long stamp = locks[i].readLock();
            try { vals.addAll(segments[i].values()); }
            finally { locks[i].unlockRead(stamp); }
        }
        return new ObjectArrayList<>(vals);
    }

    private List<Entry<V>> snapshotEntries() {
        List<Entry<V>> snap = new ArrayList<>(size());
        for (int i = 0; i < segmentCount; i++) {
            long stamp = locks[i].readLock();
            try {
                for (Long2ObjectMap.Entry<V> e : segments[i].long2ObjectEntrySet())
                    snap.add(new ImmutableEntry<>(e.getLongKey(), e.getValue()));
            } finally { locks[i].unlockRead(stamp); }
        }
        return snap;
    }

    private final class SnapshotFastEntrySet implements FastEntrySet<V> {
        @Override public ObjectIterator<Entry<V>> iterator() {
            List<Entry<V>> snap = snapshotEntries();
            return new ObjectIterator<>() {
                private final Iterator<Entry<V>> it = snap.iterator();
                private Entry<V> last;
                @Override public boolean hasNext() { return it.hasNext(); }
                @Override public Entry<V> next() { last = it.next(); return last; }
                @Override public void remove() {
                    if (last == null) throw new IllegalStateException();
                    Long2ObjectConcurrentHashMap.this.remove(last.getLongKey());
                    last = null;
                }
            };
        }

        @Override public ObjectIterator<Entry<V>> fastIterator() {
            List<Entry<V>> snap = snapshotEntries();
            return new ObjectIterator<>() {
                private final Iterator<Entry<V>> it = snap.iterator();
                private final MutableEntry reuse = new MutableEntry();
                private boolean hasLast;
                @Override public boolean hasNext() { return it.hasNext(); }
                @Override public Entry<V> next() {
                    Entry<V> src = it.next();
                    reuse.key = src.getLongKey(); reuse.value = src.getValue();
                    hasLast = true;
                    return reuse;
                }
                @Override public void remove() {
                    if (!hasLast) throw new IllegalStateException();
                    Long2ObjectConcurrentHashMap.this.remove(reuse.key);
                    hasLast = false;
                }
            };
        }

        @Override public void fastForEach(Consumer<? super Entry<V>> consumer) {
            MutableEntry reuse = new MutableEntry();
            for (int i = 0; i < segmentCount; i++) {
                long stamp = locks[i].readLock();
                try {
                    for (Long2ObjectMap.Entry<V> e : segments[i].long2ObjectEntrySet()) {
                        reuse.key = e.getLongKey(); reuse.value = e.getValue();
                        consumer.accept(reuse);
                    }
                } finally { locks[i].unlockRead(stamp); }
            }
        }

        @Override public int size() { return Long2ObjectConcurrentHashMap.this.size(); }
        @Override public boolean isEmpty() { return Long2ObjectConcurrentHashMap.this.isEmpty(); }
        @Override public void clear() { Long2ObjectConcurrentHashMap.this.clear(); }
        @Override public boolean contains(Object o) {
            if (!(o instanceof Entry<?> entry)) return false;
            V val = get(entry.getLongKey());
            return val != null && val.equals(entry.getValue());
        }
        @Override public boolean remove(Object o) {
            if (!(o instanceof Entry<?> entry)) return false;
            return Long2ObjectConcurrentHashMap.this.remove(entry.getLongKey(), entry.getValue());
        }
        @Override public boolean add(Entry<V> e) {
            V prev = put(e.getLongKey(), e.getValue());
            return !Objects.equals(prev, e.getValue());
        }
        @Override public Object[] toArray() { return snapshotEntries().toArray(); }
        @Override public <T> T[] toArray(T[] a) { return snapshotEntries().toArray(a); }
        @Override public boolean containsAll(Collection<?> c) { for (Object o : c) if (!contains(o)) return false; return true; }
        @Override public boolean addAll(Collection<? extends Entry<V>> c) { boolean m = false; for (Entry<V> e : c) m |= add(e); return m; }
        @Override public boolean removeAll(Collection<?> c) { boolean m = false; for (Object o : c) m |= remove(o); return m; }
        @Override public boolean retainAll(Collection<?> c) { throw new UnsupportedOperationException(); }
    }

    private final class MutableEntry implements Entry<V> {
        long key; V value;
        @Override public long getLongKey() { return key; }
        @Override public V getValue() { return value; }
        @Override public V setValue(V value) { this.value = value; return Long2ObjectConcurrentHashMap.this.put(key, value); }
        @Override public boolean equals(Object o) {
            if (!(o instanceof Map.Entry<?, ?> e)) return false;
            if (!(e.getKey() instanceof Long l)) return false;
            return key == l && Objects.equals(value, e.getValue());
        }
        @Override public int hashCode() { return Long.hashCode(key) ^ Objects.hashCode(value); }
        @Override public String toString() { return key + "=" + value; }
    }

    private record ImmutableEntry<V>(long key, V value) implements Entry<V> {
        @Override public long getLongKey() { return key; }
        @Override public V getValue() { return value; }
        @Override public V setValue(V value) { throw new UnsupportedOperationException(); }
        @Override public boolean equals(Object o) {
            if (!(o instanceof Map.Entry<?, ?> e)) return false;
            if (!(e.getKey() instanceof Long l)) return false;
            return key == l && Objects.equals(this.value, e.getValue());
        }
        @Override public int hashCode() { return Long.hashCode(key) ^ Objects.hashCode(value); }
        @Override public String toString() { return key + "=" + value; }
    }

    @Override public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof Map<?, ?> other)) return false;
        if (other.size() != size()) return false;
        for (Entry<V> e : snapshotEntries()) {
            Object otherVal = other.get(e.getLongKey());
            if (!Objects.equals(e.getValue(), otherVal)) return false;
        }
        return true;
    }

    @Override public int hashCode() {
        int h = 0;
        for (int i = 0; i < segmentCount; i++) {
            long stamp = locks[i].readLock();
            try {
                for (Long2ObjectMap.Entry<V> e : segments[i].long2ObjectEntrySet())
                    h += Long.hashCode(e.getLongKey()) ^ Objects.hashCode(e.getValue());
            } finally { locks[i].unlockRead(stamp); }
        }
        return h;
    }

    @Override public String toString() { return "Long2ObjectConcurrentHashMap[size=" + size() + "]"; }
}
