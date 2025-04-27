package com.axalotl.async.common.mixin.entity;

import net.minecraft.server.level.ServerEntity;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = ServerEntity.class, priority = 1500)
public class ServerEntityMixin {

    //TODO Fix removed entity warn
    @Redirect(method = "sendPairingData", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;isRemoved()Z"))
    private boolean skipWarnRemovedEntityPacked(Entity instance) {
        return false;
    }
}