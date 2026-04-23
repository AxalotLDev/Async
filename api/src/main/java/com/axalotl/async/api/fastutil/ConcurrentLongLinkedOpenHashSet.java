package com.axalotl.async.api.fastutil;

import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.longs.*;

import java.util.Collection;
import java.util.NoSuchElementException;
import java.util.concurrent.locks.StampedLock;

/**
 * Thread-safe drop-in replacement for LongLinkedOpenHashSet.
 * Extends LongLinkedOpenHashSet directly so it is assignment-compatible
 * with fields typed as LongLinkedOpenHashSet (e.g. Mixin @Shadow fields).
 * <p>
 * All mutating and reading operations are guarded by a StampedLock
 * with optimistic reads for single-key lookups.
 * <p>
 * Internal note: StampedLock is NOT reentrant, so public methods must
 * never call other public (locked) methods. All delegation goes through
 * {@code super.*} which bypasses our overrides.
 */
public final class ConcurrentLongLinkedOpenHashSet extends LongLinkedOpenHashSet {

    private final StampedLock lock = new StampedLock();

    public ConcurrentLongLinkedOpenHashSet() { super(); }

    public ConcurrentLongLinkedOpenHashSet(int expected) { super(expected); }

    public ConcurrentLongLinkedOpenHashSet(int expected, float f) { super(expected, f); }

    public ConcurrentLongLinkedOpenHashSet(Collection<? extends Long> c) {
        super();
        for (Long v : c) super.add(v.longValue());
    }

    public ConcurrentLongLinkedOpenHashSet(LongCollection c) {
        super();
        LongIterator it = c.iterator();
        while (it.hasNext()) super.add(it.nextLong());
    }

    private long[] snapshotUnlocked() {
        LongListIterator it = super.iterator();
        long[] arr = new long[super.size()];
        int i = 0;
        while (it.hasNext()) arr[i++] = it.nextLong();
        return i == arr.length ? arr : java.util.Arrays.copyOf(arr, i);
    }

    @Override public int size() {
        long stamp = lock.tryOptimisticRead();
        int s = super.size();
        if (!lock.validate(stamp)) {
            stamp = lock.readLock();
            try { s = super.size(); }
            finally { lock.unlockRead(stamp); }
        }
        return s;
    }

    @Override public boolean isEmpty() {
        long stamp = lock.tryOptimisticRead();
        boolean e = super.isEmpty();
        if (!lock.validate(stamp)) {
            stamp = lock.readLock();
            try { e = super.isEmpty(); }
            finally { lock.unlockRead(stamp); }
        }
        return e;
    }

    @Override public boolean contains(long k) {
        long stamp = lock.tryOptimisticRead();
        boolean c = super.contains(k);
        if (!lock.validate(stamp)) {
            stamp = lock.readLock();
            try { c = super.contains(k); }
            finally { lock.unlockRead(stamp); }
        }
        return c;
    }

    @Override public boolean add(long k) {
        long stamp = lock.writeLock();
        try { return super.add(k); }
        finally { lock.unlockWrite(stamp); }
    }

    @Override public boolean addAll(LongCollection c) {
        long stamp = lock.writeLock();
        try { return super.addAll(c); }
        finally { lock.unlockWrite(stamp); }
    }

    @Override public boolean addAll(Collection<? extends Long> c) {
        long stamp = lock.writeLock();
        try { return super.addAll(c); }
        finally { lock.unlockWrite(stamp); }
    }

    @Override public boolean remove(long k) {
        long stamp = lock.writeLock();
        try { return super.remove(k); }
        finally { lock.unlockWrite(stamp); }
    }

    @Override public boolean removeAll(LongCollection c) {
        long stamp = lock.writeLock();
        try { return super.removeAll(c); }
        finally { lock.unlockWrite(stamp); }
    }

    @Override public boolean removeAll(Collection<?> c) {
        long stamp = lock.writeLock();
        try { return super.removeAll(c); }
        finally { lock.unlockWrite(stamp); }
    }

    @Override public boolean retainAll(LongCollection c) {
        long stamp = lock.writeLock();
        try { return super.retainAll(c); }
        finally { lock.unlockWrite(stamp); }
    }

    @Override public boolean retainAll(Collection<?> c) {
        long stamp = lock.writeLock();
        try { return super.retainAll(c); }
        finally { lock.unlockWrite(stamp); }
    }

    @Override public void clear() {
        long stamp = lock.writeLock();
        try { super.clear(); }
        finally { lock.unlockWrite(stamp); }
    }

    @Override public long firstLong() {
        long stamp = lock.readLock();
        try { return super.firstLong(); }
        finally { lock.unlockRead(stamp); }
    }

    @Override public long lastLong() {
        long stamp = lock.readLock();
        try { return super.lastLong(); }
        finally { lock.unlockRead(stamp); }
    }

    public long removeFirstLong() {
        long stamp = lock.writeLock();
        try {
            if (super.isEmpty()) throw new NoSuchElementException("Set is empty");
            long first = super.firstLong();
            super.remove(first);
            return first;
        } finally { lock.unlockWrite(stamp); }
    }

    public long removeLastLong() {
        long stamp = lock.writeLock();
        try {
            if (super.isEmpty()) throw new NoSuchElementException("Set is empty");
            long last = super.lastLong();
            super.remove(last);
            return last;
        } finally { lock.unlockWrite(stamp); }
    }

    @Override public long[] toLongArray() {
        long stamp = lock.readLock();
        try { return snapshotUnlocked(); }
        finally { lock.unlockRead(stamp); }
    }

    @Override public long[] toArray(long[] a) {
        long stamp = lock.readLock();
        try {
            long[] snap = snapshotUnlocked();
            if (a.length < snap.length) return snap;
            System.arraycopy(snap, 0, a, 0, snap.length);
            if (a.length > snap.length) a[snap.length] = 0L;
            return a;
        } finally { lock.unlockRead(stamp); }
    }

    @Override public Object[] toArray() {
        long stamp = lock.readLock();
        try {
            long[] snap = snapshotUnlocked();
            Object[] out = new Object[snap.length];
            for (int i = 0; i < snap.length; i++) out[i] = snap[i];
            return out;
        } finally { lock.unlockRead(stamp); }
    }

    @Override public <T> T[] toArray(T[] a) {
        long stamp = lock.readLock();
        try {
            long[] snap = snapshotUnlocked();
            @SuppressWarnings("unchecked")
            T[] out = a.length >= snap.length ? a :
                    (T[]) java.lang.reflect.Array.newInstance(a.getClass().getComponentType(), snap.length);
            for (int i = 0; i < snap.length; i++) {
                @SuppressWarnings("unchecked") T val = (T) Long.valueOf(snap[i]);
                out[i] = val;
            }
            if (out.length > snap.length) out[snap.length] = null;
            return out;
        } finally { lock.unlockRead(stamp); }
    }

    @Override public LongListIterator iterator() {
        long stamp = lock.readLock();
        try {
            long[] snapshot = snapshotUnlocked();
            return snapshotIterator(snapshot, 0, snapshot.length);
        } finally { lock.unlockRead(stamp); }
    }

    @Override public LongListIterator iterator(long from) {
        long stamp = lock.readLock();
        try {
            long[] snapshot = snapshotUnlocked();
            int start = 0;
            for (int j = 0; j < snapshot.length; j++) {
                if (snapshot[j] == from) { start = j; break; }
            }
            return snapshotIterator(snapshot, start, snapshot.length - start);
        } finally { lock.unlockRead(stamp); }
    }

    private LongListIterator snapshotIterator(long[] array, int offset, int length) {
        return new LongListIterator() {
            private int pos = offset;
            private final int end = offset + length;
            private int lastReturned = -1;

            @Override public boolean hasNext() { return pos < end; }
            @Override public boolean hasPrevious() { return pos > offset; }

            @Override public long nextLong() {
                if (!hasNext()) throw new NoSuchElementException();
                lastReturned = pos;
                return array[pos++];
            }
            @Override public long previousLong() {
                if (!hasPrevious()) throw new NoSuchElementException();
                lastReturned = --pos;
                return array[pos];
            }
            @Override public int nextIndex() { return pos - offset; }
            @Override public int previousIndex() { return pos - offset - 1; }

            @Override public void remove() {
                if (lastReturned < 0) throw new IllegalStateException();
                ConcurrentLongLinkedOpenHashSet.this.remove(array[lastReturned]);
                lastReturned = -1;
            }
        };
    }

    @Override public int hashCode() {
        long stamp = lock.readLock();
        try {
            int h = 0;
            LongListIterator it = super.iterator();
            while (it.hasNext()) h += HashCommon.long2int(it.nextLong());
            return h;
        } finally { lock.unlockRead(stamp); }
    }

    @Override public boolean equals(Object o) {
        long stamp = lock.readLock();
        try { return super.equals(o); }
        finally { lock.unlockRead(stamp); }
    }

    @Override public String toString() {
        long stamp = lock.readLock();
        try {
            long[] snap = snapshotUnlocked();
            StringBuilder sb = new StringBuilder("{");
            for (int i = 0; i < snap.length; i++) {
                if (i > 0) sb.append(", ");
                sb.append(snap[i]);
            }
            return sb.append("}").toString();
        } finally { lock.unlockRead(stamp); }
    }

    @Override public ConcurrentLongLinkedOpenHashSet clone() {
        long stamp = lock.readLock();
        try {
            long[] snap = snapshotUnlocked();
            ConcurrentLongLinkedOpenHashSet copy = new ConcurrentLongLinkedOpenHashSet(snap.length);
            for (long v : snap) copy.add(v);
            return copy;
        } finally { lock.unlockRead(stamp); }
    }
}
