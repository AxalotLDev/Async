package com.axalotl.async.mixin.entity;

import com.axalotl.async.parallelised.ConcurrentCollections;
import com.axalotl.async.parallelised.fastutil.Long2ObjectConcurrentHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.SpawnDensityCapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.List;
import java.util.Map;

@Mixin(SpawnDensityCapper.class)
public class SpawnDensityCapperMixin {

    @Shadow
    private final Map<ServerPlayerEntity, SpawnDensityCapper.DensityCap> playersToDensityCap = ConcurrentCollections.newHashMap();

    @Shadow
    private final Long2ObjectMap<List<ServerPlayerEntity>> chunkPosToMobSpawnablePlayers = new Long2ObjectConcurrentHashMap<>();
}
