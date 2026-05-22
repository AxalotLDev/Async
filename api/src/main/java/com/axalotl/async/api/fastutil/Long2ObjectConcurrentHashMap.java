package com.axalotl.async.api.fastutil;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectCollection;
import it.unimi.dsi.fastutil.objects.ObjectIterator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.LongFunction;

public final class Long2ObjectConcurrentHashMap<V> implements Long2ObjectMap<V> {

    private final ConcurrentHashMap<Long, V> map;
    private volatile V defaultReturnValue;

    public Long2ObjectConcurrentHashMap() { this(16); }

    public Long2ObjectConcurrentHashMap(int expectedSize) {
        this.map = new ConcurrentHashMap<>(Math.max(8, expectedSize));
    }

    @SuppressWarnings("unused")
    public Long2ObjectConcurrentHashMap(int expectedSize, int concurrencyLevel) { this(expectedSize); }
    @SuppressWarnings("unused")
    public Long2ObjectConcurrentHashMap(int initialCapacity, float loadFactor) {
        this.map = new ConcurrentHashMap<>(Math.max(8, initialCapacity), loadFactor);
    }
    @SuppressWarnings("unused")
    public Long2ObjectConcurrentHashMap(int initialCapacity, float loadFactor, int concurrencyLevel) {
        this.map = new ConcurrentHashMap<>(Math.max(8, initialCapacity), loadFactor, Math.max(1, concurrencyLevel));
    }

    static long mix(long k) {
        k ^= k >>> 33;
        k *= 0xff51afd7ed558ccdL;
        k ^= k >>> 33;
        k *= 0xc4ceb9fe1a85ec53L;
        k ^= k >>> 33;
        return k;
    }

    static long unmix(long h) {
        h ^= h >>> 33;
        h *= 0x9cb4b2f8129337dbL;
        h ^= h >>> 33;
        h *= 0x4f74430c22a54005L;
        h ^= h >>> 33;
        return h;
    }

    @Override public V get(long key) {
        V v = map.get(mix(key));
        return v == null ? defaultReturnValue : v;
    }

    @Override public V put(long key, V value) {
        Objects.requireNonNull(value, "null values are not supported");
        V prev = map.put(mix(key), value);
        return prev == null ? defaultReturnValue : prev;
    }

    @Override public V remove(long key) {
        V prev = map.remove(mix(key));
        return prev == null ? defaultReturnValue : prev;
    }

    @Override public boolean containsKey(long key) { return map.containsKey(mix(key)); }
    @Override public boolean containsValue(Object value) { return map.containsValue(value); }

    @Override public V getOrDefault(long key, V defaultValue) {
        V v = map.get(mix(key));
        return v == null ? defaultValue : v;
    }

    @Override public V putIfAbsent(long key, V value) {
        Objects.requireNonNull(value);
        V prev = map.putIfAbsent(mix(key), value);
        return prev == null ? defaultReturnValue : prev;
    }

    @Override public boolean remove(long key, Object value) {
        return value != null && map.remove(mix(key), value);
    }

    @Override public boolean replace(long key, V oldValue, V newValue) {
        Objects.requireNonNull(newValue);
        if (oldValue == null) return false;
        return map.replace(mix(key), oldValue, newValue);
    }

    @Override public V replace(long key, V value) {
        Objects.requireNonNull(value);
        V prev = map.replace(mix(key), value);
        return prev == null ? defaultReturnValue : prev;
    }

    @Override public V compute(long key, BiFunction<? super Long, ? super V, ? extends V> remappingFunction) {
        Long mixed = mix(key);
        V v = map.compute(mixed, (m, oldV) -> remappingFunction.apply(key, oldV));
        return v == null ? defaultReturnValue : v;
    }

    @Override public V computeIfAbsent(long key, LongFunction<? extends V> mappingFunction) {
        V v = map.computeIfAbsent(mix(key), m -> mappingFunction.apply(key));
        return v == null ? defaultReturnValue : v;
    }

    @Override public V computeIfPresent(long key, BiFunction<? super Long, ? super V, ? extends V> remappingFunction) {
        V v = map.computeIfPresent(mix(key), (m, oldV) -> remappingFunction.apply(key, oldV));
        return v == null ? defaultReturnValue : v;
    }

    @Override public V merge(long key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
        Objects.requireNonNull(value);
        V v = map.merge(mix(key), value, remappingFunction);
        return v == null ? defaultReturnValue : v;
    }

    @Override public void putAll(Map<? extends Long, ? extends V> m) {
        for (Map.Entry<? extends Long, ? extends V> e : m.entrySet()) {
            map.put(mix(e.getKey()), e.getValue());
        }
    }

    @Override public void clear() { map.clear(); }
    @Override public int  size()  { return map.size(); }
    @Override public boolean isEmpty() { return map.isEmpty(); }

    @Override public void defaultReturnValue(V rv) { this.defaultReturnValue = rv; }
    @Override public V defaultReturnValue() { return defaultReturnValue; }

    @Override public FastEntrySet<V> long2ObjectEntrySet() { return new FastEntrySetView(); }

    @Override public LongSet keySet() {
        LongOpenHashSet set = new LongOpenHashSet(size());
        for (Long mixed : map.keySet()) set.add(unmix(mixed.longValue()));
        return set;
    }

    @Override public ObjectCollection<V> values() {
        List<V> list = new ArrayList<>(size());
        list.addAll(map.values());
        return new ObjectArrayList<>(list);
    }

    private final class FastEntrySetView implements FastEntrySet<V> {
        @Override public ObjectIterator<Entry<V>> iterator() {
            return new EntryIter(map.entrySet().iterator());
        }
        @Override public ObjectIterator<Entry<V>> fastIterator() {
            return new FastEntryIter(map.entrySet().iterator());
        }
        @Override public void fastForEach(Consumer<? super Entry<V>> action) {
            MutableEntry<V> reuse = new MutableEntry<>();
            for (Map.Entry<Long, V> e : map.entrySet()) {
                reuse.key = unmix(e.getKey());
                reuse.value = e.getValue();
                action.accept(reuse);
            }
        }
        @Override public int size() { return map.size(); }
        @Override public boolean isEmpty() { return map.isEmpty(); }
        @Override public void clear() { map.clear(); }
        @Override public boolean contains(Object o) {
            if (!(o instanceof Entry<?> en)) return false;
            V v = get(en.getLongKey());
            return Objects.equals(v, en.getValue());
        }
        @Override public boolean remove(Object o) {
            if (!(o instanceof Entry<?> en)) return false;
            return Long2ObjectConcurrentHashMap.this.remove(en.getLongKey(), en.getValue());
        }
        @Override public boolean add(Entry<V> e) {
            V prev = put(e.getLongKey(), e.getValue());
            return !Objects.equals(prev, e.getValue());
        }
        @Override public Object[] toArray() {
            List<Entry<V>> list = new ArrayList<>(size());
            for (Map.Entry<Long, V> e : map.entrySet())
                list.add(new ImmutableEntry<>(unmix(e.getKey()), e.getValue()));
            return list.toArray();
        }
        @Override public <T> T[] toArray(T[] a) {
            List<Entry<V>> list = new ArrayList<>(size());
            for (Map.Entry<Long, V> e : map.entrySet())
                list.add(new ImmutableEntry<>(unmix(e.getKey()), e.getValue()));
            return list.toArray(a);
        }
        @Override public boolean containsAll(Collection<?> c) { for (Object o : c) if (!contains(o)) return false; return true; }
        @Override public boolean addAll(Collection<? extends Entry<V>> c) { boolean m = false; for (Entry<V> e : c) m |= add(e); return m; }
        @Override public boolean removeAll(Collection<?> c) { boolean m = false; for (Object o : c) m |= remove(o); return m; }
        @Override public boolean retainAll(Collection<?> c) { throw new UnsupportedOperationException(); }
    }

    private final class EntryIter implements ObjectIterator<Entry<V>> {
        private final Iterator<Map.Entry<Long, V>> it;
        private Entry<V> last;
        EntryIter(Iterator<Map.Entry<Long, V>> it) { this.it = it; }
        @Override public boolean hasNext() { return it.hasNext(); }
        @Override public Entry<V> next() {
            Map.Entry<Long, V> e = it.next();
            last = new ImmutableEntry<>(unmix(e.getKey()), e.getValue());
            return last;
        }
        @Override public void remove() {
            if (last == null) throw new IllegalStateException();
            Long2ObjectConcurrentHashMap.this.remove(last.getLongKey());
            last = null;
        }
    }

    private final class FastEntryIter implements ObjectIterator<Entry<V>> {
        private final Iterator<Map.Entry<Long, V>> it;
        private final MutableEntry<V> reuse = new MutableEntry<>();
        private boolean hasLast;
        FastEntryIter(Iterator<Map.Entry<Long, V>> it) { this.it = it; }
        @Override public boolean hasNext() { return it.hasNext(); }
        @Override public Entry<V> next() {
            Map.Entry<Long, V> e = it.next();
            reuse.key = unmix(e.getKey());
            reuse.value = e.getValue();
            hasLast = true;
            return reuse;
        }
        @Override public void remove() {
            if (!hasLast) throw new IllegalStateException();
            Long2ObjectConcurrentHashMap.this.remove(reuse.key);
            hasLast = false;
        }
    }

    private static final class MutableEntry<V> implements Entry<V> {
        long key;
        V value;
        @Override public long getLongKey() { return key; }
        @Override public V getValue() { return value; }
        @Override public V setValue(V value) { V old = this.value; this.value = value; return old; }
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
    }

    @Override public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof Map<?, ?> other)) return false;
        if (other.size() != size()) return false;
        for (Entry<V> e : long2ObjectEntrySet()) {
            Object ov = other.get(e.getLongKey());
            if (!Objects.equals(ov, e.getValue())) return false;
        }
        return true;
    }

    @Override public int hashCode() {
        int h = 0;
        for (Entry<V> e : long2ObjectEntrySet())
            h += Long.hashCode(e.getLongKey()) ^ Objects.hashCode(e.getValue());
        return h;
    }

    @Override public String toString() { return "Long2ObjectConcurrentHashMap[size=" + size() + "]"; }
}
