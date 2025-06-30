package com.axalotl.async.mixin.server;

import com.axalotl.async.parallelised.ConcurrentCollections;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.network.PlayerAssociatedNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerChunkLoadingManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

@Mixin(value = ServerChunkLoadingManager.EntityTracker.class)
public class ServerChunkLoadingManagerEntityTrackerMixin {

    @Mutable
    @Final
    @Shadow
    private Set<PlayerAssociatedNetworkHandler> listeners;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void init(CallbackInfo ci) {
        listeners = ConcurrentCollections.newHashSet();
    }

    @WrapMethod(method = "updateTrackedStatus(Lnet/minecraft/server/network/ServerPlayerEntity;)V")
    private synchronized void updateTrackingStatus(ServerPlayerEntity player, Operation<Void> original) {
        original.call(player);
    }
}