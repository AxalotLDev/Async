package com.axalotl.async.api.fastutil;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntCollection;
import it.unimi.dsi.fastutil.objects.AbstractObject2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import net.minecraft.world.entity.MobCategory;

import java.util.concurrent.atomic.AtomicIntegerArray;

public final class AtomicMobCategoryCounts extends Object2IntOpenHashMap<MobCategory> {
    private static final MobCategory[] CATEGORIES = MobCategory.values();

    private final AtomicIntegerArray counts;

    public AtomicMobCategoryCounts(AtomicIntegerArray counts) {
        super(0);
        this.counts = counts;
    }

    public AtomicIntegerArray backing() {
        return this.counts;
    }

    @Override
    public int getInt(Object key) {
        return key instanceof MobCategory cat ? this.counts.get(cat.ordinal()) : this.defaultReturnValue();
    }

    @Override
    public int getOrDefault(Object key, int defaultValue) {
        return key instanceof MobCategory cat ? this.counts.get(cat.ordinal()) : defaultValue;
    }

    @Override
    public boolean containsKey(Object key) {
        return key instanceof MobCategory;
    }

    @Override
    public boolean containsValue(int value) {
        for (int i = 0, n = CATEGORIES.length; i < n; i++) {
            if (this.counts.get(i) == value) return true;
        }
        return false;
    }

    @Override
    public int size() {
        return CATEGORIES.length;
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public int put(MobCategory k, int v) {
        return this.counts.getAndSet(k.ordinal(), v);
    }

    @Override
    public int addTo(MobCategory k, int incr) {
        return this.counts.getAndAdd(k.ordinal(), incr);
    }

    @Override
    public int removeInt(Object key) {
        return key instanceof MobCategory cat ? this.counts.getAndSet(cat.ordinal(), 0) : this.defaultReturnValue();
    }

    @Override
    public void clear() {
        for (int i = 0, n = CATEGORIES.length; i < n; i++) this.counts.set(i, 0);
    }

    @Override
    public Object2IntMap.FastEntrySet<MobCategory> object2IntEntrySet() {
        return new FastEntrySetView();
    }

    private final class FastEntrySetView extends AbstractObject2IntMap.BasicEntrySet<MobCategory>
            implements Object2IntMap.FastEntrySet<MobCategory> {
        FastEntrySetView() {
            super(AtomicMobCategoryCounts.this);
        }

        @Override
        public ObjectIterator<Object2IntMap.Entry<MobCategory>> iterator() {
            return new ObjectIterator<>() {
                int idx = 0;

                @Override public boolean hasNext() { return idx < CATEGORIES.length; }

                @Override public Object2IntMap.Entry<MobCategory> next() {
                    MobCategory cat = CATEGORIES[idx];
                    int value = counts.get(idx);
                    idx++;
                    return new AbstractObject2IntMap.BasicEntry<>(cat, value);
                }
            };
        }

        @Override
        public ObjectIterator<Object2IntMap.Entry<MobCategory>> fastIterator() {
            return iterator();
        }

        @Override
        public int size() {
            return CATEGORIES.length;
        }
    }

    @Override
    public ObjectSet<MobCategory> keySet() {
        ObjectOpenHashSet<MobCategory> set = new ObjectOpenHashSet<>(CATEGORIES.length);
        for (MobCategory cat : CATEGORIES) set.add(cat);
        return set;
    }

    @Override
    public IntCollection values() {
        IntArrayList list = new IntArrayList(CATEGORIES.length);
        for (int i = 0, n = CATEGORIES.length; i < n; i++) list.add(this.counts.get(i));
        return list;
    }
}
