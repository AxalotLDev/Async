package com.axalotl.async.parallelised.fastutil;

import java.io.Serial;
import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.ConcurrentSkipListSet;

import it.unimi.dsi.fastutil.longs.LongArrays;
import it.unimi.dsi.fastutil.longs.LongCollection;
import it.unimi.dsi.fastutil.longs.LongComparator;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongIterators;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongListIterator;
import it.unimi.dsi.fastutil.longs.LongSortedSet;

public class ConcurrentLongLinkedOpenHashSet extends LongLinkedOpenHashSet {

    @Serial
    private static final long serialVersionUID = -5532128240738069111L;

    private final ConcurrentSkipListSet<Long> backing;

    public ConcurrentLongLinkedOpenHashSet() {
        backing = new ConcurrentSkipListSet<>();
    }

    public ConcurrentLongLinkedOpenHashSet(final int initial) {
        backing = new ConcurrentSkipListSet<>();
    }

    public ConcurrentLongLinkedOpenHashSet(final int initial, final float dnc) {
        this(initial);
    }

    public ConcurrentLongLinkedOpenHashSet(final LongIterator i, final float f) {
        this(16, f);
        while (i.hasNext())
            add(i.nextLong());
    }

    public ConcurrentLongLinkedOpenHashSet(final LongIterator i) {
        this(i, -1);
    }

    public ConcurrentLongLinkedOpenHashSet(final Iterator<?> i) {
        this(LongIterators.asLongIterator(i));
    }

    public ConcurrentLongLinkedOpenHashSet(final long[] a, final int offset, final int length, final float f) {
        this(Math.max(length, 0), f);
        LongArrays.ensureOffsetLength(a, offset, length);
        for (int i = 0; i < length; i++)
            add(a[offset + i]);
    }

    public ConcurrentLongLinkedOpenHashSet(final long[] a, final float f) {
        this(a, 0, a.length, f);
    }

    public ConcurrentLongLinkedOpenHashSet(final long[] a) {
        this(a, -1);
    }

    @Override
    public boolean add(final long k) {
        return backing.add(k);
    }

    @Override
    public boolean addAll(LongCollection c) {
        return addAll((Collection<Long>) c);
    }

    @Override
    public boolean addAll(Collection<? extends Long> c) {
        return backing.addAll(c);
    }

    @Override
    public boolean addAndMoveToFirst(final long k) {
        return backing.add(k);
    }

    @Override
    public boolean addAndMoveToLast(final long k) {
        return backing.add(k);
    }

    @Override
    public void clear() {
        backing.clear();
    }

    @Override
    public LongLinkedOpenHashSet clone() {
        return new ConcurrentLongLinkedOpenHashSet(backing.iterator());
    }

    @Override
    public LongComparator comparator() {
        return null;
    }

    @Override
    public boolean contains(final long k) {
        return backing.contains(k);
    }

    @Override
    public long firstLong() {
        return backing.first();
    }

    @Override
    public int hashCode() {
        return backing.hashCode();
    }

    @Override
    public LongSortedSet headSet(long to) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isEmpty() {
        return backing.isEmpty();
    }

    @Override
    public LongListIterator iterator() {
        return FastUtilHackUtil.wrap(backing.iterator());
    }

    @Override
    public LongListIterator iterator(long from) {
        throw new IllegalStateException();
    }

    @Override
    public long lastLong() {
        return backing.last();
    }

    @Override
    public boolean remove(final long k) {
        return backing.remove(k);
    }

    @Override
    public long removeFirstLong() {
        long fl = this.firstLong();
        this.remove(fl);
        return fl;
    }

    @Override
    public long removeLastLong() {
        long fl = this.lastLong();
        this.remove(fl);
        return fl;
    }

    @Override
    public int size() {
        return backing.size();
    }

    @Override
    public LongSortedSet subSet(long from, long to) {
        throw new UnsupportedOperationException();
    }

    @Override
    public LongSortedSet tailSet(long from) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean trim() {
        return true;
    }

    @Override
    public boolean trim(final int n) {
        return true;
    }
}
