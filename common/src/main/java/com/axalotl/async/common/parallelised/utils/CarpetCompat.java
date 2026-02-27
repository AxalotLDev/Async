package com.axalotl.async.common.parallelised.utils;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.lang.reflect.Field;
import java.util.HashMap;

public final class CarpetCompat {

    private static boolean checked = false;
    private static boolean available = false;
    @SuppressWarnings("rawtypes")
    private static HashMap carpetChunkCounts = null;

    private CarpetCompat() {}

    private static void init() {
        if (checked) return;
        checked = true;
        Class<?> clazz = null;
        try { clazz = Class.forName("carpet.utils.SpawnReporter"); } catch (ClassNotFoundException ignored) {}
        if (clazz == null) return;
        Field field = null;
        try { field = clazz.getField("chunkCounts"); } catch (NoSuchFieldException ignored) {}
        if (field == null) return;
        try { carpetChunkCounts = (HashMap) field.get(null); } catch (IllegalAccessException ignored) {}
        available = carpetChunkCounts != null;
    }

    @SuppressWarnings("unchecked")
    public static void updateChunkCount(ResourceKey<Level> dimension, int count) {
        init();
        if (!available) return;
        carpetChunkCounts.put(dimension, count);
    }
}