package com.axalotl.async.common.mixin.server;

import com.axalotl.async.common.parallelised.ConcurrentCollections;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

@Mixin(value = ChunkMap.TrackedEntity.class)
public class ChunkMapTrackedEntityMixin {

    @Shadow
    final Set<ServerGamePacketListenerImpl> seenBy = ConcurrentCollections.newHashSet();

    @Shadow
    @Final
    public Entity entity;

    @Inject(method = "updatePlayer(Lnet/minecraft/server/level/ServerPlayer;)V", at = @At("HEAD"), cancellable = true)
    private void updatePlayerNull(ServerPlayer player, CallbackInfo ci) {
        if (this.entity.isRemoved()) {
            ci.cancel();
        }
    }

    @WrapMethod(method = "updatePlayer(Lnet/minecraft/server/level/ServerPlayer;)V")
    private synchronized void updatePlayer(ServerPlayer player, Operation<Void> original) {
        original.call(player);
    }
}