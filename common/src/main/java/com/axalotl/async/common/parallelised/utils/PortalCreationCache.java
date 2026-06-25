package com.axalotl.async.common.parallelised.utils;

import net.minecraft.BlockUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PortalCreationCache {

    public record CacheKey(ResourceKey<Level> dimension, int chunkX, int chunkZ) {}

    private static final Map<CacheKey, BlockUtil.FoundRectangle> cache = new ConcurrentHashMap<>();

    private static CacheKey keyFor(ResourceKey<Level> dimension, BlockPos exitPos) {
        return new CacheKey(dimension, exitPos.getX() >> 4, exitPos.getZ() >> 4);
    }

    public static BlockUtil.FoundRectangle get(ResourceKey<Level> dimension, BlockPos exitPos) {
        return cache.get(keyFor(dimension, exitPos));
    }

    public static void put(ResourceKey<Level> dimension, BlockPos exitPos, BlockUtil.FoundRectangle rectangle) {
        cache.put(keyFor(dimension, exitPos), rectangle);
    }

    public static void clear() {
        cache.clear();
    }
}
