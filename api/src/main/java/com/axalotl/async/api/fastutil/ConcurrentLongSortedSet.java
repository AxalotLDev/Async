package com.axalotl.async.api.fastutil;

import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.longs.*;

import java.util.*;
import java.util.concurrent.ConcurrentSkipListSet;

/**
 * Thread-safe LongSortedSet backed by ConcurrentSkipListSet.
 * Provides concurrent access and maintains elements in sorted order.
 */
public final class ConcurrentLongSortedSet implements LongSortedSet {

    private final ConcurrentSkipListSet<Long> backing;

    public ConcurrentLongSortedSet() { this.backing = new ConcurrentSkipListSet<>(); }

    public ConcurrentLongSortedSet(Collection<Long> collection) {
        this();
        addAll(Objects.requireNonNull(collection));
    }

    @Override public LongBidirectionalIterator iterator(long fromElement) {
        return wrap(backing.tailSet(fromElement).iterator());
    }

    @Override public LongBidirectionalIterator iterator() { return wrap(backing.iterator()); }
    @Override public int size() { return backing.size(); }
    @Override public boolean isEmpty() { return backing.isEmpty(); }
    @Override public Object[] toArray() { return backing.toArray(); }
    @Override public <T> T[] toArray(T[] a) { return backing.toArray(a); }
    @Override public boolean containsAll(Collection<?> c) { return backing.containsAll(c); }
    @Override public boolean addAll(Collection<? extends Long> c) { return backing.addAll(c); }
    @Override public boolean removeAll(Collection<?> c) { return backing.removeAll(c); }
    @Override public boolean retainAll(Collection<?> c) { return backing.retainAll(c); }
    @Override public void clear() { backing.clear(); }
    @Override public boolean add(long key) { return backing.add(key); }
    @Override public boolean contains(long key) { return backing.contains(key); }
    @Override public boolean remove(long k) { return backing.remove(k); }

    @Override public long[] toLongArray() { return backing.stream().mapToLong(Long::longValue).toArray(); }

    @Override public long[] toArray(long[] a) {
        long[] result = toLongArray();
        if (a.length < result.length) return result;
        System.arraycopy(result, 0, a, 0, result.length);
        if (a.length > result.length) a[result.length] = 0L;
        return a;
    }

    @Override public boolean addAll(LongCollection c) {
        boolean mod = false;
        for (LongIterator it = c.iterator(); it.hasNext(); ) if (backing.add(it.nextLong())) mod = true;
        return mod;
    }
    @Override public boolean containsAll(LongCollection c) {
        for (LongIterator it = c.iterator(); it.hasNext(); ) if (!backing.contains(it.nextLong())) return false;
        return true;
    }
    @Override public boolean removeAll(LongCollection c) {
        boolean mod = false;
        for (LongIterator it = c.iterator(); it.hasNext(); ) if (backing.remove(it.nextLong())) mod = true;
        return mod;
    }
    @Override public boolean retainAll(LongCollection c) { return backing.retainAll(c); }

    @Override public LongSortedSet subSet(long from, long to) {
        return new ConcurrentLongSortedSet(backing.subSet(Math.min(from, to), Math.max(from, to)));
    }
    @Override public LongSortedSet headSet(long to) { return new ConcurrentLongSortedSet(backing.headSet(to)); }
    @Override public LongSortedSet tailSet(long from) { return new ConcurrentLongSortedSet(backing.tailSet(from)); }
    @Override public LongComparator comparator() { return null; }
    @Override public long firstLong() { return backing.first(); }
    @Override public long lastLong() { return backing.last(); }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LongSet that)) return false;
        if (size() != that.size()) return false;
        for (LongIterator it = that.iterator(); it.hasNext(); ) if (!contains(it.nextLong())) return false;
        return true;
    }

    @Override public int hashCode() {
        int h = 0;
        for (long v : backing) h += HashCommon.long2int(v);
        return h;
    }

    @Override public String toString() { return backing.toString(); }

    private static LongBidirectionalIterator wrap(Iterator<Long> it) {
        return new LongBidirectionalIterator() {
            @Override public long previousLong() { throw new UnsupportedOperationException(); }
            @Override public boolean hasPrevious() { throw new UnsupportedOperationException(); }
            @Override public long nextLong() { return it.next(); }
            @Override public boolean hasNext() { return it.hasNext(); }
            @Override public void remove() { it.remove(); }
        };
    }
}
