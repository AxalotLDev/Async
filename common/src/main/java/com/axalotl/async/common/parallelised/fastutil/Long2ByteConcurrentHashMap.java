package com.axalotl.async.common.parallelised.fastutil;

import it.unimi.dsi.fastutil.bytes.AbstractByteCollection;
import it.unimi.dsi.fastutil.bytes.ByteCollection;
import it.unimi.dsi.fastutil.bytes.ByteIterator;
import it.unimi.dsi.fastutil.bytes.ByteSpliterator;
import it.unimi.dsi.fastutil.bytes.ByteSpliterators;
import it.unimi.dsi.fastutil.longs.AbstractLong2ByteMap;
import it.unimi.dsi.fastutil.longs.AbstractLongSet;
import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSpliterator;
import it.unimi.dsi.fastutil.longs.LongSpliterators;
import it.unimi.dsi.fastutil.objects.AbstractObjectSet;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import it.unimi.dsi.fastutil.objects.ObjectSpliterator;
import it.unimi.dsi.fastutil.objects.ObjectSpliterators;
import org.jspecify.annotations.NonNull;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe Long2ByteMap backed by ConcurrentHashMap.
 * Drop-in replacement for Long2ByteOpenHashMap in DistanceManager chunk trackers.
 * Weakly consistent iteration — safe to iterate while other threads mutate.
 */
public class Long2ByteConcurrentHashMap extends AbstractLong2ByteMap {

    private final ConcurrentHashMap<Long, Byte> map;

    public Long2ByteConcurrentHashMap() {
        this.map = new ConcurrentHashMap<>();
    }

    @Override
    public byte get(final long key) {
        Byte v = map.get(key);
        return v != null ? v : defRetValue;
    }

    @Override
    public byte getOrDefault(final long key, final byte defaultValue) {
        Byte v = map.get(key);
        return v != null ? v : defaultValue;
    }

    @Override
    public boolean containsKey(final long key) {
        return map.containsKey(key);
    }

    @Override
    public boolean containsValue(final byte value) {
        return map.containsValue(value);
    }

    @Override
    public byte put(final long key, final byte value) {
        Byte prev = map.put(key, value);
        return prev != null ? prev : defRetValue;
    }

    @Override
    public byte putIfAbsent(final long key, final byte value) {
        Byte prev = map.putIfAbsent(key, value);
        return prev != null ? prev : defRetValue;
    }

    @Override
    public byte remove(final long key) {
        Byte prev = map.remove(key);
        return prev != null ? prev : defRetValue;
    }

    @Override
    public boolean remove(final long key, final byte value) {
        return map.remove(key, value);
    }

    @Override
    public byte replace(final long key, final byte value) {
        Byte prev = map.replace(key, value);
        return prev != null ? prev : defRetValue;
    }

    @Override
    public boolean replace(final long key, final byte oldValue, final byte newValue) {
        return map.replace(key, oldValue, newValue);
    }

    @Override
    public int size() {
        return map.size();
    }

    @Override
    public boolean isEmpty() {
        return map.isEmpty();
    }

    @Override
    public void clear() {
        map.clear();
    }

    @Override
    public void putAll(Map<? extends Long, ? extends Byte> m) {
        map.putAll(m);
    }

    private volatile EntrySet entrySet;

    @Override
    public @NonNull ObjectSet<Long2ByteMap.Entry> long2ByteEntrySet() {
        if (entrySet == null) entrySet = new EntrySet();
        return entrySet;
    }

    private final class EntrySet extends AbstractObjectSet<Long2ByteMap.Entry> implements Long2ByteMap.FastEntrySet {

        @Override
        public @NonNull ObjectIterator<Long2ByteMap.Entry> iterator() {
            return new EntryIterator(map.entrySet().iterator());
        }

        @Override
        public @NonNull ObjectIterator<Long2ByteMap.Entry> fastIterator() {
            return new FastEntryIterator(map.entrySet().iterator());
        }

        @Override
        public @NonNull ObjectSpliterator<Long2ByteMap.Entry> spliterator() {
            return ObjectSpliterators.asSpliterator(iterator(), map.size(),
                    ObjectSpliterators.SET_SPLITERATOR_CHARACTERISTICS);
        }

        @Override
        public void fastForEach(java.util.function.Consumer<? super Long2ByteMap.Entry> consumer) {
            final ConcurrentEntry entry = new ConcurrentEntry();
            for (Map.Entry<Long, Byte> e : map.entrySet()) {
                entry.key = e.getKey();
                entry.value = e.getValue();
                consumer.accept(entry);
            }
        }

        @Override
        public boolean contains(Object o) {
            if (!(o instanceof Map.Entry<?, ?> e)) return false;
            if (!(e.getKey() instanceof Long k)) return false;
            if (!(e.getValue() instanceof Byte v)) return false;
            Byte stored = map.get(k);
            return stored != null && stored.byteValue() == v.byteValue();
        }

        @Override
        public boolean remove(Object o) {
            if (!(o instanceof Map.Entry<?, ?> e)) return false;
            if (!(e.getKey() instanceof Long k)) return false;
            if (!(e.getValue() instanceof Byte v)) return false;
            return map.remove(k, v);
        }

        @Override
        public int size() {
            return map.size();
        }

        @Override
        public void clear() {
            map.clear();
        }

        @Override
        public void forEach(java.util.function.Consumer<? super Long2ByteMap.Entry> consumer) {
            for (Map.Entry<Long, Byte> e : map.entrySet()) {
                consumer.accept(new ConcurrentEntry(e.getKey(), e.getValue()));
            }
        }
    }

    private static final class ConcurrentEntry implements Long2ByteMap.Entry {
        long key;
        byte value;

        ConcurrentEntry() {}

        ConcurrentEntry(long key, byte value) {
            this.key = key;
            this.value = value;
        }

        @Override public long getLongKey() { return key; }
        @Override public byte getByteValue() { return value; }
        @Override public byte setValue(byte v) { byte old = value; value = v; return old; }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Map.Entry<?, ?> e)) return false;
            if (!(e.getKey() instanceof Long k)) return false;
            if (!(e.getValue() instanceof Byte v)) return false;
            return key == k && value == v;
        }

        @Override
        public int hashCode() {
            return it.unimi.dsi.fastutil.HashCommon.long2int(key) ^ value;
        }

        @Override
        public String toString() { return key + "=>" + value; }
    }

    private record EntryIterator(Iterator<Map.Entry<Long, Byte>> backing) implements ObjectIterator<Long2ByteMap.Entry> {
        @Override public boolean hasNext() { return backing.hasNext(); }

        @Override
        public Long2ByteMap.Entry next() {
            Map.Entry<Long, Byte> e = backing.next();
            return new ConcurrentEntry(e.getKey(), e.getValue());
        }

        @Override
        public void remove() { backing.remove(); }
    }

    private static final class FastEntryIterator implements ObjectIterator<Long2ByteMap.Entry> {
        private final Iterator<Map.Entry<Long, Byte>> backing;
        private final ConcurrentEntry entry = new ConcurrentEntry();

        FastEntryIterator(Iterator<Map.Entry<Long, Byte>> backing) {
            this.backing = backing;
        }

        @Override public boolean hasNext() { return backing.hasNext(); }

        @Override
        public Long2ByteMap.Entry next() {
            Map.Entry<Long, Byte> e = backing.next();
            entry.key = e.getKey();
            entry.value = e.getValue();
            return entry;
        }

        @Override
        public void remove() { backing.remove(); }
    }

    private volatile LongSet keySet;

    @Override
    public @NonNull LongSet keySet() {
        if (keySet == null) keySet = new KeySet();
        return keySet;
    }

    private final class KeySet extends AbstractLongSet {
        @Override
        public @NonNull LongIterator iterator() {
            return new LongIterator() {
                private final Iterator<Long> it = map.keySet().iterator();
                @Override public boolean hasNext() { return it.hasNext(); }
                @Override public long nextLong() { return it.next(); }
                @Override public void remove() { it.remove(); }
            };
        }

        @Override
        public @NonNull LongSpliterator spliterator() {
            return LongSpliterators.asSpliterator(iterator(), map.size(),
                    LongSpliterators.SET_SPLITERATOR_CHARACTERISTICS);
        }

        @Override public int size() { return map.size(); }
        @Override public boolean contains(long k) { return map.containsKey(k); }
        @Override public boolean remove(long k) { return map.remove(k) != null; }
        @Override public void clear() { map.clear(); }

        @Override
        public void forEach(java.util.function.LongConsumer consumer) {
            for (long k : map.keySet()) {
                consumer.accept(k);
            }
        }
    }

    private volatile ByteCollection vals;

    @Override
    public @NonNull ByteCollection values() {
        if (vals == null) vals = new Values();
        return vals;
    }

    private final class Values extends AbstractByteCollection {
        @Override
        public @NonNull ByteIterator iterator() {
            return new ByteIterator() {
                private final Iterator<Byte> it = map.values().iterator();
                @Override public boolean hasNext() { return it.hasNext(); }
                @Override public byte nextByte() { return it.next(); }
                @Override public void remove() { it.remove(); }
            };
        }

        @Override
        public @NonNull ByteSpliterator spliterator() {
            return ByteSpliterators.asSpliterator(iterator(), map.size(),
                    ByteSpliterators.COLLECTION_SPLITERATOR_CHARACTERISTICS);
        }

        @Override public int size() { return map.size(); }
        @Override public boolean contains(byte v) { return map.containsValue(v); }
        @Override public void clear() { map.clear(); }

        @Override
        public void forEach(it.unimi.dsi.fastutil.bytes.ByteConsumer consumer) {
            for (byte v : map.values()) {
                consumer.accept(v);
            }
        }
    }

    @Override
    public void forEach(it.unimi.dsi.fastutil.longs.LongByteBiConsumer consumer) {
        for (Map.Entry<Long, Byte> e : map.entrySet()) {
            consumer.accept((long) e.getKey(), (byte) e.getValue());
        }
    }
}