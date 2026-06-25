package com.axalotl.async.common.mixin.server;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ServerLevel.EntityCallbacks.class)
public class ServerLevelEntityCallbacksMixin {

    @WrapMethod(method = "onTickingStart(Lnet/minecraft/world/entity/Entity;)V")
    private synchronized void onTickingStart(Entity entity, Operation<Void> original) {
        original.call(entity);
    }

    @WrapMethod(method = "onTickingEnd(Lnet/minecraft/world/entity/Entity;)V")
    private synchronized void onTickingEnd(Entity entity, Operation<Void> original) {
        original.call(entity);
    }
}