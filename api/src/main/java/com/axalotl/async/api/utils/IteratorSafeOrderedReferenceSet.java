package com.axalotl.async.api.utils;

import it.unimi.dsi.fastutil.objects.Reference2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import lombok.Getter;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A high-performance, insertion-ordered set backed by a primitive reference-to-index map
 * and a dense array storage.
 *
 * <p>This structure provides:
 * <ul>
 *     <li>Fast O(1) add/contains/remove operations (amortized)</li>
 *     <li>Stable insertion order iteration</li>
 *     <li>Safe iteration with optional concurrent modifications tracking</li>
 *     <li>Automatic defragmentation to reduce logical gaps</li>
 * </ul>
 *
 * <p>Internally:
 * <ul>
 *     <li>{@link Reference2IntLinkedOpenHashMap} maps elements to array indices</li>
 *     <li>{@code listElements[]} stores elements in insertion order</li>
 *     <li>Removed elements are nulled, creating fragmentation</li>
 * </ul>
 *
 * <p>Defragmentation is triggered when fragmentation exceeds {@code maxFragFactor}.
 *
 * @param <E> element type (reference-based, identity-sensitive depending on map behavior)
 */
public final class IteratorSafeOrderedReferenceSet<E> {

    /**
     * Iterator flag: the iterator also returns elements appended after it was created.
     * <p>
     * Without this flag an iterator stops at the size captured at creation time.
     *
     * @see #iterator(int)
     */
    public static final int ITERATOR_FLAG_SEE_ADDITIONS = 1;

    private final Reference2IntLinkedOpenHashMap<E> indexMap;

    private final AtomicInteger firstInvalidIndex = new AtomicInteger(-1);

    private E[] listElements;

    /**
     * Current number of slots ever used in {@code listElements}.
     * Not necessarily equal to {@link #size()} due to removals.
     */
    @Getter
    private int listSize;

    /**
     * Maximum allowed fragmentation ratio before triggering defragmentation.
     */
    private final double maxFragFactor;

    /**
     * Number of active iterators that require safe iteration tracking.
     */
    private int iteratorCount;

    /**
     * If true, iteration safety features (defragmentation during iteration) are disabled.
     */
    private final boolean threadRestricted;

    /**
     * Creates a default set with:
     * <ul>
     *     <li>Initial capacity: 16</li>
     *     <li>Load factor: 0.75</li>
     *     <li>Array capacity: 16</li>
     *     <li>Max fragmentation factor: 0.2</li>
     *     <li>Component type: Object</li>
     *     <li>Thread restriction: false</li>
     * </ul>
     */
    public IteratorSafeOrderedReferenceSet() {
        this(16, 0.75f, 16, 0.2, Object.class, false);
    }

    /**
     * Full constructor.
     *
     * @param setCapacity initial capacity for backing map
     * @param setLoadFactor load factor for backing map
     * @param arrayCapacity initial size of element array
     * @param maxFragFactor threshold for triggering defragmentation (0.0–1.0)
     * @param arrComponent component type of internal array
     * @param threadRestricted if true, disables safe iteration optimizations
     */
    @SuppressWarnings("unchecked")
    public IteratorSafeOrderedReferenceSet(final int setCapacity, final float setLoadFactor, final int arrayCapacity,
                                           final double maxFragFactor, final Class<? super E> arrComponent,
                                           final boolean threadRestricted) {
        this.indexMap = new Reference2IntLinkedOpenHashMap<>(setCapacity, setLoadFactor);
        this.indexMap.defaultReturnValue(-1);
        this.maxFragFactor = maxFragFactor;
        this.listElements = (E[]) Array.newInstance(arrComponent, arrayCapacity);
        this.threadRestricted = threadRestricted;
    }

    private boolean allowSafeIteration() {
        return !this.threadRestricted;
    }

    private double getFragFactor() {
        return 1.0 - ((double) this.indexMap.size() / (double) this.listSize);
    }

    /**
     * Releases one active iterator.
     * <p>
     * Once the last iterator is released, the set is defragmented if fragmentation has
     * reached {@code maxFragFactor}. Must be paired with each iterator handed out.
     */
    public void finishRawIterator() {
        if (--this.iteratorCount == 0) {
            if (this.getFragFactor() >= this.maxFragFactor) {
                this.defrag();
            }
        }
    }

    /**
     * Removes an element from the set.
     *
     * <p>If removal creates fragmentation beyond threshold, defragmentation
     * may be triggered (depending on iterator state and configuration).
     *
     * @param element element to remove
     * @return true if element was present and removed
     *
     * @throws IllegalStateException if internal array consistency is violated
     */
    public boolean remove(final E element) {
        final int index = this.indexMap.removeInt(element);
        if (index >= 0) {

            int firstInvalid = this.firstInvalidIndex.get();
            if (firstInvalid < 0 || index < firstInvalid) {
                this.firstInvalidIndex.set(index);
            }

            if (this.listElements[index] != element) {
                throw new IllegalStateException();
            }

            this.listElements[index] = null;

            if (this.allowSafeIteration() && this.iteratorCount == 0 &&
                    this.getFragFactor() >= this.maxFragFactor) {
                this.defrag();
            }

            return true;
        }
        return false;
    }

    /**
     * Checks whether the set contains the given element.
     *
     * @param element element to check
     * @return true if present
     */
    public boolean contains(final E element) {
        return this.indexMap.containsKey(element);
    }

    /**
     * Adds an element to the set if it is not already present.
     *
     * @param element element to add
     * @return true if added, false if already present
     *
     */
    public boolean add(final E element) {
        final int listSize = this.listSize;

        final int previous = this.indexMap.putIfAbsent(element, listSize);
        if (previous != -1) {
            return false;
        }

        if (listSize >= this.listElements.length) {
            this.listElements = Arrays.copyOf(this.listElements, listSize * 2);
        }

        this.listElements[listSize] = element;
        this.listSize = listSize + 1;

        return true;
    }

    private void defrag() {
        int firstInvalid = this.firstInvalidIndex.get();
        if (firstInvalid < 0) {
            return;
        }

        if (this.indexMap.isEmpty()) {
            Arrays.fill(this.listElements, 0, this.listSize, null);
            this.listSize = 0;
            this.firstInvalidIndex.set(-1);
            return;
        }

        final E[] backingArray = this.listElements;

        int lastValidIndex;
        java.util.Iterator<Reference2IntMap.Entry<E>> iterator;

        if (firstInvalid == 0) {
            iterator = this.indexMap.reference2IntEntrySet().fastIterator();
            lastValidIndex = 0;
        } else {
            lastValidIndex = firstInvalid;
            final E key = backingArray[lastValidIndex - 1];

            iterator = this.indexMap.reference2IntEntrySet().fastIterator(new Reference2IntMap.Entry<>() {
                @Override
                public int getIntValue() {
                    throw new UnsupportedOperationException();
                }

                @Override
                public int setValue(int i) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public E getKey() {
                    return key;
                }
            });
        }

        while (iterator.hasNext()) {
            final Reference2IntMap.Entry<E> entry = iterator.next();

            final int newIndex = lastValidIndex++;
            backingArray[newIndex] = entry.getKey();
            entry.setValue(newIndex);
        }

        Arrays.fill(backingArray, lastValidIndex, this.listSize, null);
        this.listSize = lastValidIndex;
        this.firstInvalidIndex.set(-1);
    }

    /**
     * Returns the number of elements currently in the set.
     *
     * @return logical size of the set
     */
    public int size() {
        return this.indexMap.size();
    }

    /**
     * Returns an iterator over the set in insertion order.
     *
     * <p>Iteration is safe against removals and may optionally allow
     * seeing additions depending on flags.
     *
     * @return iterator instance
     */
    public Iterator<E> iterator() {
        return this.iterator(0);
    }

    /**
     * Returns an iterator with behavior flags.
     *
     * @param flags iterator behavior flags
     * @return iterator instance
     *
     * @see #ITERATOR_FLAG_SEE_ADDITIONS
     */
    public Iterator<E> iterator(final int flags) {
        if (this.allowSafeIteration()) {
            ++this.iteratorCount;
        }

        return new BaseIterator<>(
                this,
                true,
                (flags & ITERATOR_FLAG_SEE_ADDITIONS) != 0 ? Integer.MAX_VALUE : this.listSize
        );
    }

    /**
     * Extended iterator that supports explicit completion signaling.
     *
     * @param <E> element type
     */
    public interface Iterator<E> extends java.util.Iterator<E> {
        /**
         * Signals that iteration is complete.
         *
         * <p>May trigger internal cleanup or defragmentation.
         */
        void finishedIterating();
    }

    /**
     * Internal iterator implementation.
     *
     * <p>Iterates over dense array, skipping nulls.
     * Supports optional visibility of newly added elements.
     */
    private static final class BaseIterator<E> implements Iterator<E> {

        private final IteratorSafeOrderedReferenceSet<E> set;
        private final boolean canFinish;
        private final int maxIndex;

        private int nextIndex;
        private E pendingValue;
        private boolean finished;
        private E lastReturned;

        private BaseIterator(final IteratorSafeOrderedReferenceSet<E> set, final boolean canFinish, final int maxIndex) {
            this.set = set;
            this.canFinish = canFinish;
            this.maxIndex = maxIndex;
        }

        /**
         * Checks if more non-null elements exist in range.
         *
         * @return true if next element exists
         */
        @Override
        public boolean hasNext() {
            if (this.finished) {
                return false;
            }

            if (this.pendingValue != null) {
                return true;
            }

            final E[] elements = this.set.listElements;

            int index, len;
            for (index = this.nextIndex, len = Math.min(this.maxIndex, this.set.listSize);
                 index < len; ++index) {

                final E element = elements[index];
                if (element != null) {
                    this.pendingValue = element;
                    this.nextIndex = index + 1;
                    return true;
                }
            }

            this.nextIndex = index;
            return false;
        }

        /**
         * Returns next available element.
         *
         * @return next element
         * @throws NoSuchElementException if no elements remain
         */
        @Override
        public E next() {
            if (!this.hasNext()) {
                throw new NoSuchElementException();
            }

            final E ret = this.pendingValue;

            this.pendingValue = null;
            this.lastReturned = ret;

            return ret;
        }

        /**
         * Removes last returned element from the underlying set.
         *
         * @throws IllegalStateException if next() was not called or element already removed
         */
        @Override
        public void remove() {
            final E lastReturned = this.lastReturned;

            if (lastReturned == null) {
                throw new IllegalStateException();
            }

            this.lastReturned = null;
            this.set.remove(lastReturned);
        }

        /**
         * Marks iteration as complete and releases iterator tracking.
         *
         * <p>May trigger defragmentation if enabled.
         *
         * @throws IllegalStateException if already finished or not allowed
         */
        @Override
        public void finishedIterating() {
            if (this.finished || !this.canFinish) {
                throw new IllegalStateException();
            }

            this.lastReturned = null;
            this.finished = true;

            if (this.set.allowSafeIteration()) {
                this.set.finishRawIterator();
            }
        }
    }
}
