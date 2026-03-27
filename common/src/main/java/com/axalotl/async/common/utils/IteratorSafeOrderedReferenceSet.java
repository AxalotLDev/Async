package com.axalotl.async.common.utils;

import it.unimi.dsi.fastutil.objects.Reference2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import lombok.Getter;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicInteger;

public final class IteratorSafeOrderedReferenceSet<E> {

    public static final int ITERATOR_FLAG_SEE_ADDITIONS = 1;

    private final Reference2IntLinkedOpenHashMap<E> indexMap;

    private final AtomicInteger firstInvalidIndex = new AtomicInteger(-1);

    private E[] listElements;
    @Getter
    private int listSize;

    private final double maxFragFactor;

    private int iteratorCount;

    private final boolean threadRestricted;

    public IteratorSafeOrderedReferenceSet() {
        this(16, 0.75f, 16, 0.2, Object.class, false);
    }

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

    public void finishRawIterator() {
        if (--this.iteratorCount == 0) {
            if (this.getFragFactor() >= this.maxFragFactor) {
                this.defrag();
            }
        }
    }

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

    public boolean contains(final E element) {
        return this.indexMap.containsKey(element);
    }

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

    public int size() {
        return this.indexMap.size();
    }

    public Iterator<E> iterator() {
        return this.iterator(0);
    }

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

    public interface Iterator<E> extends java.util.Iterator<E> {
        void finishedIterating();
    }

    private static final class BaseIterator<E> implements IteratorSafeOrderedReferenceSet.Iterator<E> {

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

        @Override
        public void remove() {
            final E lastReturned = this.lastReturned;

            if (lastReturned == null) {
                throw new IllegalStateException();
            }

            this.lastReturned = null;
            this.set.remove(lastReturned);
        }

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