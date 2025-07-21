package com.axalotl.async.common.parallelised.fastutil;

import it.unimi.dsi.fastutil.longs.*;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentSkipListSet;

/**
 * Thread-safe implementation of LongLinkedOpenHashSet using ConcurrentSkipListSet as backing storage.
 * This implementation provides concurrent access and maintains elements in sorted order.
 */
public class ConcurrentLongLinkedOpenHashSet extends LongLinkedOpenHashSet {

    private final ConcurrentSkipListSet<Long> backing = new ConcurrentSkipListSet<>();

    public ConcurrentLongLinkedOpenHashSet() {
    }

    @Override
    public boolean add(final long k) {
        return backing.add(k);
    }

    @Override
    public boolean addAll(Collection<? extends Long> c) {
        Objects.requireNonNull(c, "Collection cannot be null");
        return backing.addAll(c);
    }

    @Override
    public boolean contains(final long k) {
        return backing.contains(k);
    }

    @Override
    public boolean remove(final long k) {
        return backing.remove(k);
    }

    @Override
    public void clear() {
        backing.clear();
    }

    @Override
    public boolean isEmpty() {
        return backing.isEmpty();
    }

    @Override
    public int size() {
        return backing.size();
    }

    @Override
    public long firstLong() {
        return Optional.ofNullable(backing.first())
                .orElseThrow(() -> new NoSuchElementException("Set is empty"));
    }

    @Override
    public long lastLong() {
        return Optional.ofNullable(backing.last())
                .orElseThrow(() -> new NoSuchElementException("Set is empty"));
    }

    @Override
    public long removeFirstLong() {
        long first = firstLong();
        backing.remove(first);
        return first;
    }

    @Override
    public long removeLastLong() {
        long last = lastLong();
        backing.remove(last);
        return last;
    }

    @Override
    public @NotNull LongListIterator iterator() {
        return FastUtilHackUtil.wrap(backing.iterator());
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj || (obj instanceof ConcurrentLongLinkedOpenHashSet other && backing.equals(other.backing));
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
