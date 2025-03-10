package com.axalotl.async.parallelised.fastutil;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.ObjectCollection;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.LongFunction;

public class Long2ObjectOpenConcurrentHashMap<V> extends Long2ObjectOpenHashMap<V> {

    private final Map<Long, V> backing = new ConcurrentHashMap<>();

    @Override
    public V get(long key) {
        return backing.get(key);
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
    public void putAll(Map<? extends Long, ? extends V> m) {
        backing.putAll(m);
    }

    @Override
    public int size() {
        return backing.size();
    }

    @Override
    public V defaultReturnValue() {
        return null;
    }

    @Override
    public FastEntrySet<V> long2ObjectEntrySet() {
        return FastUtilHackUtil.entrySetLongWrapFast(backing);
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
        return backing.put(key, value);
    }

    @Override
    public V remove(long key) {
        return backing.remove(key);
    }

    @Override
    public boolean trim() {
        return true;
    }

    @Override
    public boolean trim(int n) {
        return true;
    }

    @Override
    public boolean replace(long k, V oldValue, V v) {
        return backing.replace(k, oldValue, v);
    }

    @Override
    public V replace(long k, V v) {
        return backing.replace(k, v);
    }

    @Override
    public boolean replace(Long k, V oldValue, V v) {
        return backing.replace(k, oldValue, v);
    }

    @Override
    public V replace(Long k, V v) {
        return backing.replace(k, v);
    }

    @Override
    public boolean remove(long k, Object v) {
        return backing.remove(k, v);
    }

    @Override
    public V putIfAbsent(long k, V v) {
        return backing.putIfAbsent(k, v);
    }

    @Override
    public V putIfAbsent(Long k, V v) {
        return backing.putIfAbsent(k, v);
    }

    @Override
    public V merge(long k, V v, BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
        return backing.merge(k, v, remappingFunction);
    }

    @Override
    public V merge(Long k, @NotNull V v, @NotNull BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
        return backing.merge(k, v, remappingFunction);
    }

    @Override
    public int hashCode() {
        return backing.hashCode();
    }

    @Override
    public V getOrDefault(long k, V defaultValue) {
        return backing.getOrDefault(k, defaultValue);
    }

    @Override
    public V computeIfPresent(long k, BiFunction<? super Long, ? super V, ? extends V> remappingFunction) {
        return backing.computeIfPresent(k, remappingFunction);
    }

    @Override
    public V computeIfPresent(Long k, @NotNull BiFunction<? super Long, ? super V, ? extends V> remappingFunction) {
        return backing.computeIfPresent(k, remappingFunction);
    }

    @Override
    public V computeIfAbsent(long k, LongFunction<? extends V> mappingFunction) {
        return backing.computeIfAbsent(k, mappingFunction::apply);
    }

    @Override
    public V compute(long k, BiFunction<? super Long, ? super V, ? extends V> remappingFunction) {
        return backing.compute(k, remappingFunction);
    }

    @Override
    public V compute(Long k, @NotNull BiFunction<? super Long, ? super V, ? extends V> remappingFunction) {
        return backing.compute(k, remappingFunction);
    }

    @Override
    public Long2ObjectOpenHashMap<V> clone() {
        throw new UnsupportedOperationException("Clone not supported");
    }

    @Override
    public void clear() {
        backing.clear();
    }

    @Override
    public boolean remove(Object key, Object value) {
        return backing.remove(key, value);
    }
}