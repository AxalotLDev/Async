package com.axalotl.async.mixin.entity;

import com.axalotl.async.parallelised.ConcurrentCollections;
import com.axalotl.async.parallelised.fastutil.Long2ObjectConcurrentHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LocalMobCapCalculator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Map;

@Mixin(LocalMobCapCalculator.class)
public abstract class SpawnDensityCapperMixin {

    @Shadow
    private final Long2ObjectMap<List<ServerPlayer>> playersNearChunk = new Long2ObjectConcurrentHashMap<>();

    @Shadow
    private final Map<ServerPlayer, ?> playerMobCounts = ConcurrentCollections.newHashMap();

    @Inject(method = "getPlayersNear", at = @At("RETURN"), cancellable = true)
    private void onGetMobSpawnablePlayers(ChunkPos chunkPos, CallbackInfoReturnable<List<ServerPlayer>> cir) {
        if (cir.getReturnValue() == null) {
            cir.setReturnValue(List.of());
        }
    }
}
