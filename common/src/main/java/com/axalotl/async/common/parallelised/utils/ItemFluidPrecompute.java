package com.axalotl.async.common.parallelised.utils;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.Nullable;

public final class ItemFluidPrecompute {

    private static volatile Long2ObjectOpenHashMap<FluidState> cache = null;

    public static void activate(Long2ObjectOpenHashMap<FluidState> computed) {
        cache = computed;
    }

    @Nullable
    public static FluidState get(long posLong) {
        Long2ObjectOpenHashMap<FluidState> c = cache;
        return c != null ? c.get(posLong) : null;
    }
}