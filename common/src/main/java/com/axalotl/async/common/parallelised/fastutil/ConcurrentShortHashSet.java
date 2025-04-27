package com.axalotl.async.common.parallelised.fastutil;

import it.unimi.dsi.fastutil.shorts.ShortCollection;
import it.unimi.dsi.fastutil.shorts.ShortIterator;
import it.unimi.dsi.fastutil.shorts.ShortSet;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A thread-safe implementation of ShortSet using ConcurrentHashMap.KeySetView as backing storage.
 * This implementation provides concurrent access and high performance for concurrent operations.
 */
public final class ConcurrentShortHashSet implements ShortSet {

    private final ConcurrentHashMap.KeySetView<Short, Boolean> backing;

    /**
     * Creates a new empty concurrent short set.
     */
    public ConcurrentShortHashSet() {
        this.backing = ConcurrentHashMap.newKeySet();
    }

    @Override
    public int size() {
        return backing.size();
    }

    @Override
    public boolean isEmpty() {
        return backing.isEmpty();
    }

    @Override
    public @NotNull ShortIterator iterator() {
        return FastUtilHackUtil.itrShortWrap(backing);
    }

    @NotNull
    @Override
    public Object @NotNull [] toArray() {
        return backing.toArray();
    }

    @NotNull
    @Override
    public <T> T @NotNull [] toArray(@NotNull T @NotNull [] array) {
        Objects.requireNonNull(array, "Array cannot be null");
        return backing.toArray(array);
    }

    @Override
    public boolean containsAll(@NotNull Collection<?> collection) {
        Objects.requireNonNull(collection, "Collection cannot be null");
        return backing.containsAll(collection);
    }

    @Override
    public boolean addAll(@NotNull Collection<? extends Short> collection) {
        Objects.requireNonNull(collection, "Collection cannot be null");
        return backing.addAll(collection);
    }

    @Override
    public boolean removeAll(@NotNull Collection<?> collection) {
        Objects.requireNonNull(collection, "Collection cannot be null");
        return backing.removeAll(collection);
    }

    @Override
    public boolean retainAll(@NotNull Collection<?> collection) {
        Objects.requireNonNull(collection, "Collection cannot be null");
        return backing.retainAll(collection);
    }

    @Override
    public void clear() {
        backing.clear();
    }

    @Override
    public boolean add(short key) {
        return backing.add(key);
    }

    @Override
    public boolean contains(short key) {
        return backing.contains(key);
    }

    @Override
    public short[] toShortArray() {
        int size = backing.size();
        short[] result = new short[size];
        int i = 0;
        // Используем for-each для обхода backing
        for (Short value : backing) {
            result[i++] = value;
        }
        return result;
    }

    @Override
    public short[] toArray(short[] array) {
        Objects.requireNonNull(array, "Array cannot be null");
        short[] result = toShortArray();
        if (array.length < result.length) {
            return result;
        }
        System.arraycopy(result, 0, array, 0, result.length);
        if (array.length > result.length) {
            array[result.length] = 0;
        }
        return array;
    }

    @Override
    public boolean addAll(ShortCollection c) {
        Objects.requireNonNull(c, "Collection cannot be null");
        boolean modified = false;
        ShortIterator iterator = c.iterator();
        while (iterator.hasNext()) {
            modified |= add(iterator.nextShort());
        }
        return modified;
    }

    @Override
    public boolean containsAll(ShortCollection c) {
        Objects.requireNonNull(c, "Collection cannot be null");
        ShortIterator iterator = c.iterator();
        while (iterator.hasNext()) {
            if (!contains(iterator.nextShort())) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean removeAll(ShortCollection c) {
        Objects.requireNonNull(c, "Collection cannot be null");
        boolean modified = false;
        ShortIterator iterator = c.iterator();
        while (iterator.hasNext()) {
            modified |= remove(iterator.nextShort());
        }
        return modified;
    }

    @Override
    public boolean retainAll(ShortCollection c) {
        Objects.requireNonNull(c, "Collection cannot be null");
        return backing.retainAll(c);
    }

    @Override
    public boolean remove(short k) {
        return backing.remove(k);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ShortSet that)) return false;
        if (size() != that.size()) return false;
        return containsAll(that);
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
