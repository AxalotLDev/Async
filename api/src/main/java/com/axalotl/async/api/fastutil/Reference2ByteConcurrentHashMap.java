package com.axalotl.async.api.fastutil;

import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import it.unimi.dsi.fastutil.bytes.ByteCollection;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.Reference2ByteFunction;
import it.unimi.dsi.fastutil.objects.Reference2ByteMap;
import it.unimi.dsi.fastutil.objects.Reference2ByteOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceSet;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

/**
 * Drop-in concurrent replacement for {@link Reference2ByteOpenHashMap}.
 * Same design notes as
 * {@link Reference2ReferenceConcurrentHashMap}: lazy-init lock survives
 * {@code super(Map)} re-entry; {@link ReentrantReadWriteLock} so fastutil's
 * virtual {@code putAll → size/put} re-entries do not self-deadlock.
 */
public final class Reference2ByteConcurrentHashMap<K> extends Reference2ByteOpenHashMap<K> {

    private volatile ReentrantReadWriteLock lockField;

    private ReentrantReadWriteLock lock() {
        ReentrantReadWriteLock l = lockField;
        if (l != null) return l;
        l = new ReentrantReadWriteLock();
        lockField = l;
        return l;
    }

    public Reference2ByteConcurrentHashMap() { super(); }
    public Reference2ByteConcurrentHashMap(int expected) { super(expected); }
    public Reference2ByteConcurrentHashMap(int expected, float f) { super(expected, f); }
    public Reference2ByteConcurrentHashMap(Map<? extends K, ? extends Byte> m) { super(m); }
    public Reference2ByteConcurrentHashMap(Reference2ByteMap<K> m) { super(m); }

    @Override public byte getByte(Object k) { var l = lock(); l.readLock().lock(); try { return super.getByte(k); } finally { l.readLock().unlock(); } }
    @Override public byte getOrDefault(Object k, byte d) { var l = lock(); l.readLock().lock(); try { return super.getOrDefault(k, d); } finally { l.readLock().unlock(); } }
    @Override public boolean containsKey(Object k) { var l = lock(); l.readLock().lock(); try { return super.containsKey(k); } finally { l.readLock().unlock(); } }
    @Override public boolean containsValue(byte v) { var l = lock(); l.readLock().lock(); try { return super.containsValue(v); } finally { l.readLock().unlock(); } }
    @Override public int size() { var l = lock(); l.readLock().lock(); try { return super.size(); } finally { l.readLock().unlock(); } }
    @Override public boolean isEmpty() { var l = lock(); l.readLock().lock(); try { return super.isEmpty(); } finally { l.readLock().unlock(); } }

    @Override public byte put(K k, byte v) { var l = lock(); l.writeLock().lock(); try { return super.put(k, v); } finally { l.writeLock().unlock(); } }
    @Override public byte putIfAbsent(K k, byte v) { var l = lock(); l.writeLock().lock(); try { return super.putIfAbsent(k, v); } finally { l.writeLock().unlock(); } }
    @Override public byte removeByte(Object k) { var l = lock(); l.writeLock().lock(); try { return super.removeByte(k); } finally { l.writeLock().unlock(); } }
    @Override public boolean remove(Object k, byte v) { var l = lock(); l.writeLock().lock(); try { return super.remove(k, v); } finally { l.writeLock().unlock(); } }
    @Override public byte replace(K k, byte v) { var l = lock(); l.writeLock().lock(); try { return super.replace(k, v); } finally { l.writeLock().unlock(); } }
    @Override public boolean replace(K k, byte oldV, byte newV) { var l = lock(); l.writeLock().lock(); try { return super.replace(k, oldV, newV); } finally { l.writeLock().unlock(); } }
    @Override public byte computeIfAbsent(K k, java.util.function.ToIntFunction<? super K> f) { var l = lock(); l.writeLock().lock(); try { return super.computeIfAbsent(k, f); } finally { l.writeLock().unlock(); } }
    @Override public byte computeIfAbsent(K k, Reference2ByteFunction<? super K> f) { var l = lock(); l.writeLock().lock(); try { return super.computeIfAbsent(k, f); } finally { l.writeLock().unlock(); } }
    @Override public byte computeByteIfPresent(K k, BiFunction<? super K, ? super Byte, ? extends Byte> f) { var l = lock(); l.writeLock().lock(); try { return super.computeByteIfPresent(k, f); } finally { l.writeLock().unlock(); } }
    @Override public byte computeByte(K k, BiFunction<? super K, ? super Byte, ? extends Byte> f) { var l = lock(); l.writeLock().lock(); try { return super.computeByte(k, f); } finally { l.writeLock().unlock(); } }
    @Override public byte mergeByte(K k, byte v, it.unimi.dsi.fastutil.bytes.ByteBinaryOperator f) { var l = lock(); l.writeLock().lock(); try { return super.mergeByte(k, v, f); } finally { l.writeLock().unlock(); } }
    @Override public byte merge(K k, byte v, BiFunction<? super Byte, ? super Byte, ? extends Byte> f) { var l = lock(); l.writeLock().lock(); try { return super.merge(k, v, f); } finally { l.writeLock().unlock(); } }

    @Override public void putAll(Map<? extends K, ? extends Byte> m) { var l = lock(); l.writeLock().lock(); try { super.putAll(m); } finally { l.writeLock().unlock(); } }
    @Override public void clear() { var l = lock(); l.writeLock().lock(); try { super.clear(); } finally { l.writeLock().unlock(); } }

    @Override public void forEach(BiConsumer<? super K, ? super Byte> action) {
        List<Map.Entry<K, Byte>> snap = snapshotEntries();
        for (Map.Entry<K, Byte> e : snap) action.accept(e.getKey(), e.getValue());
    }

    @Override public ReferenceSet<K> keySet() {
        var l = lock(); l.readLock().lock();
        try { return new ReferenceOpenHashSet<>(super.keySet()); } finally { l.readLock().unlock(); }
    }

    @Override public ByteCollection values() {
        var l = lock(); l.readLock().lock();
        try { return new ByteArrayList(super.values()); } finally { l.readLock().unlock(); }
    }

    @Override public Reference2ByteMap.FastEntrySet<K> reference2ByteEntrySet() {
        List<Map.Entry<K, Byte>> snap = snapshotEntries();
        Reference2ByteOpenHashMap<K> copy = new Reference2ByteOpenHashMap<>(snap.size());
        for (Map.Entry<K, Byte> e : snap) copy.put(e.getKey(), e.getValue().byteValue());
        return copy.reference2ByteEntrySet();
    }

    private List<Map.Entry<K, Byte>> snapshotEntries() {
        var l = lock(); l.readLock().lock();
        try {
            List<Map.Entry<K, Byte>> out = new ArrayList<>(super.size());
            ObjectIterator<Reference2ByteMap.Entry<K>> it = super.reference2ByteEntrySet().iterator();
            while (it.hasNext()) {
                Reference2ByteMap.Entry<K> e = it.next();
                out.add(Map.entry(e.getKey(), e.getByteValue()));
            }
            return out;
        } finally { l.readLock().unlock(); }
    }

    @Override public int hashCode() { var l = lock(); l.readLock().lock(); try { return super.hashCode(); } finally { l.readLock().unlock(); } }
    @Override public boolean equals(Object o) { var l = lock(); l.readLock().lock(); try { return super.equals(o); } finally { l.readLock().unlock(); } }
    @Override public String toString() { var l = lock(); l.readLock().lock(); try { return super.toString(); } finally { l.readLock().unlock(); } }
    @Override public Reference2ByteOpenHashMap<K> clone() { var l = lock(); l.readLock().lock(); try { return super.clone(); } finally { l.readLock().unlock(); } }
    @Override public boolean trim() { var l = lock(); l.writeLock().lock(); try { return super.trim(); } finally { l.writeLock().unlock(); } }
    @Override public boolean trim(int n) { var l = lock(); l.writeLock().lock(); try { return super.trim(n); } finally { l.writeLock().unlock(); } }
}
