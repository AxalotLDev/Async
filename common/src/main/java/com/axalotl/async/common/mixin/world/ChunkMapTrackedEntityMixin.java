package com.axalotl.async.common.mixin.world;

import com.axalotl.async.common.parallelised.ConcurrentCollections;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

@Mixin(value = ChunkMap.TrackedEntity.class)
public class ChunkMapTrackedEntityMixin {

    @Mutable
    @Final
    @Shadow
    private Set<ServerGamePacketListenerImpl> seenBy = ConcurrentCollections.newHashSet();

    @Inject(method = "<init>", at = @At("RETURN"))
    private void init(CallbackInfo ci) {
        seenBy = ConcurrentCollections.newHashSet();
    }

    @WrapMethod(method = "updatePlayer(Lnet/minecraft/server/level/ServerPlayer;)V")
    private synchronized void updateTrackingStatus(ServerPlayer player, Operation<Void> original) {
        original.call(player);
    }
}