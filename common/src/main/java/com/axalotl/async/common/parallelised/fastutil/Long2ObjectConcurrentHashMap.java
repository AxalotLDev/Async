package com.axalotl.async.common.parallelised.fastutil;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.ObjectCollection;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.LongFunction;

/**
 * High-performance striped concurrent Long2ObjectMap.

 * Replaces ConcurrentHashMap<Long,V> wrapper approach which suffered from:
 * - TreeBin degradation due to Long.hashCode() collisions on chunk coordinates
 * - Autoboxing overhead on every get/put/containsKey
 * - O(log n) lookups in degenerate tree bins

 * This implementation uses:
 * - Striped segments of fastutil Long2ObjectOpenHashMap (open addressing, O(1))
 * - Stafford variant 13 mix function for excellent long key distribution
 * - synchronized per-segment (auto-releases on exception, no try/catch needed)
 * - Primitive long keys throughout - zero autoboxing
 *
 * @param <V> value type
 */
public final class Long2ObjectConcurrentHashMap<V> implements Long2ObjectMap<V> {

    private static final int SEGMENT_BITS = 5;
    private static final int SEGMENT_COUNT = 1 << SEGMENT_BITS; // 32
    private static final int SEGMENT_MASK = SEGMENT_COUNT - 1;
    private static final int INITIAL_SEGMENT_CAPACITY = 256;

    private final Long2ObjectOpenHashMap<V>[] segments;
    private final Object[] locks;
    private volatile V defaultReturnValue;

    public Long2ObjectConcurrentHashMap() {
        this(INITIAL_SEGMENT_CAPACITY * SEGMENT_COUNT);
    }

    @SuppressWarnings("unchecked")
    public Long2ObjectConcurrentHashMap(int expectedSize) {
        int perSegment = Math.max(16, expectedSize / SEGMENT_COUNT);
        segments = new Long2ObjectOpenHashMap[SEGMENT_COUNT];
        locks = new Object[SEGMENT_COUNT];
        for (int i = 0; i < SEGMENT_COUNT; i++) {
            segments[i] = new Long2ObjectOpenHashMap<>(perSegment, 0.75f);
            locks[i] = new Object();
        }
    }

    @SuppressWarnings("unused")
    public Long2ObjectConcurrentHashMap(int initialCapacity, float loadFactor) {
        this(initialCapacity);
    }

    @SuppressWarnings("unused")
    public Long2ObjectConcurrentHashMap(int initialCapacity, float loadFactor, int concurrencyLevel) {
        this(initialCapacity);
    }

    private static int spread(long key) {
        key = (key ^ (key >>> 30)) * 0xbf58476d1ce4e5b9L;
        key = (key ^ (key >>> 27)) * 0x94d049bb133111ebL;
        return (int) (key ^ (key >>> 31));
    }

    private static int segmentFor(long key) {
        return spread(key) & SEGMENT_MASK;
    }

    @Override
    public V get(long key) {
        int seg = segmentFor(key);
        synchronized (locks[seg]) {
            V v = segments[seg].get(key);
            return v == null ? defaultReturnValue : v;
        }
    }

    @Override
    public V put(long key, V value) {
        int seg = segmentFor(key);
        synchronized (locks[seg]) {
            V prev = segments[seg].put(key, value);
            return prev == null ? defaultReturnValue : prev;
        }
    }

    @Override
    public V remove(long key) {
        int seg = segmentFor(key);
        synchronized (locks[seg]) {
            V prev = segments[seg].remove(key);
            return prev == null ? defaultReturnValue : prev;
        }
    }

    @Override
    public boolean containsKey(long key) {
        int seg = segmentFor(key);
        synchronized (locks[seg]) {
            return segments[seg].containsKey(key);
        }
    }

    @Override
    public boolean containsValue(Object value) {
        if (value == null) return false;
        for (int i = 0; i < SEGMENT_COUNT; i++) {
            synchronized (locks[i]) {
                if (segments[i].containsValue(value)) return true;
            }
        }
        return false;
    }

    @Override
    public V getOrDefault(long key, V defaultValue) {
        int seg = segmentFor(key);
        synchronized (locks[seg]) {
            return segments[seg].getOrDefault(key, defaultValue);
        }
    }

    @Override
    public V putIfAbsent(long key, V value) {
        int seg = segmentFor(key);
        synchronized (locks[seg]) {
            V existing = segments[seg].get(key);
            if (existing != null) return existing;
            segments[seg].put(key, value);
            return defaultReturnValue;
        }
    }

    @Override
    public boolean remove(long key, Object value) {
        int seg = segmentFor(key);
        synchronized (locks[seg]) {
            V existing = segments[seg].get(key);
            if (existing != null && existing.equals(value)) {
                segments[seg].remove(key);
                return true;
            }
            return false;
        }
    }

    @Override
    public boolean replace(long key, V oldValue, V newValue) {
        int seg = segmentFor(key);
        synchronized (locks[seg]) {
            V existing = segments[seg].get(key);
            if (existing != null && existing.equals(oldValue)) {
                segments[seg].put(key, newValue);
                return true;
            }
            return false;
        }
    }

    @Override
    public V replace(long key, V value) {
        int seg = segmentFor(key);
        synchronized (locks[seg]) {
            if (segments[seg].containsKey(key)) {
                return segments[seg].put(key, value);
            }
            return defaultReturnValue;
        }
    }

    @Override
    public V compute(long key, BiFunction<? super Long, ? super V, ? extends V> remappingFunction) {
        int seg = segmentFor(key);
        synchronized (locks[seg]) {
            V oldVal = segments[seg].get(key);
            V newVal = remappingFunction.apply(key, oldVal);
            if (newVal != null) {
                segments[seg].put(key, newVal);
            } else if (oldVal != null) {
                segments[seg].remove(key);
            }
            return newVal == null ? defaultReturnValue : newVal;
        }
    }

    @Override
    public V computeIfAbsent(long key, LongFunction<? extends V> mappingFunction) {
        int seg = segmentFor(key);
        synchronized (locks[seg]) {
            if (segments[seg].containsKey(key)) {
                return segments[seg].get(key);
            }
            V computed = mappingFunction.apply(key);
            if (computed != null) {
                segments[seg].put(key, computed);
            }
            return computed == null ? defaultReturnValue : computed;
        }
    }

    @Override
    public V computeIfPresent(long key, BiFunction<? super Long, ? super V, ? extends V> remappingFunction) {
        int seg = segmentFor(key);
        synchronized (locks[seg]) {
            V existing = segments[seg].get(key);
            if (existing == null) return defaultReturnValue;
            V newVal = remappingFunction.apply(key, existing);
            if (newVal != null) {
                segments[seg].put(key, newVal);
            } else {
                segments[seg].remove(key);
            }
            return newVal == null ? defaultReturnValue : newVal;
        }
    }

    @Override
    public V merge(long key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
        int seg = segmentFor(key);
        synchronized (locks[seg]) {
            V existing = segments[seg].get(key);
            V newVal = existing == null ? value : remappingFunction.apply(existing, value);
            if (newVal != null) {
                segments[seg].put(key, newVal);
            } else {
                segments[seg].remove(key);
            }
            return newVal == null ? defaultReturnValue : newVal;
        }
    }

    @Override
    public void putAll(@NotNull Map<? extends Long, ? extends V> m) {
        for (Map.Entry<? extends Long, ? extends V> e : m.entrySet()) {
            put(e.getKey().longValue(), e.getValue());
        }
    }

    @Override
    public void clear() {
        for (int i = 0; i < SEGMENT_COUNT; i++) {
            synchronized (locks[i]) {
                segments[i].clear();
            }
        }
    }

    @Override
    public int size() {
        int total = 0;
        for (int i = 0; i < SEGMENT_COUNT; i++) {
            synchronized (locks[i]) {
                total += segments[i].size();
            }
        }
        return total;
    }

    @Override
    public boolean isEmpty() {
        for (int i = 0; i < SEGMENT_COUNT; i++) {
            synchronized (locks[i]) {
                if (!segments[i].isEmpty()) return false;
            }
        }
        return true;
    }

    @Override
    public void defaultReturnValue(V rv) {
        this.defaultReturnValue = rv;
        for (int i = 0; i < SEGMENT_COUNT; i++) {
            synchronized (locks[i]) {
                segments[i].defaultReturnValue(rv);
            }
        }
    }

    @Override
    public V defaultReturnValue() {
        return defaultReturnValue;
    }

    @Override
    public FastEntrySet<V> long2ObjectEntrySet() {
        return new SnapshotFastEntrySet();
    }

    @Override
    public @NotNull LongSet keySet() {
        it.unimi.dsi.fastutil.longs.LongOpenHashSet keys = new it.unimi.dsi.fastutil.longs.LongOpenHashSet(size());
        for (int i = 0; i < SEGMENT_COUNT; i++) {
            synchronized (locks[i]) {
                keys.addAll(segments[i].keySet());
            }
        }
        return keys;
    }

    @Override
    public @NotNull ObjectCollection<V> values() {
        List<V> vals = new ArrayList<>(size());
        for (int i = 0; i < SEGMENT_COUNT; i++) {
            synchronized (locks[i]) {
                vals.addAll(segments[i].values());
            }
        }
        return FastUtilHackUtil.wrap(vals);
    }

    private List<Entry<V>> snapshotEntries() {
        List<Entry<V>> snapshot = new ArrayList<>(size());
        for (int i = 0; i < SEGMENT_COUNT; i++) {
            synchronized (locks[i]) {
                for (Long2ObjectMap.Entry<V> e : segments[i].long2ObjectEntrySet()) {
                    snapshot.add(new ImmutableEntry<>(e.getLongKey(), e.getValue()));
                }
            }
        }
        return snapshot;
    }

    private final class SnapshotFastEntrySet implements FastEntrySet<V> {

        @Override
        public @NotNull ObjectIterator<Entry<V>> iterator() {
            List<Entry<V>> snapshot = snapshotEntries();
            return new ObjectIterator<>() {
                private final Iterator<Entry<V>> it = snapshot.iterator();
                private Entry<V> last;

                @Override public boolean hasNext() { return it.hasNext(); }

                @Override
                public Entry<V> next() {
                    last = it.next();
                    return last;
                }

                @Override
                public void remove() {
                    if (last == null) throw new IllegalStateException();
                    Long2ObjectConcurrentHashMap.this.remove(last.getLongKey());
                    last = null;
                }
            };
        }

        @Override
        public ObjectIterator<Entry<V>> fastIterator() {
            List<Entry<V>> snapshot = snapshotEntries();
            return new ObjectIterator<>() {
                private final Iterator<Entry<V>> it = snapshot.iterator();
                private final MutableEntry reuse = new MutableEntry();
                private boolean hasLast;

                @Override public boolean hasNext() { return it.hasNext(); }

                @Override
                public Entry<V> next() {
                    Entry<V> src = it.next();
                    reuse.key = src.getLongKey();
                    reuse.value = src.getValue();
                    hasLast = true;
                    return reuse;
                }

                @Override
                public void remove() {
                    if (!hasLast) throw new IllegalStateException();
                    Long2ObjectConcurrentHashMap.this.remove(reuse.key);
                    hasLast = false;
                }
            };
        }

        @Override
        public void fastForEach(Consumer<? super Entry<V>> consumer) {
            MutableEntry reuse = new MutableEntry();
            for (int i = 0; i < SEGMENT_COUNT; i++) {
                synchronized (locks[i]) {
                    for (Long2ObjectMap.Entry<V> e : segments[i].long2ObjectEntrySet()) {
                        reuse.key = e.getLongKey();
                        reuse.value = e.getValue();
                        consumer.accept(reuse);
                    }
                }
            }
        }

        @Override public int size() { return Long2ObjectConcurrentHashMap.this.size(); }
        @Override public boolean isEmpty() { return Long2ObjectConcurrentHashMap.this.isEmpty(); }
        @Override public void clear() { Long2ObjectConcurrentHashMap.this.clear(); }

        @Override
        public boolean contains(Object o) {
            if (!(o instanceof Entry<?> entry)) return false;
            V val = get(entry.getLongKey());
            return val != null && val.equals(entry.getValue());
        }

        @Override
        public boolean remove(Object o) {
            if (!(o instanceof Entry<?> entry)) return false;
            return Long2ObjectConcurrentHashMap.this.remove(entry.getLongKey(), entry.getValue());
        }

        @Override
        public boolean add(Entry<V> e) {
            V prev = put(e.getLongKey(), e.getValue());
            return !Objects.equals(prev, e.getValue());
        }

        @Override public Object @NotNull [] toArray() { return snapshotEntries().toArray(); }
        @Override public <T> T @NotNull [] toArray(T @NotNull [] a) { return snapshotEntries().toArray(a); }
        @Override public boolean containsAll(@NotNull Collection<?> c) { for (Object o : c) if (!contains(o)) return false; return true; }
        @Override public boolean addAll(@NotNull Collection<? extends Entry<V>> c) { boolean m = false; for (Entry<V> e : c) m |= add(e); return m; }
        @Override public boolean removeAll(@NotNull Collection<?> c) { boolean m = false; for (Object o : c) m |= remove(o); return m; }
        @Override public boolean retainAll(@NotNull Collection<?> c) { throw new UnsupportedOperationException(); }
    }

    private final class MutableEntry implements Entry<V> {
        long key;
        V value;

        @Override public long getLongKey() { return key; }
        @Override public V getValue() { return value; }

        @Override
        public V setValue(V value) {
            this.value = value;
            return Long2ObjectConcurrentHashMap.this.put(key, value);
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Map.Entry<?, ?> e)) return false;
            if (!(e.getKey() instanceof Long l)) return false;
            return key == l && Objects.equals(value, e.getValue());
        }

        @Override public int hashCode() { return Long.hashCode(key) ^ Objects.hashCode(value); }
        @Override public String toString() { return key + "=" + value; }
    }

    private record ImmutableEntry<V>(long key, V value) implements Entry<V> {

        @Override
        public long getLongKey() {
            return key;
        }

        @Override
        public V getValue() {
            return value;
        }

        @Override
        public V setValue(V value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Map.Entry<?, ?> e)) return false;
            if (!(e.getKey() instanceof Long l)) return false;
            return key == l && Objects.equals(this.value, e.getValue());
        }

        @Override
        public int hashCode() {
            return Long.hashCode(key) ^ Objects.hashCode(value);
        }

        @Override
        public String toString() {
            return key + "=" + value;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof Map<?, ?> other)) return false;
        if (other.size() != size()) return false;
        List<Entry<V>> snapshot = snapshotEntries();
        for (Entry<V> e : snapshot) {
            Object otherVal = other.get(e.getLongKey());
            if (!Objects.equals(e.getValue(), otherVal)) return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int h = 0;
        for (int i = 0; i < SEGMENT_COUNT; i++) {
            synchronized (locks[i]) {
                for (Long2ObjectMap.Entry<V> e : segments[i].long2ObjectEntrySet()) {
                    h += Long.hashCode(e.getLongKey()) ^ Objects.hashCode(e.getValue());
                }
            }
        }
        return h;
    }

    @Override
    public String toString() {
        return "StripedLong2ObjectMap[size=" + size() + ", segments=" + SEGMENT_COUNT + "]";
    }
}