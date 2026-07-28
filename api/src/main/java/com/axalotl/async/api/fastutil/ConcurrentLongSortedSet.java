package com.axalotl.async.api.fastutil;

import it.unimi.dsi.fastutil.longs.*;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.SortedSet;
import java.util.concurrent.ConcurrentSkipListSet;

/**
 * A thread-safe implementation of LongSortedSet backed by ConcurrentSkipListSet.
 * Provides concurrent access and maintains elements in sorted order.
 *
 * <p>
 * A type-specific {@link ConcurrentSkipListSet}; provides some additional methods that use polymorphism to
 * avoid (un)boxing.
 *
 * <p>
 * Additionally, this interface strengthens {@link #iterator()}, {@link #comparator()} (for
 * primitive types), {@link ConcurrentSkipListSet#subSet(Object, Object)}, {@link ConcurrentSkipListSet#headSet(Object)} and
 * {@link ConcurrentSkipListSet#tailSet(Object)}.
 *
 * @see SortedSet
 */
public final class ConcurrentLongSortedSet implements LongSortedSet {

    private final NavigableSet<Long> backing;

    /**
     * Creates a new empty concurrent sorted set
     */
    public ConcurrentLongSortedSet() {
        this.backing = new ConcurrentSkipListSet<>();
    }

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

    /**
     * Wraps a live range view of another concurrent sorted set; changes in the parent are
     * visible here and vice versa. Used by {@link #subSet}, {@link #headSet} and
     * {@link #tailSet} to avoid copying the range.
     */
    private ConcurrentLongSortedSet(NavigableSet<Long> view) {
        this.backing = view;
    }

    /**
     * Returns a type-specific {@link it.unimi.dsi.fastutil.BidirectionalIterator} on the elements in
     * this set, starting from a given element of the domain (optional operation).
     *
     * <p>
     * This method returns a type-specific bidirectional iterator with given starting point. The
     * starting point is any element comparable to the elements of this set (even if it does not
     * actually belong to the set). The next element of the returned iterator is the least element of
     * the set that is greater than the starting point (if there are no elements greater than the
     * starting point, {@link it.unimi.dsi.fastutil.BidirectionalIterator#hasNext() hasNext()} will
     * return {@code false}). The previous element of the returned iterator is the greatest element of
     * the set that is smaller than or equal to the starting point (if there are no elements smaller
     * than or equal to the starting point,
     * {@link it.unimi.dsi.fastutil.BidirectionalIterator#hasPrevious() hasPrevious()} will return
     * {@code false}).
     *
     * <p>
     * Note that passing the last element of the set as starting point and calling
     * {@link it.unimi.dsi.fastutil.BidirectionalIterator#previous() previous()} you can traverse the
     * entire set in reverse order.
     *
     * @param fromElement an element to start from.
     * @return a bidirectional iterator on the element in this set, starting at the given element.
     * @throws UnsupportedOperationException if this set does not support iterators with a starting
     *             point.
     */
    @Override
    public LongBidirectionalIterator iterator(long fromElement) {
        return FastUtilHackUtil.wrap(backing.tailSet(fromElement).iterator());
    }

    /**
     * Returns a type-specific {@link it.unimi.dsi.fastutil.BidirectionalIterator} on the elements in
     * this set.
     *
     * <p>
     * This method returns a parameterised bidirectional iterator. The iterator can be moreover safely
     * cast to a type-specific iterator.
     * <p>
     *
     * This specification strengthens the one given in the corresponding type-specific
     *          {@link Collection}.
     *
     * @return a bidirectional iterator on the element in this set.
     */
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

    /**
     * Returns a view of the portion of this sorted set whose elements range from {@code fromElement},
     * inclusive, to {@code toElement}, exclusive.
     * <p>
     * This specification strengthens the one given in {@link ConcurrentSkipListSet#subSet(Object,Object)}.
     * @see ConcurrentSkipListSet#subSet(Object,Object)
     */
    @Override
    public LongSortedSet subSet(long fromElement, long toElement) {
        long from = Math.min(fromElement, toElement);
        long to = Math.max(fromElement, toElement);
        return new ConcurrentLongSortedSet(backing.subSet(from, true, to, false));
    }

    /**
     * Returns a view of the portion of this sorted set whose elements are strictly less than
     * {@code toElement}.
     * <p>
     * This specification strengthens the one given in {@link ConcurrentSkipListSet#headSet(Object)}.
     * @see ConcurrentSkipListSet#headSet(Object)
     */
    @Override
    public LongSortedSet headSet(long toElement) {
        return new ConcurrentLongSortedSet(backing.headSet(toElement, false));
    }

    /**
     * Returns a view of the portion of this sorted set whose elements are greater than or equal to
     * {@code fromElement}.
     * <p>
     * This specification strengthens the one given in {@link ConcurrentSkipListSet#headSet(Object)}.
     * @see ConcurrentSkipListSet#tailSet(Object)
     */
    @Override
    public LongSortedSet tailSet(long fromElement) {
        return new ConcurrentLongSortedSet(backing.tailSet(fromElement, true));
    }

    /**
     * {@inheritDoc}
     *
     * This specification strengthens the one given in {@link ConcurrentSkipListSet#comparator()}.
     */
    @Override
    public LongComparator comparator() {
        return null;
    }

    /**
     * Returns the first (lowest) element currently in this set.
     *
     * @see ConcurrentSkipListSet#first()
     */
    @Override
    public long firstLong() {
        return backing.first();
    }

    /**
     * Returns the last (highest) element currently in this set.
     *
     * @see ConcurrentSkipListSet#last()
     */
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