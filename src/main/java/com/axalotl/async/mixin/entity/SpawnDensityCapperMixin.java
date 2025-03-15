package com.axalotl.async.mixin.entity;

import com.axalotl.async.parallelised.ConcurrentCollections;
import com.axalotl.async.parallelised.fastutil.Long2ObjectConcurrentHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.SpawnDensityCapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Map;

@Mixin(SpawnDensityCapper.class)
public class SpawnDensityCapperMixin {

    @Shadow
    private final Map<ServerPlayerEntity, SpawnDensityCapper.DensityCap> playersToDensityCap = ConcurrentCollections.newHashMap();

    @Shadow
    private final Long2ObjectMap<List<ServerPlayerEntity>> chunkPosToMobSpawnablePlayers = new Long2ObjectConcurrentHashMap<>();

    @Inject(method = "getMobSpawnablePlayers", at = @At("RETURN"), cancellable = true)
    private void onGetMobSpawnablePlayers(ChunkPos chunkPos, CallbackInfoReturnable<List<ServerPlayerEntity>> cir) {
        if (cir.getReturnValue() == null) {
            cir.setReturnValue(List.of());
        }
    }
}
