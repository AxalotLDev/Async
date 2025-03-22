package com.axalotl.async.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.concurrent.locks.ReentrantLock;

@Mixin(ServerLevel.EntityCallbacks.class)
public class ServerEntityHandlerMixin {
    @Unique
    private static final ReentrantLock lock = new ReentrantLock();

    @WrapMethod(method = "onTickingStart(Lnet/minecraft/world/entity/Entity;)V")
    private void startTicking(Entity entity, Operation<Void> original) {
        synchronized (lock) {
            original.call(entity);
        }
    }

    @WrapMethod(method = "onTickingEnd(Lnet/minecraft/world/entity/Entity;)V")
    private void stopTicking(Entity entity, Operation<Void> original) {
        synchronized (lock) {
            original.call(entity);
        }
    }
}
