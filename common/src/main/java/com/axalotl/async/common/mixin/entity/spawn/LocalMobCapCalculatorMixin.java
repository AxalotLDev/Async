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

@Mixin(value = LocalMobCapCalculator.class, priority = 500)
public class LocalMobCapCalculatorMixin {

    @Mutable
    @Final
    @Shadow
    private Map<ServerPlayer, LocalMobCapCalculator.MobCounts> playerMobCounts;

    @Mutable
    @Final
    @Shadow
    private Long2ObjectMap<List<ServerPlayer>> playersNearChunk;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void async$swapToConcurrentMaps(CallbackInfo ci) {
        ConcurrentHashMap<ServerPlayer, LocalMobCapCalculator.MobCounts> concurrentMobCounts = new ConcurrentHashMap<>();
        if (this.playerMobCounts != null && !this.playerMobCounts.isEmpty()) {
            concurrentMobCounts.putAll(this.playerMobCounts);
        }
        this.playerMobCounts = concurrentMobCounts;

        Long2ObjectConcurrentHashMap<List<ServerPlayer>> concurrentPlayersNear = new Long2ObjectConcurrentHashMap<>();
        if (this.playersNearChunk != null && !this.playersNearChunk.isEmpty()) {
            for (Long2ObjectMap.Entry<List<ServerPlayer>> e : this.playersNearChunk.long2ObjectEntrySet()) {
                concurrentPlayersNear.put(e.getLongKey(), e.getValue());
            }
        }
        this.playersNearChunk = concurrentPlayersNear;
    }

    @Inject(method = "getPlayersNear", at = @At("RETURN"), cancellable = true)
    private void onGetMobSpawnablePlayers(ChunkPos pos, CallbackInfoReturnable<List<ServerPlayer>> cir) {
        if (cir.getReturnValue() == null) {
            cir.setReturnValue(List.of());
        }
    }
}
