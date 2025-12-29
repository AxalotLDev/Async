package com.axalotl.async.common.mixin.lithium;

import net.caffeinemc.mods.lithium.common.block.BlockStateFlagHolder;
import net.caffeinemc.mods.lithium.common.block.BlockStateFlags;
import net.caffeinemc.mods.lithium.common.world.section.RandomTickingSectionDataHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;

/**
 * Makes Lithium's random tick optimization thread-safe for async entity ticking.
 *
 * Problem: Lithium caches tickable block counts in byte arrays. When blocks are modified
 * from async threads (by async entity ticking), this cache can become inconsistent with
 * the actual block data, causing "Failed to find random tickable position" crashes.
 *
 * Solution:
 * 1. Snapshot the cache data at the start to avoid mid-operation changes
 * 2. Validate total count before searching
 * 3. If optimized search fails, fall back to full section search
 * 4. If block count decreased due to concurrent modification, exit gracefully
 *
 * This preserves Lithium's optimization for the common case while handling race conditions.
 */
@Mixin(value = RandomTickingSectionDataHelper.class, priority = 1500)
public class LithiumRandomTickMixin {

    @Unique
    private static final int MINISECTION_SIZE = 248;

    @Unique
    private static final int RANDOM_TICKING_FLAG_MASK = 1 << BlockStateFlags.RANDOM_TICKING.getIndex();

    /**
     * @author Async mod
     * @reason Thread-safe random tick block search with snapshot and fallback
     */
    @Overwrite
    public static void randomTickNthBlock(
            LevelChunkSection section,
            int randomBlockIndex,
            byte[] data,
            ServerLevel level,
            int sectionBlockX,
            int sectionBlockY,
            int sectionBlockZ,
            RandomSource random
    ) {
        // Snapshot the cache data to avoid concurrent modification during search
        final int dataLength = data.length;
        final int[] snapshot = new int[dataLength];
        int totalCachedCount = 0;

        for (int i = 0; i < dataLength; i++) {
            int count = Byte.toUnsignedInt(data[i]);
            snapshot[i] = count;
            totalCachedCount += count;
        }

        // Early exit if index is out of bounds (blocks were removed concurrently)
        if (randomBlockIndex >= totalCachedCount) {
            return;
        }

        // Find the minisection using snapshot data
        int minisectionIndex = 0;
        int remainingIndex = randomBlockIndex;

        for (; minisectionIndex < dataLength; minisectionIndex++) {
            int countInMinisection = snapshot[minisectionIndex];
            if (remainingIndex >= countInMinisection) {
                remainingIndex -= countInMinisection;
            } else {
                break;
            }
        }

        // Search from minisection start (optimized path)
        int searchStart = minisectionIndex * MINISECTION_SIZE;
        BlockState foundState = null;
        int foundX = 0, foundY = 0, foundZ = 0;
        int tickableFound = 0;

        for (int blockIndex = searchStart; blockIndex < 4096; blockIndex++) {
            int x = blockIndex & 0xF;
            int y = (blockIndex >> 8) & 0xF;
            int z = (blockIndex >> 4) & 0xF;

            BlockState blockState = section.getBlockState(x, y, z);
            if ((((BlockStateFlagHolder) blockState).lithium$getAllFlags() & RANDOM_TICKING_FLAG_MASK) != 0) {
                if (tickableFound == remainingIndex) {
                    foundState = blockState;
                    foundX = x;
                    foundY = y;
                    foundZ = z;
                    break;
                }
                tickableFound++;
            }
        }

        // Fallback: if not found in expected range, search from beginning
        // This handles cases where blocks moved due to concurrent modifications
        if (foundState == null && searchStart > 0) {
            int actualIndex = randomBlockIndex;

            for (int blockIndex = 0; blockIndex < 4096; blockIndex++) {
                int x = blockIndex & 0xF;
                int y = (blockIndex >> 8) & 0xF;
                int z = (blockIndex >> 4) & 0xF;

                BlockState blockState = section.getBlockState(x, y, z);
                if ((((BlockStateFlagHolder) blockState).lithium$getAllFlags() & RANDOM_TICKING_FLAG_MASK) != 0) {
                    if (actualIndex == 0) {
                        foundState = blockState;
                        foundX = x;
                        foundY = y;
                        foundZ = z;
                        break;
                    }
                    actualIndex--;
                }
            }
        }

        // Execute tick if block was found
        if (foundState != null) {
            BlockPos pos = new BlockPos(
                    sectionBlockX | foundX,
                    sectionBlockY | foundY,
                    sectionBlockZ | foundZ
            );

            if (foundState.isRandomlyTicking()) {
                foundState.randomTick(level, pos, random);
            }

            FluidState fluidState = foundState.getFluidState();
            if (fluidState.isRandomlyTicking()) {
                fluidState.randomTick(level, pos, random);
            }
        }

        // If not found: block count decreased due to concurrent modification
        // This is safe - the block was already processed or is no longer tickable
    }
}