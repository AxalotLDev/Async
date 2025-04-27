package com.axalotl.async.common.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.concurrent.locks.ReentrantLock;

@Mixin(ServerLevel.EntityCallbacks.class)
public class EntityCallbacksMixin {

    @Unique
    private static final ReentrantLock async$lock = new ReentrantLock();

    @WrapMethod(method = "onTickingStart(Lnet/minecraft/world/entity/Entity;)V")
    private synchronized void onTickingStart(Entity entity, Operation<Void> original) {
        synchronized (async$lock) {
            original.call(entity);
        }
    }

    @WrapMethod(method = "onTickingEnd(Lnet/minecraft/world/entity/Entity;)V")
    private synchronized void onTickingEnd(Entity entity, Operation<Void> original) {
        synchronized (async$lock) {
            original.call(entity);
        }
    }
}