package com.axalotl.async.common.mixin.entity.spawn;

import com.axalotl.async.api.fastutil.Long2ObjectConcurrentHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LocalMobCapCalculator;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(LocalMobCapCalculator.class)
public class LocalMobCapCalculatorMixin {
    @Shadow
    @Final
    @Mutable
    private Map<ServerPlayer, LocalMobCapCalculator.MobCounts> playerMobCounts;

    @Shadow
    @Final
    @Mutable
    public Long2ObjectMap<List<ServerPlayer>> playersNearChunk;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void replaceWithConcurrentCollections(CallbackInfo callbackInfo) {
        this.playersNearChunk = new Long2ObjectConcurrentHashMap<>();
        this.playerMobCounts = new ConcurrentHashMap<>();
    }

    @Inject(method = "getPlayersNear", at = @At("RETURN"), cancellable = true)
    private void onGetMobSpawnablePlayers(ChunkPos pos, CallbackInfoReturnable<List<ServerPlayer>> cir) {
        if (cir.getReturnValue() == null) {
            cir.setReturnValue(List.of());
        }
    }
}
