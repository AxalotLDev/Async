package com.axalotl.async.api.fastutil;

import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceMap;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceCollection;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceSet;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Drop-in concurrent replacement for
 * {@link Reference2ReferenceOpenHashMap} that protects the parent's
 * internal arrays with a {@link ReentrantReadWriteLock}.
 * <p>
 * Extension (not composition) keeps {@code instanceof
 * Reference2ReferenceOpenHashMap} checks valid and lets the async mod
 * transparently swap instances via bytecode rewriting — callers holding a
 * reference typed as the parent class keep working.
 * <p>
 * <b>Why reentrant rather than {@link java.util.concurrent.locks.StampedLock}</b>:
 * fastutil's parent methods freely call {@code this.X} virtually. For example
 * {@code Reference2ReferenceOpenHashMap.putAll} calls {@code this.size()} and
 * then {@code super.putAll(m)} which in turn loops {@code put(k, v)} through
 * {@code AbstractReference2ReferenceMap.putAll}. Both size and put dispatch
 * into this subclass's overrides. A non-reentrant lock parks the thread on
 * the first such re-entry. ReentrantReadWriteLock handles this natively,
 * including the write-lock-holding-thread-acquires-read-lock downgrade path.
 * <p>
 * The lock is lazy-initialized because fastutil's
 * {@code Reference2ReferenceOpenHashMap(Map)} calls {@code this.putAll(m)}
 * from inside the superclass constructor — before subclass field initializers
 * have run.
 */
@SuppressWarnings("unchecked")
public final class Reference2ReferenceConcurrentHashMap<K, V> extends Reference2ReferenceOpenHashMap<K, V> {

    private volatile ReentrantReadWriteLock lockField;

    /**
     * Seq-lock counter: incremented twice per write (before and after the
     * mutation inside the {@code writeLock}), so readers can optimistically
     * walk the fastutil arrays <em>without</em> taking the RRWL read-lock.
     * Odd value = write in progress, even value = quiescent. Readers read
     * this before and after their {@code super.get}; if the two reads match
     * and are even, no write overlapped the read. This turns the common-case
     * read from an AQS CAS pair (~30-50 ns uncontended, 100+ ns under
     * multi-worker contention) into a single volatile read.
     * <p>
     * Writes still take the RRWL because fastutil's own
     * {@link Reference2ReferenceOpenHashMap#putAll} calls
     * {@code AbstractReference2ReferenceMap#putAll} which loops
     * {@code this.put(k, v)} — which re-enters our {@code put} override.
     * RRWL handles re-entry natively; a plain {@code StampedLock.writeLock}
     * would deadlock on the second acquisition.
     */
    private volatile long writeGen;

    private ReentrantReadWriteLock lock() {
        ReentrantReadWriteLock l = lockField;
        if (l != null) return l;
        l = new ReentrantReadWriteLock();
        lockField = l;
        return l;
    }

    public Reference2ReferenceConcurrentHashMap() {
        super();
    }

    public Reference2ReferenceConcurrentHashMap(int expected) {
        super(expected);
    }

    public Reference2ReferenceConcurrentHashMap(int expected, float f) {
        super(expected, f);
    }

    public Reference2ReferenceConcurrentHashMap(Map<? extends K, ? extends V> m) {
        super(m);
    }

    public Reference2ReferenceConcurrentHashMap(Reference2ReferenceMap<K, V> m) {
        super(m);
    }

    @Override
    public V get(Object k) {
        // Re-entry fast path: if the current thread is inside a write, the
        // fastutil arrays are only visible to us (RRWL write-lock excludes
        // other writers AND all readers), so no synchronization is needed.
        ReentrantReadWriteLock l = lockField;
        if (l != null && l.isWriteLockedByCurrentThread()) return super.get(k);

        // Optimistic read — validate via writeGen. No AQS traffic on the
        // happy path.
        long g0 = writeGen;
        if ((g0 & 1) == 0) {
            V v;
            try {
                v = super.get(k);
            } catch (Throwable t) {
                return getLocked(k);
            }
            if (writeGen == g0) return v;
        }
        return getLocked(k);
    }

    private V getLocked(Object k) {
        ReentrantReadWriteLock l = lock();
        l.readLock().lock();
        try { return super.get(k); } finally { l.readLock().unlock(); }
    }

    @Override
    public V getOrDefault(Object k, V defaultValue) {
        ReentrantReadWriteLock l = lockField;
        if (l != null && l.isWriteLockedByCurrentThread()) return super.getOrDefault(k, defaultValue);
        long g0 = writeGen;
        if ((g0 & 1) == 0) {
            V v;
            try {
                v = super.getOrDefault(k, defaultValue);
            } catch (Throwable t) {
                return getOrDefaultLocked(k, defaultValue);
            }
            if (writeGen == g0) return v;
        }
        return getOrDefaultLocked(k, defaultValue);
    }

    private V getOrDefaultLocked(Object k, V defaultValue) {
        ReentrantReadWriteLock l = lock();
        l.readLock().lock();
        try { return super.getOrDefault(k, defaultValue); } finally { l.readLock().unlock(); }
    }

    @Override
    public boolean containsKey(Object k) {
        ReentrantReadWriteLock l = lockField;
        if (l != null && l.isWriteLockedByCurrentThread()) return super.containsKey(k);
        long g0 = writeGen;
        if ((g0 & 1) == 0) {
            boolean r;
            try {
                r = super.containsKey(k);
            } catch (Throwable t) {
                return containsKeyLocked(k);
            }
            if (writeGen == g0) return r;
        }
        return containsKeyLocked(k);
    }

    private boolean containsKeyLocked(Object k) {
        ReentrantReadWriteLock l = lock();
        l.readLock().lock();
        try { return super.containsKey(k); } finally { l.readLock().unlock(); }
    }

    @Override
    public boolean containsValue(Object v) {
        ReentrantReadWriteLock l = lock();
        l.readLock().lock();
        try { return super.containsValue(v); } finally { l.readLock().unlock(); }
    }

    @Override
    public int size() {
        // size() reads a single int field in fastutil; optimistic read is
        // always safe (no array walk). The writeGen validate still protects
        // against the rare "torn long" on 32-bit VMs and against a resize
        // racing the read.
        long g0 = writeGen;
        if ((g0 & 1) == 0) {
            int s = super.size();
            if (writeGen == g0) return s;
        }
        ReentrantReadWriteLock l = lock();
        l.readLock().lock();
        try { return super.size(); } finally { l.readLock().unlock(); }
    }

    @Override
    public boolean isEmpty() {
        long g0 = writeGen;
        if ((g0 & 1) == 0) {
            boolean e = super.isEmpty();
            if (writeGen == g0) return e;
        }
        ReentrantReadWriteLock l = lock();
        l.readLock().lock();
        try { return super.isEmpty(); } finally { l.readLock().unlock(); }
    }

    /**
     * Runs {@code op} under the write-lock with a seq-lock sandwich:
     * {@code writeGen++} before and after the mutation so optimistic
     * readers observe the write window and fall back. Re-entrant: if the
     * current thread already holds the write-lock (fastutil's
     * {@code super.putAll} → {@code this.put} recursion), we skip the
     * bump pair — only the outermost writer publishes the generation
     * transition, keeping the counter monotonic.
     */
    private <R> R doWrite(java.util.function.Supplier<R> op) {
        ReentrantReadWriteLock l = lock();
        l.writeLock().lock();
        boolean outermost = l.getWriteHoldCount() == 1;
        try {
            if (outermost) writeGen++;
            try {
                return op.get();
            } finally {
                if (outermost) writeGen++;
            }
        } finally { l.writeLock().unlock(); }
    }

    private void doWriteVoid(Runnable op) {
        ReentrantReadWriteLock l = lock();
        l.writeLock().lock();
        boolean outermost = l.getWriteHoldCount() == 1;
        try {
            if (outermost) writeGen++;
            try {
                op.run();
            } finally {
                if (outermost) writeGen++;
            }
        } finally { l.writeLock().unlock(); }
    }

    @Override
    public V put(K k, V v) { return doWrite(() -> super.put(k, v)); }

    @Override
    public V putIfAbsent(K k, V v) { return doWrite(() -> super.putIfAbsent(k, v)); }

    @Override
    public V remove(Object k) { return doWrite(() -> super.remove(k)); }

    @Override
    public boolean remove(Object k, Object v) { return doWrite(() -> super.remove(k, v)); }

    @Override
    public V replace(K k, V v) { return doWrite(() -> super.replace(k, v)); }

    @Override
    public boolean replace(K k, V oldV, V newV) { return doWrite(() -> super.replace(k, oldV, newV)); }

    @Override
    public V computeIfAbsent(K k, Function<? super K, ? extends V> f) { return doWrite(() -> super.computeIfAbsent(k, f)); }

    @Override
    public V computeIfPresent(K k, BiFunction<? super K, ? super V, ? extends V> f) { return doWrite(() -> super.computeIfPresent(k, f)); }

    @Override
    public V compute(K k, BiFunction<? super K, ? super V, ? extends V> f) { return doWrite(() -> super.compute(k, f)); }

    @Override
    public V merge(K k, V v, BiFunction<? super V, ? super V, ? extends V> f) { return doWrite(() -> super.merge(k, v, f)); }

    @Override
    public void putAll(Map<? extends K, ? extends V> m) { doWriteVoid(() -> super.putAll(m)); }

    @Override
    public void clear() { doWriteVoid(super::clear); }

    @Override
    public void forEach(BiConsumer<? super K, ? super V> action) {
        List<Map.Entry<K, V>> snap = snapshotEntries();
        for (Map.Entry<K, V> e : snap) action.accept(e.getKey(), e.getValue());
    }

    @Override
    public void replaceAll(BiFunction<? super K, ? super V, ? extends V> f) { doWriteVoid(() -> super.replaceAll(f)); }

    @Override
    public ReferenceSet<K> keySet() {
        ReentrantReadWriteLock l = lock();
        l.readLock().lock();
        try { return new ReferenceOpenHashSet<>(super.keySet()); } finally { l.readLock().unlock(); }
    }

    @Override
    public ReferenceCollection<V> values() {
        ReentrantReadWriteLock l = lock();
        l.readLock().lock();
        try { return new ReferenceOpenHashSet<>(super.values()); } finally { l.readLock().unlock(); }
    }

    @Override
    public Reference2ReferenceMap.FastEntrySet<K, V> reference2ReferenceEntrySet() {
        List<Map.Entry<K, V>> snap = snapshotEntries();
        Reference2ReferenceOpenHashMap<K, V> copy = new Reference2ReferenceOpenHashMap<>(snap.size());
        for (Map.Entry<K, V> e : snap) copy.put(e.getKey(), e.getValue());
        return copy.reference2ReferenceEntrySet();
    }

    private List<Map.Entry<K, V>> snapshotEntries() {
        ReentrantReadWriteLock l = lock();
        l.readLock().lock();
        try {
            List<Map.Entry<K, V>> out = new ArrayList<>(super.size());
            ObjectIterator<Reference2ReferenceMap.Entry<K, V>> it = super.reference2ReferenceEntrySet().iterator();
            while (it.hasNext()) {
                Reference2ReferenceMap.Entry<K, V> e = it.next();
                out.add(new ImmutableRefEntry<>(e.getKey(), e.getValue()));
            }
            return out;
        } finally { l.readLock().unlock(); }
    }

    @Override
    public int hashCode() {
        ReentrantReadWriteLock l = lock();
        l.readLock().lock();
        try { return super.hashCode(); } finally { l.readLock().unlock(); }
    }

    @Override
    public boolean equals(Object o) {
        ReentrantReadWriteLock l = lock();
        l.readLock().lock();
        try { return super.equals(o); } finally { l.readLock().unlock(); }
    }

    @Override
    public String toString() {
        ReentrantReadWriteLock l = lock();
        l.readLock().lock();
        try { return super.toString(); } finally { l.readLock().unlock(); }
    }

    @Override
    public Reference2ReferenceOpenHashMap<K, V> clone() {
        ReentrantReadWriteLock l = lock();
        l.readLock().lock();
        try { return super.clone(); } finally { l.readLock().unlock(); }
    }

    @Override
    public boolean trim() { return doWrite(super::trim); }

    @Override
    public boolean trim(int n) { return doWrite(() -> super.trim(n)); }

    private record ImmutableRefEntry<K, V>(K key, V value) implements Map.Entry<K, V> {
        @Override public K getKey() { return key; }
        @Override public V getValue() { return value; }
        @Override public V setValue(V value) { throw new UnsupportedOperationException(); }
    }
}
