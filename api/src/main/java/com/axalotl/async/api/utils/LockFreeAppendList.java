package com.axalotl.async.api.utils;

import org.jspecify.annotations.NonNull;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.AbstractList;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.Consumer;

public final class LockFreeAppendList<E> extends AbstractList<E> {

    private static final int SEG_BITS = 8;
    private static final int SEG_SIZE = 1 << SEG_BITS;
    private static final int SEG_MASK = SEG_SIZE - 1;
    private static final int MAX_SEGMENTS = 64;

    private static final VarHandle SLOT = MethodHandles.arrayElementVarHandle(Object[].class);

    private final AtomicReferenceArray<Object[]> segments = new AtomicReferenceArray<>(MAX_SEGMENTS);
    private final AtomicIntegerArray sizeHolder = new AtomicIntegerArray(1);

    public LockFreeAppendList() {
        segments.set(0, new Object[SEG_SIZE]);
    }

    @Override
    public boolean add(E e) {
        Objects.requireNonNull(e);
        int idx = sizeHolder.getAndIncrement(0);
        if (idx >= MAX_SEGMENTS * SEG_SIZE) {
            throw new IllegalStateException("LockFreeAppendList capacity exceeded: " + idx);
        }
        Object[] seg = segmentFor(idx);
        SLOT.setRelease(seg, idx & SEG_MASK, e);
        return true;
    }

    private Object[] segmentFor(int idx) {
        int segIdx = idx >>> SEG_BITS;
        Object[] seg = segments.get(segIdx);
        if (seg != null) return seg;
        Object[] fresh = new Object[SEG_SIZE];
        return segments.compareAndSet(segIdx, null, fresh) ? fresh : segments.get(segIdx);
    }

    @Override
    public int size() {
        return sizeHolder.get(0);
    }

    @Override
    @SuppressWarnings("unchecked")
    public E get(int index) {
        int s = sizeHolder.get(0);
        if (index < 0 || index >= s) throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + s);
        Object[] seg = segments.get(index >>> SEG_BITS);
        int slot = index & SEG_MASK;
        Object e = SLOT.getAcquire(seg, slot);
        while (e == null) {
            Thread.onSpinWait();
            e = SLOT.getAcquire(seg, slot);
        }
        return (E) e;
    }

    @Override
    public void forEach(Consumer<? super E> action) {
        Objects.requireNonNull(action);
        int s = sizeHolder.get(0);
        Object[] currentSeg = null;
        int currentSegIdx = -1;
        for (int i = 0; i < s; i++) {
            int segIdx = i >>> SEG_BITS;
            if (segIdx != currentSegIdx) {
                currentSeg = segments.get(segIdx);
                currentSegIdx = segIdx;
            }
            int slot = i & SEG_MASK;
            Object e = SLOT.getAcquire(currentSeg, slot);
            while (e == null) {
                Thread.onSpinWait();
                e = SLOT.getAcquire(currentSeg, slot);
            }
            @SuppressWarnings("unchecked") E typed = (E) e;
            action.accept(typed);
        }
    }

    @Override
    public @NonNull Iterator<E> iterator() {
        return new Iterator<>() {
            private final int max = sizeHolder.get(0);
            private int cursor = 0;

            @Override public boolean hasNext() { return cursor < max; }

            @Override public E next() {
                if (cursor >= max) throw new NoSuchElementException();
                return get(cursor++);
            }
        };
    }

    @Override
    public boolean isEmpty() {
        return sizeHolder.get(0) == 0;
    }

    @Override
    public void clear() {
        sizeHolder.set(0, 0);
        for (int i = 1; i < MAX_SEGMENTS; i++) segments.set(i, null);
        Object[] first = segments.get(0);
        if (first != null) java.util.Arrays.fill(first, null);
    }
}
