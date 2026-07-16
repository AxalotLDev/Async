package com.axalotl.async.api.fastutil;

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
 * A type-specific {@link Map}; provides some additional methods that use polymorphism to avoid
 * (un)boxing, and handling of a default return value.
 *
 * <p>
 * Besides extending the corresponding type-specific {@linkplain it.unimi.dsi.fastutil.Function
 * function}, this interface strengthens {@link Map#entrySet()}, {@link #keySet()} and
 * {@link #values()}. Moreover, a number of methods, such as {@link #size()},
 * {@link #defaultReturnValue()}, etc., are un-defaulted as their function default do not make sense
 * for a map. Maps returning entry sets of type {@link FastEntrySet} support also fast iteration.
 *
 * <p>
 * A submap or subset may or may not have an independent default return value (which however must be
 * initialized to the default return value of the originator).
 *
 * @see Map
 */
public final class Int2ObjectConcurrentHashMap<V> implements Int2ObjectMap<V> {

    private final ConcurrentHashMap<Integer, V> backing = new ConcurrentHashMap<>(16, 0.9f, 1);
    private V defaultReturnValue;

    /**
     * Creates an empty map.
     */
    public Int2ObjectConcurrentHashMap() {
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
        if (!(o instanceof Map<?, ?>)) return false;
        return backing.equals(o);
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
