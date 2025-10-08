package com.axalotl.async.common.mixin.entity.spawn;

import com.axalotl.async.common.parallelised.ConcurrentCollections;
import com.axalotl.async.common.parallelised.fastutil.Long2ObjectConcurrentHashMap;
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
public class LocalMobCapCalculatorMixin {
    @Shadow
    private final Map<ServerPlayer, LocalMobCapCalculator.MobCounts> playerMobCounts = ConcurrentCollections.newHashMap();

    @Shadow
    private final Long2ObjectMap<List<ServerPlayer>> playersNearChunk = new Long2ObjectConcurrentHashMap<>();

    @Inject(method = "getPlayersNear", at = @At("RETURN"), cancellable = true)
    private void onGetMobSpawnablePlayers(ChunkPos pos, CallbackInfoReturnable<List<ServerPlayer>> cir) {
        if (cir.getReturnValue() == null) {
            cir.setReturnValue(List.of());
        }
    }
}