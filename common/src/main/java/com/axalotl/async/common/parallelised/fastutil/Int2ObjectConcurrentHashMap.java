package com.axalotl.async.common.parallelised.fastutil;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.ObjectCollection;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

/**
 * A thread-safe implementation of Int2ObjectMap using ConcurrentHashMap as backing storage.
 * Provides concurrent access and high performance for integer-keyed maps.
 *
 * @param <V> the type of values maintained by this map
 */
public final class Int2ObjectConcurrentHashMap<V> implements Int2ObjectMap<V> {

    private final ConcurrentHashMap<Integer, V> backing;
    private V defaultReturnValue;

    public Int2ObjectConcurrentHashMap() {
        this.backing = new ConcurrentHashMap<>(16, 0.9f, 1);
    }

    @Override
    public V get(int key) {
        return backing.getOrDefault(key, defaultReturnValue);
    }

    @Override
    public boolean isEmpty() {
        return backing.isEmpty();
    }

    @Override
    public boolean containsValue(Object value) {
        return backing.containsValue(value);
    }

    @Override
    public void putAll(@NotNull Map<? extends Integer, ? extends V> m) {
        backing.putAll(Objects.requireNonNull(m, "Source map cannot be null"));
    }

    @Override
    public int size() {
        return backing.size();
    }

    @Override
    public void defaultReturnValue(V rv) {
        this.defaultReturnValue = rv;
    }

    @Override
    public V defaultReturnValue() {
        return defaultReturnValue;
    }

    @Override
    public ObjectSet<Entry<V>> int2ObjectEntrySet() {
        return FastUtilHackUtil.entrySetIntWrap(backing);
    }

    @Override
    public @NotNull IntSet keySet() {
        return FastUtilHackUtil.wrapIntSet(backing.keySet());
    }

    @Override
    public @NotNull ObjectCollection<V> values() {
        return FastUtilHackUtil.wrap(backing.values());
    }

    @Override
    public boolean containsKey(int key) {
        return backing.containsKey(key);
    }

    @Override
    public V put(int key, V value) {
        return backing.put(key, value);
    }

    @Override
    public V remove(int key) {
        return backing.remove(key);
    }

    @Override
    public void clear() {
        backing.clear();
    }

    public V compute(int key, BiFunction<? super Integer, ? super V, ? extends V> remappingFunction) {
        return backing.compute(key, Objects.requireNonNull(remappingFunction));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Int2ObjectMap<?> that)) return false;
        return size() == that.size() && int2ObjectEntrySet().containsAll(that.int2ObjectEntrySet());
    }

    @Override
    public int hashCode() {
        return backing.hashCode();
    }

    @Override
    public String toString() {
        return backing.toString();
    }

    public V getOrDefault(int key, V defaultValue) {
        return backing.getOrDefault(key, defaultValue);
    }

    public V putIfAbsent(int key, V value) {
        return backing.putIfAbsent(key, value);
    }

    public boolean remove(int key, Object value) {
        V previous = backing.remove(key);
        return backing.remove(key, previous);
    }

    public boolean replace(int key, V oldValue, V newValue) {
        return backing.replace(key, oldValue, newValue);
    }

    public V replace(int key, V value) {
        return backing.replace(key, value);
    }
}
