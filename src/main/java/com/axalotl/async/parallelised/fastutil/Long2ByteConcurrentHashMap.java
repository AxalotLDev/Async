package com.axalotl.async.parallelised.fastutil;

import it.unimi.dsi.fastutil.bytes.ByteCollection;
import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Long2ByteConcurrentHashMap implements Long2ByteMap {
    private final Map<Long, Byte> backing;
    byte defaultReturn = 0;

    public Long2ByteConcurrentHashMap() {
        this.backing = new ConcurrentHashMap<>();
    }

    @Override
    public byte get(long key) {
        Byte out = this.backing.get(key);
        return out == null ? this.defaultReturn : out;
    }

    @Override
    public boolean isEmpty() {
        return this.backing.isEmpty();
    }

    @Override
    public boolean containsValue(byte value) {
        return this.backing.containsValue(value);
    }

    @Override
    public void putAll(@NotNull Map<? extends Long, ? extends Byte> m) {
        this.backing.putAll(m);
    }

    @Override
    public int size() {
        return this.backing.size();
    }

    @Override
    public void defaultReturnValue(byte rv) {
        this.defaultReturn = rv;
    }

    @Override
    public byte defaultReturnValue() {
        return this.defaultReturn;
    }

    @Override
    public ObjectSet<Long2ByteMap.Entry> long2ByteEntrySet() {
        return FastUtilHackUtil.entrySetLongByteWrap(this.backing);
    }

    @Override
    public @NotNull LongSet keySet() {
        return FastUtilHackUtil.wrapLongSet(this.backing.keySet());
    }

    public @NotNull ByteCollection values() {
        return FastUtilHackUtil.wrapBytes(this.backing.values());
    }

    @Override
    public boolean containsKey(long key) {
        return this.backing.containsKey(key);
    }

    @Override
    public byte put(long key, byte value) {
        Byte out = this.backing.put(key, value);
        return out == null ? this.defaultReturn : out;
    }

    @Override
    public byte remove(long key) {
        Byte out = this.backing.remove(key);
        return out == null ? this.defaultReturn : out;
    }
}