package com.axalotl.async.api.fastutil;

import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.ints.AbstractIntCollection;
import it.unimi.dsi.fastutil.ints.IntCollection;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntSpliterator;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntSpliterators;
import it.unimi.dsi.fastutil.longs.AbstractLong2IntMap;
import it.unimi.dsi.fastutil.longs.AbstractLongSet;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSpliterator;
import it.unimi.dsi.fastutil.longs.LongSpliterators;
import it.unimi.dsi.fastutil.objects.AbstractObjectSet;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.ObjectSpliterator;
import it.unimi.dsi.fastutil.objects.ObjectSpliterators;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.StampedLock;
import java.util.function.LongConsumer;

/**
 * High-performance striped concurrent Long2IntMap.
 * <p>
 * Uses striped fastutil Long2IntOpenHashMap with StampedLock optimistic reads.
 * Zero autoboxing on all primitive operations.
 * Segment count scales with available processors for write throughput on 32+ core machines.
 */
public final class Long2IntConcurrentHashMap extends AbstractLong2IntMap {

    private static final int DEFAULT_SEGMENTS = defaultSegmentCount();

    private final int segmentCount;
    private final int segmentMask;
    private final Long2IntOpenHashMap[] segments;
    private final StampedLock[] locks;
    private final LongAdder totalSize = new LongAdder();

    public Long2IntConcurrentHashMap() { this(256 * DEFAULT_SEGMENTS, DEFAULT_SEGMENTS); }

    public Long2IntConcurrentHashMap(int expectedSize) { this(expectedSize, DEFAULT_SEGMENTS); }

    public Long2IntConcurrentHashMap(int expectedSize, int concurrencyLevel) {
        this.segmentCount = nextPowerOf2(Math.max(16, concurrencyLevel));
        this.segmentMask = segmentCount - 1;
        int perSegment = Math.max(16, expectedSize / segmentCount);
        segments = new Long2IntOpenHashMap[segmentCount];
        locks = new StampedLock[segmentCount];
        for (int i = 0; i < segmentCount; i++) {
            segments[i] = new Long2IntOpenHashMap(perSegment, 0.75f);
            locks[i] = new StampedLock();
        }
    }

    static int defaultSegmentCount() {
        return nextPowerOf2(Math.max(16, Runtime.getRuntime().availableProcessors()));
    }

    private static int nextPowerOf2(int v) {
        return Integer.highestOneBit(Math.max(1, v - 1)) << 1;
    }

    private static int spread(long key) {
        key = (key ^ (key >>> 30)) * 0xbf58476d1ce4e5b9L;
        key = (key ^ (key >>> 27)) * 0x94d049bb133111ebL;
        return (int) (key ^ (key >>> 31));
    }

    private int segmentFor(long key) {
        return spread(key) & segmentMask;
    }

    @Override
    public int get(long key) {
        int seg = segmentFor(key);
        StampedLock lock = locks[seg];
        long stamp = lock.tryOptimisticRead();
        int v = segments[seg].get(key);
        if (lock.validate(stamp)) return v;
        stamp = lock.readLock();
        try { return segments[seg].get(key); }
        finally { lock.unlockRead(stamp); }
    }

    @Override
    public int getOrDefault(long key, int defaultValue) {
        int seg = segmentFor(key);
        StampedLock lock = locks[seg];
        long stamp = lock.tryOptimisticRead();
        int v = segments[seg].getOrDefault(key, defaultValue);
        if (lock.validate(stamp)) return v;
        stamp = lock.readLock();
        try { return segments[seg].getOrDefault(key, defaultValue); }
        finally { lock.unlockRead(stamp); }
    }

    @Override
    public boolean containsKey(long key) {
        int seg = segmentFor(key);
        StampedLock lock = locks[seg];
        long stamp = lock.tryOptimisticRead();
        boolean result = segments[seg].containsKey(key);
        if (lock.validate(stamp)) return result;
        stamp = lock.readLock();
        try { return segments[seg].containsKey(key); }
        finally { lock.unlockRead(stamp); }
    }

    @Override
    public boolean containsValue(int value) {
        for (int i = 0; i < segmentCount; i++) {
            long stamp = locks[i].readLock();
            try { if (segments[i].containsValue(value)) return true; }
            finally { locks[i].unlockRead(stamp); }
        }
        return false;
    }

    @Override
    public int put(long key, int value) {
        int seg = segmentFor(key);
        long stamp = locks[seg].writeLock();
        try {
            int sizeBefore = segments[seg].size();
            int prev = segments[seg].put(key, value);
            if (segments[seg].size() > sizeBefore) totalSize.increment();
            return prev;
        } finally { locks[seg].unlockWrite(stamp); }
    }

    @Override
    public int putIfAbsent(long key, int value) {
        int seg = segmentFor(key);
        long stamp = locks[seg].writeLock();
        try {
            if (segments[seg].containsKey(key)) return segments[seg].get(key);
            segments[seg].put(key, value);
            totalSize.increment();
            return defRetValue;
        } finally { locks[seg].unlockWrite(stamp); }
    }

    @Override
    public int remove(long key) {
        int seg = segmentFor(key);
        long stamp = locks[seg].writeLock();
        try {
            int sizeBefore = segments[seg].size();
            int prev = segments[seg].remove(key);
            if (segments[seg].size() < sizeBefore) totalSize.decrement();
            return prev;
        } finally { locks[seg].unlockWrite(stamp); }
    }

    @Override
    public boolean remove(long key, int value) {
        int seg = segmentFor(key);
        long stamp = locks[seg].writeLock();
        try {
            if (segments[seg].containsKey(key) && segments[seg].get(key) == value) {
                segments[seg].remove(key);
                totalSize.decrement();
                return true;
            }
            return false;
        } finally { locks[seg].unlockWrite(stamp); }
    }

    @Override
    public int replace(long key, int value) {
        int seg = segmentFor(key);
        long stamp = locks[seg].writeLock();
        try {
            if (segments[seg].containsKey(key)) return segments[seg].put(key, value);
            return defRetValue;
        } finally { locks[seg].unlockWrite(stamp); }
    }

    @Override
    public boolean replace(long key, int oldValue, int newValue) {
        int seg = segmentFor(key);
        long stamp = locks[seg].writeLock();
        try {
            if (segments[seg].containsKey(key) && segments[seg].get(key) == oldValue) {
                segments[seg].put(key, newValue);
                return true;
            }
            return false;
        } finally { locks[seg].unlockWrite(stamp); }
    }

    /**
     * Adds {@code delta} to the value currently associated with {@code key}.
     * If the key is not present, inserts it with value {@code delta}.
     * Returns the new value.
     */
    public int addTo(long key, int delta) {
        int seg = segmentFor(key);
        long stamp = locks[seg].writeLock();
        try {
            int sizeBefore = segments[seg].size();
            int cur = segments[seg].getOrDefault(key, 0);
            int newVal = cur + delta;
            segments[seg].put(key, newVal);
            if (segments[seg].size() > sizeBefore) totalSize.increment();
            return newVal;
        } finally { locks[seg].unlockWrite(stamp); }
    }

    public int computeIfAbsent(long key, java.util.function.LongToIntFunction mappingFunction) {
        int seg = segmentFor(key);
        StampedLock lock = locks[seg];
        long stamp = lock.tryOptimisticRead();
        boolean has = segments[seg].containsKey(key);
        int existing = has ? segments[seg].get(key) : defRetValue;
        if (lock.validate(stamp) && has) return existing;

        stamp = lock.writeLock();
        try {
            if (segments[seg].containsKey(key)) return segments[seg].get(key);
            int computed = mappingFunction.applyAsInt(key);
            segments[seg].put(key, computed);
            totalSize.increment();
            return computed;
        } finally { lock.unlockWrite(stamp); }
    }

    public int compute(long key, java.util.function.BiFunction<? super Long, ? super Integer, ? extends Integer> remappingFunction) {
        int seg = segmentFor(key);
        long stamp = locks[seg].writeLock();
        try {
            boolean had = segments[seg].containsKey(key);
            Integer oldVal = had ? Integer.valueOf(segments[seg].get(key)) : null;
            Integer newVal = remappingFunction.apply(key, oldVal);
            if (newVal != null) {
                int nv = newVal.intValue();
                int sizeBefore = segments[seg].size();
                segments[seg].put(key, nv);
                if (segments[seg].size() > sizeBefore) totalSize.increment();
                return nv;
            } else if (had) {
                segments[seg].remove(key);
                totalSize.decrement();
            }
            return defRetValue;
        } finally { locks[seg].unlockWrite(stamp); }
    }

    public int mergeInt(long key, int value, java.util.function.IntBinaryOperator remappingFunction) {
        int seg = segmentFor(key);
        long stamp = locks[seg].writeLock();
        try {
            int sizeBefore = segments[seg].size();
            if (segments[seg].containsKey(key)) {
                int oldVal = segments[seg].get(key);
                int newVal = remappingFunction.applyAsInt(oldVal, value);
                segments[seg].put(key, newVal);
                return newVal;
            } else {
                segments[seg].put(key, value);
                if (segments[seg].size() > sizeBefore) totalSize.increment();
                return value;
            }
        } finally { locks[seg].unlockWrite(stamp); }
    }

    @Override
    public void putAll(Map<? extends Long, ? extends Integer> m) {
        if (m instanceof Long2IntMap l2i) {
            for (Long2IntMap.Entry e : l2i.long2IntEntrySet()) {
                put(e.getLongKey(), e.getIntValue());
            }
        } else {
            for (Map.Entry<? extends Long, ? extends Integer> e : m.entrySet()) {
                put(e.getKey().longValue(), e.getValue().intValue());
            }
        }
    }

    @Override
    public int size() {
        long s = totalSize.sum();
        return (int) Math.max(0L, Math.min(s, Integer.MAX_VALUE));
    }

    @Override
    public boolean isEmpty() { return totalSize.sum() == 0; }

    @Override
    public void clear() {
        for (int i = 0; i < segmentCount; i++) {
            long stamp = locks[i].writeLock();
            try { segments[i].clear(); }
            finally { locks[i].unlockWrite(stamp); }
        }
        totalSize.reset();
    }

    @Override
    public void defaultReturnValue(int rv) {
        super.defaultReturnValue(rv);
        for (int i = 0; i < segmentCount; i++) {
            long stamp = locks[i].writeLock();
            try { segments[i].defaultReturnValue(rv); }
            finally { locks[i].unlockWrite(stamp); }
        }
    }

    @FunctionalInterface
    public interface LongIntConsumer {
        void accept(long key, int value);
    }

    public void forEach(LongIntConsumer action) {
        for (int i = 0; i < segmentCount; i++) {
            long stamp = locks[i].readLock();
            try {
                for (Long2IntMap.Entry e : segments[i].long2IntEntrySet()) {
                    action.accept(e.getLongKey(), e.getIntValue());
                }
            } finally { locks[i].unlockRead(stamp); }
        }
    }

    public void forEachKey(LongConsumer action) {
        for (int i = 0; i < segmentCount; i++) {
            long stamp = locks[i].readLock();
            try {
                for (long key : segments[i].keySet()) action.accept(key);
            } finally { locks[i].unlockRead(stamp); }
        }
    }

    public void forEachValue(java.util.function.IntConsumer action) {
        for (int i = 0; i < segmentCount; i++) {
            long stamp = locks[i].readLock();
            try {
                for (int value : segments[i].values()) action.accept(value);
            } finally { locks[i].unlockRead(stamp); }
        }
    }

    private volatile FastEntrySet entrySetView;
    private volatile LongSet keySetView;
    private volatile IntCollection valuesView;

    @Override
    public FastEntrySet long2IntEntrySet() {
        FastEntrySet es = entrySetView;
        if (es == null) {
            es = new SnapshotFastEntrySet();
            entrySetView = es;
        }
        return es;
    }

    @Override
    public LongSet keySet() {
        LongSet ks = keySetView;
        if (ks == null) {
            ks = new KeySet();
            keySetView = ks;
        }
        return ks;
    }

    @Override
    public IntCollection values() {
        IntCollection v = valuesView;
        if (v == null) {
            v = new Values();
            valuesView = v;
        }
        return v;
    }

    private List<Long2IntMap.Entry> snapshotEntries() {
        List<Long2IntMap.Entry> snapshot = new ArrayList<>(size());
        for (int i = 0; i < segmentCount; i++) {
            long stamp = locks[i].readLock();
            try {
                for (Long2IntMap.Entry e : segments[i].long2IntEntrySet()) {
                    snapshot.add(new ImmutableEntry(e.getLongKey(), e.getIntValue()));
                }
            } finally { locks[i].unlockRead(stamp); }
        }
        return snapshot;
    }

    private final class SnapshotFastEntrySet extends AbstractObjectSet<Long2IntMap.Entry>
            implements Long2IntMap.FastEntrySet {

        @Override
        public ObjectIterator<Long2IntMap.Entry> iterator() {
            List<Long2IntMap.Entry> snapshot = snapshotEntries();
            return new ObjectIterator<>() {
                private final Iterator<Long2IntMap.Entry> it = snapshot.iterator();
                private Long2IntMap.Entry last;
                @Override public boolean hasNext() { return it.hasNext(); }
                @Override public Long2IntMap.Entry next() { last = it.next(); return last; }
                @Override public void remove() {
                    if (last == null) throw new IllegalStateException();
                    Long2IntConcurrentHashMap.this.remove(last.getLongKey());
                    last = null;
                }
            };
        }

        @Override
        public ObjectIterator<Long2IntMap.Entry> fastIterator() {
            List<Long2IntMap.Entry> snapshot = snapshotEntries();
            return new ObjectIterator<>() {
                private final Iterator<Long2IntMap.Entry> it = snapshot.iterator();
                private final MutableEntry reuse = new MutableEntry();
                private boolean hasLast;
                @Override public boolean hasNext() { return it.hasNext(); }
                @Override public Long2IntMap.Entry next() {
                    Long2IntMap.Entry src = it.next();
                    reuse.key = src.getLongKey();
                    reuse.value = src.getIntValue();
                    hasLast = true;
                    return reuse;
                }
                @Override public void remove() {
                    if (!hasLast) throw new IllegalStateException();
                    Long2IntConcurrentHashMap.this.remove(reuse.key);
                    hasLast = false;
                }
            };
        }

        @Override
        public void fastForEach(java.util.function.Consumer<? super Long2IntMap.Entry> consumer) {
            MutableEntry reuse = new MutableEntry();
            for (int i = 0; i < segmentCount; i++) {
                long stamp = locks[i].readLock();
                try {
                    for (Long2IntMap.Entry e : segments[i].long2IntEntrySet()) {
                        reuse.key = e.getLongKey();
                        reuse.value = e.getIntValue();
                        consumer.accept(reuse);
                    }
                } finally { locks[i].unlockRead(stamp); }
            }
        }

        @Override
        public ObjectSpliterator<Long2IntMap.Entry> spliterator() {
            return ObjectSpliterators.asSpliterator(iterator(), size(),
                    ObjectSpliterators.SET_SPLITERATOR_CHARACTERISTICS);
        }

        @Override public boolean contains(Object o) {
            if (!(o instanceof Map.Entry<?, ?> e)) return false;
            if (!(e.getKey() instanceof Long k) || !(e.getValue() instanceof Integer v)) return false;
            int seg = segmentFor(k);
            long stamp = locks[seg].readLock();
            try {
                return segments[seg].containsKey(k.longValue()) && segments[seg].get(k.longValue()) == v;
            } finally { locks[seg].unlockRead(stamp); }
        }

        @Override public boolean remove(Object o) {
            if (!(o instanceof Map.Entry<?, ?> e)) return false;
            if (!(e.getKey() instanceof Long k) || !(e.getValue() instanceof Integer v)) return false;
            return Long2IntConcurrentHashMap.this.remove(k.longValue(), v.intValue());
        }

        @Override public int size() { return Long2IntConcurrentHashMap.this.size(); }
        @Override public void clear() { Long2IntConcurrentHashMap.this.clear(); }
    }

    private final class KeySet extends AbstractLongSet {
        @Override
        public LongIterator iterator() {
            LongOpenHashSet keys = new LongOpenHashSet(size());
            for (int i = 0; i < segmentCount; i++) {
                long stamp = locks[i].readLock();
                try { keys.addAll(segments[i].keySet()); }
                finally { locks[i].unlockRead(stamp); }
            }
            long[] snapshot = keys.toLongArray();
            return new LongIterator() {
                private int index = 0;
                private long lastReturned;
                private boolean canRemove;
                @Override public boolean hasNext() { return index < snapshot.length; }
                @Override public long nextLong() {
                    if (index >= snapshot.length) throw new NoSuchElementException();
                    lastReturned = snapshot[index++]; canRemove = true; return lastReturned;
                }
                @Override public void remove() {
                    if (!canRemove) throw new IllegalStateException();
                    Long2IntConcurrentHashMap.this.remove(lastReturned); canRemove = false;
                }
            };
        }

        @Override public LongSpliterator spliterator() {
            return LongSpliterators.asSpliterator(iterator(), size(), LongSpliterators.SET_SPLITERATOR_CHARACTERISTICS);
        }
        @Override public boolean contains(long key) { return Long2IntConcurrentHashMap.this.containsKey(key); }
        @Override public boolean remove(long key) {
            int seg = segmentFor(key);
            long stamp = locks[seg].writeLock();
            try {
                int sb = segments[seg].size(); segments[seg].remove(key);
                boolean r = segments[seg].size() < sb;
                if (r) totalSize.decrement(); return r;
            } finally { locks[seg].unlockWrite(stamp); }
        }
        @Override public int size() { return Long2IntConcurrentHashMap.this.size(); }
        @Override public void clear() { Long2IntConcurrentHashMap.this.clear(); }
    }

    private final class Values extends AbstractIntCollection {
        @Override public IntIterator iterator() {
            IntArrayList vals = new IntArrayList(size());
            for (int i = 0; i < segmentCount; i++) {
                long stamp = locks[i].readLock();
                try { vals.addAll(segments[i].values()); }
                finally { locks[i].unlockRead(stamp); }
            }
            return vals.iterator();
        }
        @Override public IntSpliterator spliterator() {
            return IntSpliterators.asSpliterator(iterator(), size(), 0);
        }
        @Override public boolean contains(int value) { return Long2IntConcurrentHashMap.this.containsValue(value); }
        @Override public int size() { return Long2IntConcurrentHashMap.this.size(); }
        @Override public void clear() { Long2IntConcurrentHashMap.this.clear(); }
    }

    private static final class MutableEntry implements Long2IntMap.Entry {
        long key; int value;
        @Override public long getLongKey() { return key; }
        @Override public int getIntValue() { return value; }
        @Override public int setValue(int value) { int old = this.value; this.value = value; return old; }
    }

    private record ImmutableEntry(long key, int value) implements Long2IntMap.Entry {
        @Override public long getLongKey() { return key; }
        @Override public int getIntValue() { return value; }
        @Override public int setValue(int value) { throw new UnsupportedOperationException(); }
        @Override public boolean equals(Object o) {
            return o instanceof Long2IntMap.Entry e && key == e.getLongKey() && value == e.getIntValue();
        }
        @Override public int hashCode() { return HashCommon.long2int(key) ^ value; }
        @Override public String toString() { return key + "=>" + value; }
    }
}
