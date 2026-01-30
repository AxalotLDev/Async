package com.axalotl.async.common.parallelised.fastutil;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.ObjectCollection;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.LongFunction;

/**
 * A thread-safe implementation of Long2ObjectMap using ConcurrentHashMap as backing storage.
 * Provides concurrent access and high performance for long-to-object mappings.
 *
 * @param <V> the type of values maintained by this map
 */
public final class Long2ObjectConcurrentHashMap<V> implements Long2ObjectMap<V> {

    private final ConcurrentHashMap<Long, V> backing;
    private V defaultReturnValue;

    /**
     * Creates a new empty concurrent map with default initial capacity
     */
    public Long2ObjectConcurrentHashMap() {
        this.backing = new ConcurrentHashMap<>();
    }

    @Override
    public V get(long key) {
        V value = backing.get(key);
        return (value == null && !backing.containsKey(key)) ? defaultReturnValue : value;
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
    public void putAll(@NotNull Map<? extends Long, ? extends V> m) {
        Objects.requireNonNull(m, "Source map cannot be null");
        backing.putAll(m);
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
    public ObjectSet<Entry<V>> long2ObjectEntrySet() {
        return FastUtilHackUtil.entrySetLongWrap(backing);
    }

    @Override
    public @NotNull LongSet keySet() {
        return FastUtilHackUtil.wrapLongSet(backing.keySet());
    }

    @Override
    public @NotNull ObjectCollection<V> values() {
        return FastUtilHackUtil.wrap(backing.values());
    }

    @Override
    public boolean containsKey(long key) {
        return backing.containsKey(key);
    }

    @Override
    public V put(long key, V value) {
        V previous = backing.put(key, value);
        return (previous == null && !backing.containsKey(key)) ? defaultReturnValue : previous;
    }

    @Override
    public V remove(long key) {
        V previous = backing.remove(key);
        return (previous == null) ? defaultReturnValue : previous;
    }

    @Override
    public void clear() {
        backing.clear();
    }

    /**
     * CRITICAL: Atomic computeIfAbsent for thread-safety.
     * Default Long2ObjectMap implementation is NOT thread-safe!
     */
    @Override
    public V computeIfAbsent(long key, @NotNull LongFunction<? extends V> mappingFunction) {
        Objects.requireNonNull(mappingFunction);
        return backing.computeIfAbsent(key, k -> mappingFunction.apply(k));
    }

    @Override
    public V compute(long key, @NotNull BiFunction<? super Long, ? super V, ? extends V> remappingFunction) {
        Objects.requireNonNull(remappingFunction, "Remapping function cannot be null");
        return backing.compute(key, remappingFunction);
    }

    public V getOrDefault(long key, V defaultValue) {
        V value = backing.get(key);
        return (value == null && !backing.containsKey(key)) ? defaultValue : value;
    }

    /**
     * Associates the specified value with the specified key if no value is present
     *
     * @param key   key with which the specified value is to be associated
     * @param value value to be associated with the specified key
     * @return the previous value or defaultReturnValue if none
     */
    public V putIfAbsent(long key, V value) {
        V previous = backing.putIfAbsent(key, value);
        return (previous == null) ? defaultReturnValue : previous;
    }

    /**
     * Removes the entry for the specified key only if it is currently mapped to the specified value
     *
     * @param key   key with which the specified value is associated
     * @param value value expected to be associated with the specified key
     * @return true if the value was removed
     */
    public boolean remove(long key, Object value) {
        return backing.remove(key, value);
    }

    /**
     * Replaces the entry for the specified key only if it is currently mapped to the specified value
     *
     * @param key      key with which the specified value is associated
     * @param oldValue value expected to be associated with the specified key
     * @param newValue value to be associated with the specified key
     * @return true if the value was replaced
     */
    public boolean replace(long key, V oldValue, V newValue) {
        return backing.replace(key, oldValue, newValue);
    }

    /**
     * Replaces the entry for the specified key only if it is currently mapped to some value
     *
     * @param key   key with which the specified value is associated
     * @param value value to be associated with the specified key
     * @return the previous value or defaultReturnValue if none
     */
    public V replace(long key, V value) {
        V previous = backing.replace(key, value);
        return (previous == null) ? defaultReturnValue : previous;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Long2ObjectMap<?> that)) return false;

        if (size() != that.size()) return false;
        return long2ObjectEntrySet().containsAll(that.long2ObjectEntrySet());
    }

    @Override
    public int hashCode() {
        return backing.hashCode();
    }

    @Override
    public String toString() {
        return backing.toString();
    }
}