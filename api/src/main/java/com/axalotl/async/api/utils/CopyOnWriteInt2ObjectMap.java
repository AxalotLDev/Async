package com.axalotl.async.api.utils;

import it.unimi.dsi.fastutil.ints.AbstractInt2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.ObjectCollection;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.jspecify.annotations.NonNull;

/**
 * An {@link Int2ObjectOpenHashMap} that any thread may read and iterate while another thread changes it.
 *
 * <p>Each change clones the published map, changes the clone and publishes it, so a reader always walks a
 * map that was a complete state at some point: neither a rehash nor an entry shift can move under its
 * iterator. The clone keeps the table layout, so iteration order stays that of a plain
 * {@link Int2ObjectOpenHashMap}. Reads are lock-free and allocation-free: the views are built once per
 * change and every thread reads the same published instance. Changes are rare enough that cloning beats a
 * monitor on every read. The {@code defaultReturnValue} lives in the published map, so it survives every
 * change.</p>
 *
 * <p>Views are read-only snapshots. Intended for maps that are read on every tick and written only when
 * something starts or stops being tracked, such as a level's ender dragon parts.</p>
 */
public final class CopyOnWriteInt2ObjectMap<V> extends AbstractInt2ObjectMap<V> {

    private record Published<V>(Int2ObjectOpenHashMap<V> map,
                                IntSet keySet,
                                ObjectCollection<V> values,
                                ObjectSet<Int2ObjectMap.Entry<V>> entrySet) {
    }

    private volatile Published<V> published = publish(new Int2ObjectOpenHashMap<>());

    private static <V> Published<V> publish(Int2ObjectOpenHashMap<V> map) {
        Int2ObjectMap<V> view = Int2ObjectMaps.unmodifiable(map);
        return new Published<>(map, view.keySet(), view.values(), view.int2ObjectEntrySet());
    }

    @Override
    public int size() {
        return published.map().size();
    }

    @Override
    public boolean isEmpty() {
        return published.map().isEmpty();
    }

    @Override
    public boolean containsKey(int key) {
        return published.map().containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return published.map().containsValue(value);
    }

    @Override
    public V defaultReturnValue() {
        return published.map().defaultReturnValue();
    }

    @Override
    public synchronized void defaultReturnValue(V defaultReturnValue) {
        published.map().defaultReturnValue(defaultReturnValue);
    }

    @Override
    public V get(int key) {
        return published.map().get(key);
    }

    @Override
    public ObjectSet<Int2ObjectMap.Entry<V>> int2ObjectEntrySet() {
        return published.entrySet();
    }

    @Override
    public @NonNull IntSet keySet() {
        return published.keySet();
    }

    @Override
    public @NonNull ObjectCollection<V> values() {
        return published.values();
    }

    @Override
    public synchronized V put(int key, V value) {
        Int2ObjectOpenHashMap<V> next = published.map().clone();
        V previous = next.put(key, value);
        published = publish(next);
        return previous;
    }

    @Override
    public synchronized V remove(int key) {
        if (!published.map().containsKey(key)) return defaultReturnValue();
        Int2ObjectOpenHashMap<V> next = published.map().clone();
        V previous = next.remove(key);
        published = publish(next);
        return previous;
    }

    @Override
    public synchronized void clear() {
        if (published.map().isEmpty()) return;
        Int2ObjectOpenHashMap<V> next = new Int2ObjectOpenHashMap<>(0);
        next.defaultReturnValue(defaultReturnValue());
        published = publish(next);
    }
}
