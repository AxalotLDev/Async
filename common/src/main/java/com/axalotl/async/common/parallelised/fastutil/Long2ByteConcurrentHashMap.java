package com.axalotl.async.common.parallelised.fastutil;

import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.bytes.AbstractByteCollection;
import it.unimi.dsi.fastutil.bytes.ByteCollection;
import it.unimi.dsi.fastutil.bytes.ByteIterator;
import it.unimi.dsi.fastutil.bytes.ByteSpliterator;
import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import it.unimi.dsi.fastutil.bytes.ByteSpliterators;
import it.unimi.dsi.fastutil.longs.*;
import it.unimi.dsi.fastutil.objects.AbstractObjectSet;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import it.unimi.dsi.fastutil.objects.ObjectSpliterator;
import it.unimi.dsi.fastutil.objects.ObjectSpliterators;

import java.util.*;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.StampedLock;

/**
 * High-performance striped concurrent Long2ByteMap.
 * Zero autoboxing — uses striped Long2ByteOpenHashMap with StampedLock optimistic reads.
 * Drop-in replacement for Long2ByteOpenHashMap in DistanceManager chunk trackers.
 * Segment count scales with available processors for write throughput on 32+ core machines.
 */
public final class Long2ByteConcurrentHashMap extends AbstractLong2ByteMap {

    private static final int DEFAULT_SEGMENTS = defaultSegmentCount();

    private final int segmentCount;
    private final int segmentMask;
    private final Long2ByteOpenHashMap[] segments;
    private final StampedLock[] locks;
    private final LongAdder totalSize = new LongAdder();

    public Long2ByteConcurrentHashMap() { this(256 * DEFAULT_SEGMENTS, DEFAULT_SEGMENTS); }

    public Long2ByteConcurrentHashMap(int expectedSize) { this(expectedSize, DEFAULT_SEGMENTS); }

    public Long2ByteConcurrentHashMap(int expectedSize, int concurrencyLevel) {
        this.segmentCount = nextPowerOf2(Math.max(16, concurrencyLevel));
        this.segmentMask = segmentCount - 1;
        int perSegment = Math.max(16, expectedSize / segmentCount);
        segments = new Long2ByteOpenHashMap[segmentCount];
        locks = new StampedLock[segmentCount];
        for (int i = 0; i < segmentCount; i++) {
            segments[i] = new Long2ByteOpenHashMap(perSegment, 0.75f);
            locks[i] = new StampedLock();
        }
    }

    static int defaultSegmentCount() {
        return nextPowerOf2(Math.max(32, Runtime.getRuntime().availableProcessors() * 4));
    }

    private static int nextPowerOf2(int v) {
        return Integer.highestOneBit(Math.max(1, v - 1)) << 1;
    }

    private static int spread(long key) {
        key = (key ^ (key >>> 30)) * 0xbf58476d1ce4e5b9L;
        key = (key ^ (key >>> 27)) * 0x94d049bb133111ebL;
        return (int) (key ^ (key >>> 31));
    }

    private int segmentFor(long key) { return spread(key) & segmentMask; }

    @Override public byte get(long key) {
        int seg = segmentFor(key);
        StampedLock lock = locks[seg];
        long stamp = lock.tryOptimisticRead();
        byte v = segments[seg].get(key);
        if (lock.validate(stamp)) return v;
        stamp = lock.readLock();
        try { return segments[seg].get(key); }
        finally { lock.unlockRead(stamp); }
    }

    @Override public byte getOrDefault(long key, byte defaultValue) {
        int seg = segmentFor(key);
        StampedLock lock = locks[seg];
        long stamp = lock.tryOptimisticRead();
        byte v = segments[seg].getOrDefault(key, defaultValue);
        if (lock.validate(stamp)) return v;
        stamp = lock.readLock();
        try { return segments[seg].getOrDefault(key, defaultValue); }
        finally { lock.unlockRead(stamp); }
    }

    @Override public boolean containsKey(long key) {
        int seg = segmentFor(key);
        StampedLock lock = locks[seg];
        long stamp = lock.tryOptimisticRead();
        boolean r = segments[seg].containsKey(key);
        if (lock.validate(stamp)) return r;
        stamp = lock.readLock();
        try { return segments[seg].containsKey(key); }
        finally { lock.unlockRead(stamp); }
    }

    @Override public boolean containsValue(byte value) {
        for (int i = 0; i < segmentCount; i++) {
            long stamp = locks[i].readLock();
            try { if (segments[i].containsValue(value)) return true; }
            finally { locks[i].unlockRead(stamp); }
        }
        return false;
    }

    @Override public byte put(long key, byte value) {
        int seg = segmentFor(key);
        long stamp = locks[seg].writeLock();
        try {
            int sb = segments[seg].size();
            byte prev = segments[seg].put(key, value);
            if (segments[seg].size() > sb) totalSize.increment();
            return prev;
        } finally { locks[seg].unlockWrite(stamp); }
    }

    @Override public byte putIfAbsent(long key, byte value) {
        int seg = segmentFor(key);
        long stamp = locks[seg].writeLock();
        try {
            if (segments[seg].containsKey(key)) return segments[seg].get(key);
            segments[seg].put(key, value);
            totalSize.increment();
            return defRetValue;
        } finally { locks[seg].unlockWrite(stamp); }
    }

    @Override public byte remove(long key) {
        int seg = segmentFor(key);
        long stamp = locks[seg].writeLock();
        try {
            int sb = segments[seg].size();
            byte prev = segments[seg].remove(key);
            if (segments[seg].size() < sb) totalSize.decrement();
            return prev;
        } finally { locks[seg].unlockWrite(stamp); }
    }

    @Override public boolean remove(long key, byte value) {
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

    @Override public byte replace(long key, byte value) {
        int seg = segmentFor(key);
        long stamp = locks[seg].writeLock();
        try {
            if (segments[seg].containsKey(key)) return segments[seg].put(key, value);
            return defRetValue;
        } finally { locks[seg].unlockWrite(stamp); }
    }

    @Override public boolean replace(long key, byte oldValue, byte newValue) {
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
    public byte addTo(long key, byte delta) {
        int seg = segmentFor(key);
        long stamp = locks[seg].writeLock();
        try {
            int sb = segments[seg].size();
            byte cur = segments[seg].getOrDefault(key, (byte) 0);
            byte newVal = (byte) (cur + delta);
            segments[seg].put(key, newVal);
            if (segments[seg].size() > sb) totalSize.increment();
            return newVal;
        } finally { locks[seg].unlockWrite(stamp); }
    }

    public byte computeIfAbsent(long key, java.util.function.LongFunction<Byte> mappingFunction) {
        int seg = segmentFor(key);
        StampedLock lock = locks[seg];
        long stamp = lock.tryOptimisticRead();
        boolean has = segments[seg].containsKey(key);
        byte existing = has ? segments[seg].get(key) : defRetValue;
        if (lock.validate(stamp) && has) return existing;

        stamp = lock.writeLock();
        try {
            if (segments[seg].containsKey(key)) return segments[seg].get(key);
            Byte computed = mappingFunction.apply(key);
            if (computed != null) {
                byte bv = computed.byteValue();
                segments[seg].put(key, bv);
                totalSize.increment();
                return bv;
            }
            return defRetValue;
        } finally { lock.unlockWrite(stamp); }
    }

    @Override public void putAll(Map<? extends Long, ? extends Byte> m) {
        if (m instanceof Long2ByteMap l2b) {
            for (Long2ByteMap.Entry e : l2b.long2ByteEntrySet()) put(e.getLongKey(), e.getByteValue());
        } else {
            for (Map.Entry<? extends Long, ? extends Byte> e : m.entrySet()) put(e.getKey().longValue(), e.getValue().byteValue());
        }
    }

    @Override public int size() {
        long s = totalSize.sum();
        return (int) Math.max(0L, Math.min(s, Integer.MAX_VALUE));
    }
    @Override public boolean isEmpty() { return totalSize.sum() == 0; }

    @Override public void clear() {
        for (int i = 0; i < segmentCount; i++) {
            long stamp = locks[i].writeLock();
            try { segments[i].clear(); }
            finally { locks[i].unlockWrite(stamp); }
        }
        totalSize.reset();
    }

    @Override public void defaultReturnValue(byte rv) {
        super.defaultReturnValue(rv);
        for (int i = 0; i < segmentCount; i++) {
            long stamp = locks[i].writeLock();
            try { segments[i].defaultReturnValue(rv); }
            finally { locks[i].unlockWrite(stamp); }
        }
    }

    private volatile EntrySet entrySetView;

    @Override public ObjectSet<Long2ByteMap.Entry> long2ByteEntrySet() {
        EntrySet es = entrySetView;
        if (es == null) {
            es = new EntrySet();
            entrySetView = es;
        }
        return es;
    }

    @Override public LongSet keySet() {
        LongOpenHashSet keys = new LongOpenHashSet(size());
        for (int i = 0; i < segmentCount; i++) {
            long stamp = locks[i].readLock();
            try { keys.addAll(segments[i].keySet()); }
            finally { locks[i].unlockRead(stamp); }
        }
        return keys;
    }

    @Override public ByteCollection values() {
        return new Values();
    }

    private List<Long2ByteMap.Entry> snapshotEntries() {
        List<Long2ByteMap.Entry> snap = new ArrayList<>(size());
        for (int i = 0; i < segmentCount; i++) {
            long stamp = locks[i].readLock();
            try {
                for (Long2ByteMap.Entry e : segments[i].long2ByteEntrySet())
                    snap.add(new ImmutableEntry(e.getLongKey(), e.getByteValue()));
            } finally { locks[i].unlockRead(stamp); }
        }
        return snap;
    }

    private final class EntrySet extends AbstractObjectSet<Long2ByteMap.Entry>
            implements Long2ByteMap.FastEntrySet {

        @Override public ObjectIterator<Long2ByteMap.Entry> iterator() {
            List<Long2ByteMap.Entry> snap = snapshotEntries();
            return new ObjectIterator<>() {
                private final Iterator<Long2ByteMap.Entry> it = snap.iterator();
                @Override public boolean hasNext() { return it.hasNext(); }
                @Override public Long2ByteMap.Entry next() { return it.next(); }
            };
        }

        @Override public ObjectIterator<Long2ByteMap.Entry> fastIterator() {
            List<Long2ByteMap.Entry> snap = snapshotEntries();
            return new ObjectIterator<>() {
                private final Iterator<Long2ByteMap.Entry> it = snap.iterator();
                private final MutableEntry reuse = new MutableEntry();
                @Override public boolean hasNext() { return it.hasNext(); }
                @Override public Long2ByteMap.Entry next() {
                    Long2ByteMap.Entry src = it.next();
                    reuse.key = src.getLongKey(); reuse.value = src.getByteValue();
                    return reuse;
                }
            };
        }

        @Override public void fastForEach(java.util.function.Consumer<? super Long2ByteMap.Entry> consumer) {
            MutableEntry reuse = new MutableEntry();
            for (int i = 0; i < segmentCount; i++) {
                long stamp = locks[i].readLock();
                try {
                    for (Long2ByteMap.Entry e : segments[i].long2ByteEntrySet()) {
                        reuse.key = e.getLongKey(); reuse.value = e.getByteValue();
                        consumer.accept(reuse);
                    }
                } finally { locks[i].unlockRead(stamp); }
            }
        }

        @Override public ObjectSpliterator<Long2ByteMap.Entry> spliterator() {
            return ObjectSpliterators.asSpliterator(iterator(), size(), ObjectSpliterators.SET_SPLITERATOR_CHARACTERISTICS);
        }

        @Override public boolean contains(Object o) {
            if (!(o instanceof Map.Entry<?, ?> e)) return false;
            if (!(e.getKey() instanceof Long k) || !(e.getValue() instanceof Byte v)) return false;
            int seg = segmentFor(k);
            long stamp = locks[seg].readLock();
            try { return segments[seg].containsKey(k.longValue()) && segments[seg].get(k.longValue()) == v; }
            finally { locks[seg].unlockRead(stamp); }
        }

        @Override public boolean remove(Object o) {
            if (!(o instanceof Map.Entry<?, ?> e)) return false;
            if (!(e.getKey() instanceof Long k) || !(e.getValue() instanceof Byte v)) return false;
            return Long2ByteConcurrentHashMap.this.remove(k.longValue(), v.byteValue());
        }

        @Override public int size() { return Long2ByteConcurrentHashMap.this.size(); }
        @Override public void clear() { Long2ByteConcurrentHashMap.this.clear(); }
    }

    private final class Values extends AbstractByteCollection {
        @Override public ByteIterator iterator() {
            ByteArrayList vals = new ByteArrayList(size());
            for (int i = 0; i < segmentCount; i++) {
                long stamp = locks[i].readLock();
                try { for (byte v : segments[i].values()) vals.add(v); }
                finally { locks[i].unlockRead(stamp); }
            }
            return vals.iterator();
        }
        @Override public ByteSpliterator spliterator() {
            return ByteSpliterators.asSpliterator(iterator(), size(), ByteSpliterators.COLLECTION_SPLITERATOR_CHARACTERISTICS);
        }
        @Override public boolean contains(byte v) { return Long2ByteConcurrentHashMap.this.containsValue(v); }
        @Override public int size() { return Long2ByteConcurrentHashMap.this.size(); }
        @Override public void clear() { Long2ByteConcurrentHashMap.this.clear(); }
    }

    private static final class MutableEntry implements Long2ByteMap.Entry {
        long key; byte value;
        @Override public long getLongKey() { return key; }
        @Override public byte getByteValue() { return value; }
        @Override public byte setValue(byte v) { byte old = value; value = v; return old; }
    }

    private record ImmutableEntry(long key, byte value) implements Long2ByteMap.Entry {
        @Override public long getLongKey() { return key; }
        @Override public byte getByteValue() { return value; }
        @Override public byte setValue(byte v) { throw new UnsupportedOperationException(); }
        @Override public boolean equals(Object o) {
            return o instanceof Long2ByteMap.Entry e && key == e.getLongKey() && value == e.getByteValue();
        }
        @Override public int hashCode() { return HashCommon.long2int(key) ^ value; }
        @Override public String toString() { return key + "=>" + value; }
    }

    @FunctionalInterface
    public interface LongByteConsumer { void accept(long key, byte value); }

    public void forEach(LongByteConsumer consumer) {
        for (int i = 0; i < segmentCount; i++) {
            long stamp = locks[i].readLock();
            try {
                for (Long2ByteMap.Entry e : segments[i].long2ByteEntrySet())
                    consumer.accept(e.getLongKey(), e.getByteValue());
            } finally { locks[i].unlockRead(stamp); }
        }
    }
}
