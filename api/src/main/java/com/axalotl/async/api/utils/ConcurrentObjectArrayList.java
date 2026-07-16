package com.axalotl.async.api.utils;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectListIterator;
import org.jspecify.annotations.NonNull;

import java.util.Collection;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * A thread-safe {@link ObjectArrayList}.
 *
 * <p>All mutating and reading operations are synchronized on the list itself. Iteration helpers
 * ({@link #iterator()}, {@link #forEach(Consumer)}, {@link #stream()}) operate on a snapshot taken
 * under the lock, so concurrent readers never observe a partially-mutated list or throw
 * {@link java.util.ConcurrentModificationException}.</p>
 *
 * @param <T> the element type
 */
public class ConcurrentObjectArrayList<T> extends ObjectArrayList<T> {

    /**
     * Creates an empty list.
     */
    public ConcurrentObjectArrayList() {
        super();
    }

    @Override
    public synchronized boolean add(T element) {
        return super.add(element);
    }

    @Override
    public synchronized void add(int index, T element) {
        super.add(index, element);
    }

    @Override
    public synchronized boolean addAll(Collection<? extends T> c) {
        return super.addAll(c);
    }

    @Override
    public synchronized boolean remove(Object element) {
        return super.remove(element);
    }

    @Override
    public synchronized T remove(int index) {
        return super.remove(index);
    }

    @Override
    public synchronized boolean removeAll(Collection<?> c) {
        return super.removeAll(c);
    }

    @Override
    public synchronized void clear() {
        super.clear();
    }

    @Override
    public synchronized T get(int index) {
        return super.get(index);
    }

    @Override
    public synchronized T set(int index, T element) {
        return super.set(index, element);
    }

    @Override
    public synchronized int size() {
        return super.size();
    }

    @Override
    public synchronized boolean contains(Object element) {
        return super.contains(element);
    }

    @SuppressWarnings("unchecked")
    private synchronized T[] snapshot() {
        return (T[]) toArray();
    }

    @Override
    public @NonNull ObjectListIterator<T> iterator() {
        return ObjectArrayList.wrap(snapshot()).iterator();
    }

    @Override
    public void forEach(Consumer<? super T> action) {
        for (T element : snapshot()) {
            action.accept(element);
        }
    }

    @Override
    public @NonNull Stream<T> stream() {
        return Stream.of(snapshot());
    }
}