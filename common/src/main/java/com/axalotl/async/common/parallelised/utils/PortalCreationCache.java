package com.axalotl.async.common.parallelised.utils;

import net.minecraft.resources.ResourceKey;
import net.minecraft.util.BlockUtil;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PortalCreationCache {

    private static final Map<ResourceKey<Level>, BlockUtil.FoundRectangle> cache = new ConcurrentHashMap<>();

    public static BlockUtil.FoundRectangle get(ResourceKey<Level> dimension) {
        return cache.get(dimension);
    }

    public static void put(ResourceKey<Level> dimension, BlockUtil.FoundRectangle rectangle) {
        cache.put(dimension, rectangle);
    }

    public static void clear() {
        cache.clear();
    }
}