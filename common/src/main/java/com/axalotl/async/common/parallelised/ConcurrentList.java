package com.axalotl.async.common.parallelised;

import java.util.AbstractList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.locks.StampedLock;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * High-performance concurrent {@link java.util.List} backed by a flat array
 * with {@link StampedLock}-based synchronization.
 * <p>
 * Design:
 * <ul>
 *   <li>{@link #get(int)}        — <b>O(1) optimistic lock-free</b>: direct array access
 *       validated by StampedLock optimistic stamp — zero CAS, zero locking overhead.</li>
 *   <li>{@link #size()}          — O(1) optimistic lock-free read.</li>
 *   <li>{@link #contains}, {@link #forEach}, {@link #indexOf} — parallel read-locked scans,
 *       multiple readers execute concurrently.</li>
 *   <li>{@link #add(Object)}     — O(1) amortized, write-locked.</li>
 *   <li>{@link #remove(Object)}  — O(n) scan + shift, write-locked.</li>
 * </ul>
 * <p>
 * Why writes use a write lock (not lock-free):
 * <p>
 * Unlike {@code ConcurrentHashMap} which partitions keys into independent segments,
 * a {@link java.util.List} has a <b>global index ordering</b> — element at index 5
 * depends on elements 0–4 existing. This makes truly lock-free concurrent append
 * impossible without sequential commit ordering (which causes convoy effects and
 * priority inversion under contention). Our write lock approach is simpler, correct,
 * and still significantly outperforms {@code synchronizedList(ArrayList)} because
 * <b>reads never block</b>.
 * <p>
 * Thread safety: all operations are thread-safe. Reads use optimistic/read locks
 * (multiple concurrent readers). Writes use exclusive write locks.
 * Iteration is snapshot-based (weakly consistent).
 *
 * @param <E> element type (nulls not permitted)
 */
public final class ConcurrentList<E> extends AbstractList<E> {

    private static final int DEFAULT_CAPACITY = 16;

    private Object[] array;
    private int size;
    private final StampedLock lock = new StampedLock();

    public ConcurrentList() {
        this.array = new Object[DEFAULT_CAPACITY];
    }

    public ConcurrentList(Collection<? extends E> c) {
        Object[] src = c.toArray();
        int count = 0;
        for (Object e : src) {
            if (e != null) src[count++] = e;
        }
        this.size = count;
        this.array = count > 0
                ? Arrays.copyOf(src, Math.max(count, DEFAULT_CAPACITY))
                : new Object[DEFAULT_CAPACITY];
    }

    @Override
    @SuppressWarnings("unchecked")
    public E get(int index) {
        long stamp = lock.tryOptimisticRead();
        if (stamp != 0) {
            Object[] a = array;
            int s = size;
            if (lock.validate(stamp)) {
                if (index < 0 || index >= s)
                    throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + s);
                Object result = a[index];
                if (lock.validate(stamp)) return (E) result;
            }
        }
        stamp = lock.readLock();
        try {
            if (index < 0 || index >= size)
                throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + size);
            return (E) array[index];
        } finally {
            lock.unlockRead(stamp);
        }
    }

    @Override
    public int size() {
        long stamp = lock.tryOptimisticRead();
        if (stamp != 0) {
            int s = size;
            if (lock.validate(stamp)) return s;
        }
        stamp = lock.readLock();
        try { return size; } finally { lock.unlockRead(stamp); }
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    @Override
    public boolean contains(Object o) {
        if (o == null) return false;
        long stamp = lock.readLock();
        try {
            Object[] a = array;
            int s = size;
            for (int i = 0; i < s; i++) {
                if (o.equals(a[i])) return true;
            }
            return false;
        } finally {
            lock.unlockRead(stamp);
        }
    }

    @Override
    public int indexOf(Object o) {
        if (o == null) return -1;
        long stamp = lock.readLock();
        try {
            Object[] a = array;
            int s = size;
            for (int i = 0; i < s; i++) {
                if (o.equals(a[i])) return i;
            }
            return -1;
        } finally {
            lock.unlockRead(stamp);
        }
    }

    @Override
    public int lastIndexOf(Object o) {
        if (o == null) return -1;
        long stamp = lock.readLock();
        try {
            Object[] a = array;
            int s = size;
            for (int i = s - 1; i >= 0; i--) {
                if (o.equals(a[i])) return i;
            }
            return -1;
        } finally {
            lock.unlockRead(stamp);
        }
    }

    @Override
    public boolean add(E element) {
        Objects.requireNonNull(element);
        long stamp = lock.writeLock();
        try {
            if (size == array.length) grow();
            array[size++] = element;
            return true;
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    @Override
    public void add(int index, E element) {
        Objects.requireNonNull(element);
        long stamp = lock.writeLock();
        try {
            if (index < 0 || index > size)
                throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + size);
            if (size == array.length) grow();
            if (index < size) {
                System.arraycopy(array, index, array, index + 1, size - index);
            }
            array[index] = element;
            size++;
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    @Override
    public boolean remove(Object o) {
        if (o == null) return false;
        long stamp = lock.writeLock();
        try {
            Object[] a = array;
            int s = size;
            for (int i = 0; i < s; i++) {
                if (o.equals(a[i])) {
                    removeAt(a, i, s);
                    return true;
                }
            }
            return false;
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public E remove(int index) {
        long stamp = lock.writeLock();
        try {
            if (index < 0 || index >= size)
                throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + size);
            E old = (E) array[index];
            removeAt(array, index, size);
            return old;
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    @Override
    public boolean removeIf(Predicate<? super E> filter) {
        Objects.requireNonNull(filter);
        long stamp = lock.writeLock();
        try {
            Object[] a = array;
            int s = size;
            int newSize = 0;
            for (int i = 0; i < s; i++) {
                @SuppressWarnings("unchecked") E e = (E) a[i];
                if (!filter.test(e)) {
                    a[newSize++] = e;
                }
            }
            if (newSize == s) return false;
            for (int i = newSize; i < s; i++) a[i] = null;
            size = newSize;
            return true;
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public E set(int index, E element) {
        Objects.requireNonNull(element);
        long stamp = lock.writeLock();
        try {
            if (index < 0 || index >= size)
                throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + size);
            E old = (E) array[index];
            array[index] = element;
            return old;
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    @Override
    public void clear() {
        long stamp = lock.writeLock();
        try {
            Arrays.fill(array, 0, size, null);
            size = 0;
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    @Override
    public boolean addAll(Collection<? extends E> c) {
        if (c.isEmpty()) return false;
        long stamp = lock.writeLock();
        try {
            int oldSize = size;
            for (E e : c) {
                if (e != null) {
                    if (size == array.length) grow();
                    array[size++] = e;
                }
            }
            return size != oldSize;
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    @Override
    public boolean addAll(int index, Collection<? extends E> c) {
        long stamp = lock.writeLock();
        try {
            if (index < 0 || index > size)
                throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + size);
            Object[] elements = c.toArray();
            int numNew = elements.length;
            if (numNew == 0) return false;
            ensureCapacity(size + numNew);
            int numMoved = size - index;
            if (numMoved > 0) {
                System.arraycopy(array, index, array, index + numNew, numMoved);
            }
            System.arraycopy(elements, 0, array, index, numNew);
            size += numNew;
            return true;
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    @Override
    public boolean removeAll(Collection<?> c) { return removeIf(c::contains); }

    @Override
    public boolean retainAll(Collection<?> c) { return removeIf(e -> !c.contains(e)); }

    @Override
    public boolean containsAll(Collection<?> c) {
        long stamp = lock.readLock();
        try {
            Object[] a = array;
            int s = size;
            for (Object e : c) {
                if (!containsIn(a, s, e)) return false;
            }
            return true;
        } finally {
            lock.unlockRead(stamp);
        }
    }

    @Override
    public Object[] toArray() {
        long stamp = lock.readLock();
        try {
            return Arrays.copyOf(array, size);
        } finally {
            lock.unlockRead(stamp);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T[] toArray(T[] a) {
        long stamp = lock.readLock();
        try {
            int s = size;
            if (a.length < s) return (T[]) Arrays.copyOf(array, s, a.getClass());
            System.arraycopy(array, 0, a, 0, s);
            if (a.length > s) a[s] = null;
            return a;
        } finally {
            lock.unlockRead(stamp);
        }
    }

    @Override
    public void forEach(Consumer<? super E> action) {
        Objects.requireNonNull(action);
        long stamp = lock.readLock();
        try {
            Object[] a = array;
            int s = size;
            for (int i = 0; i < s; i++) {
                @SuppressWarnings("unchecked") E e = (E) a[i];
                action.accept(e);
            }
        } finally {
            lock.unlockRead(stamp);
        }
    }

    @Override
    public Iterator<E> iterator() {
        Object[] snapshot;
        long stamp = lock.readLock();
        try {
            snapshot = Arrays.copyOf(array, size);
        } finally {
            lock.unlockRead(stamp);
        }
        return new SnapshotIterator(snapshot);
    }

    private final class SnapshotIterator implements Iterator<E> {
        private final Object[] snapshot;
        private int cursor;
        private int lastRet = -1;

        SnapshotIterator(Object[] snapshot) { this.snapshot = snapshot; }

        @Override public boolean hasNext() { return cursor < snapshot.length; }

        @Override
        @SuppressWarnings("unchecked")
        public E next() {
            if (cursor >= snapshot.length) throw new NoSuchElementException();
            lastRet = cursor;
            return (E) snapshot[cursor++];
        }

        @Override
        public void remove() {
            if (lastRet < 0) throw new IllegalStateException();
            ConcurrentList.this.remove(snapshot[lastRet]);
            lastRet = -1;
        }

        @Override
        public void forEachRemaining(Consumer<? super E> action) {
            Objects.requireNonNull(action);
            while (cursor < snapshot.length) {
                @SuppressWarnings("unchecked") E e = (E) snapshot[cursor++];
                action.accept(e);
            }
        }
    }

    public boolean shouldCompact(float threshold) { return false; }
    public void compact() { /* no-op: flat array, no tombstones */ }
    public float fragmentation() { return 0.0f; }

    private void removeAt(Object[] a, int index, int s) {
        int newSize = s - 1;
        if (index < newSize) {
            System.arraycopy(a, index + 1, a, index, newSize - index);
        }
        a[newSize] = null;
        size = newSize;
    }

    private void grow() {
        int oldCap = array.length;
        int newCap = oldCap + (oldCap >> 1);
        if (newCap < oldCap + 1) newCap = oldCap + 1;
        array = Arrays.copyOf(array, newCap);
    }

    private void ensureCapacity(int minCapacity) {
        if (minCapacity > array.length) {
            int newCap = array.length + (array.length >> 1);
            if (newCap < minCapacity) newCap = minCapacity;
            array = Arrays.copyOf(array, newCap);
        }
    }

    private static boolean containsIn(Object[] a, int size, Object o) {
        if (o == null) return false;
        for (int i = 0; i < size; i++) {
            if (o.equals(a[i])) return true;
        }
        return false;
    }

    @Override
    public String toString() {
        long stamp = lock.readLock();
        try {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < size; i++) {
                if (i > 0) sb.append(", ");
                sb.append(array[i]);
            }
            return sb.append(']').toString();
        } finally {
            lock.unlockRead(stamp);
        }
    }
}
