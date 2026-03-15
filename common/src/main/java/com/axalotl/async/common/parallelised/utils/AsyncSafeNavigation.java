package com.axalotl.async.common.parallelised.utils;

import net.minecraft.core.BlockPos;

public interface AsyncSafeNavigation {
    boolean async$shouldRecomputePathSafe(BlockPos pos);
}