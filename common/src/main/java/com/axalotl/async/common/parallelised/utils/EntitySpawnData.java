package com.axalotl.async.common.parallelised.utils;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;

public record EntitySpawnData(BlockPos pos, MobCategory category, double charge, boolean isMob, ChunkPos chunkPos) {}