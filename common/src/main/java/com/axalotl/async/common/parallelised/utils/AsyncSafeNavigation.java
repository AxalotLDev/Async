package com.axalotl.async.common.parallelised.utils;

import net.minecraft.core.BlockPos;

public interface AsyncSafeNavigation {
    boolean async$shouldRecomputePathSafe(BlockPos pos);

    record PathSnapshot(
            double centerX,
            double centerY,
            double centerZ,
            double maxDistanceSq
    ) {
        public boolean shouldRecompute(BlockPos pos) {
            double dx = pos.getX() + 0.5 - centerX;
            double dy = pos.getY() + 0.5 - centerY;
            double dz = pos.getZ() + 0.5 - centerZ;
            return dx * dx + dy * dy + dz * dz <= maxDistanceSq;
        }
    }
}