package com.axalotl.async.common.parallelised.fastutil;

import it.unimi.dsi.fastutil.longs.*;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.ConcurrentSkipListSet;

/**
 * A thread-safe implementation of LongSortedSet backed by ConcurrentSkipListSet.
 * Provides concurrent access and maintains elements in sorted order.
 */
public final class ConcurrentLongSortedSet implements LongSortedSet {

    private final ConcurrentSkipListSet<Long> backing = new ConcurrentSkipListSet<>();

    /**
     * Creates a new empty concurrent sorted set
     */
    public ConcurrentLongSortedSet() {}

    /**
     * Creates a new concurrent sorted set containing elements from the given collection
     *
     * @param collection initial elements
     * @throws NullPointerException if collection is null
     */
    public ConcurrentLongSortedSet(Collection<Long> collection) {
        this();
        addAll(Objects.requireNonNull(collection, "Initial collection cannot be null"));
    }

    @Override
    public LongBidirectionalIterator iterator(long fromElement) {
        return FastUtilHackUtil.wrap(backing.tailSet(fromElement).iterator());
    }

    @Override
    public @NotNull LongBidirectionalIterator iterator() {
        return FastUtilHackUtil.wrap(backing.iterator());
    }

    @Override
    public int size() {
        return backing.size();
    }

    @Override
    public boolean isEmpty() {
        return backing.isEmpty();
    }

    @NotNull
    @Override
    public Object @NotNull [] toArray() {
        return backing.toArray();
    }

    @NotNull
    @Override
    public <T> T @NotNull [] toArray(@NotNull T @NotNull [] array) {
        return backing.toArray(array);
    }

    @Override
    public boolean containsAll(@NotNull Collection<?> collection) {
        return backing.containsAll(collection);
    }

    @Override
    public boolean addAll(@NotNull Collection<? extends Long> collection) {
        return backing.addAll(collection);
    }

    @Override
    public boolean removeAll(@NotNull Collection<?> collection) {
        return backing.removeAll(collection);
    }

    @Override
    public boolean retainAll(@NotNull Collection<?> collection) {
        return backing.retainAll(collection);
    }

    @Override
    public void clear() {
        backing.clear();
    }

    @Override
    public boolean add(long key) {
        return backing.add(key);
    }

    @Override
    public boolean contains(long key) {
        return backing.contains(key);
    }

    @Override
    public long[] toLongArray() {
        return longStream().toArray();
    }

    @Override
    public long[] toArray(long[] array) {
        long[] result = toLongArray();
        if (array.length < result.length) {
            return result;
        }
        System.arraycopy(result, 0, array, 0, result.length);
        if (array.length > result.length) {
            array[result.length] = 0L;
        }
        return array;
    }

    @Override
    public boolean addAll(LongCollection c) {
        boolean modified = false;
        for (LongIterator it = c.iterator(); it.hasNext(); ) {
            if (backing.add(it.nextLong())) {
                modified = true;
            }
        }
        return modified;
    }

    @Override
    public boolean containsAll(LongCollection c) {
        for (LongIterator it = c.iterator(); it.hasNext(); ) {
            if (!backing.contains(it.nextLong())) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean removeAll(LongCollection c) {
        boolean modified = false;
        for (LongIterator it = c.iterator(); it.hasNext(); ) {
            if (backing.remove(it.nextLong())) {
                modified = true;
            }
        }
        return modified;
    }

    @Override
    public boolean retainAll(LongCollection c) {
        return backing.retainAll(c);
    }

    @Override
    public boolean remove(long k) {
        return backing.remove(k);
    }

    @Override
    public LongSortedSet subSet(long fromElement, long toElement) {
        return new ConcurrentLongSortedSet(backing.subSet(Math.min(fromElement, toElement), Math.max(fromElement, toElement)));
    }

    @Override
    public LongSortedSet headSet(long toElement) {
        return new ConcurrentLongSortedSet(backing.headSet(toElement));
    }

    @Override
    public LongSortedSet tailSet(long fromElement) {
        return new ConcurrentLongSortedSet(backing.tailSet(fromElement));
    }

    @Override
    public LongComparator comparator() {
        return null;
    }

    @Override
    public long firstLong() {
        return backing.first();
    }

    @Override
    public long lastLong() {
        return backing.last();
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof LongSortedSet that && backing.equals(that));
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