package com.axalotl.async.mixin.server;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.concurrent.locks.ReentrantLock;

@Mixin(ServerWorld.ServerEntityHandler.class)
public class ServerEntityHandlerMixin {
    @Unique
    private static final ReentrantLock lock = new ReentrantLock();

    @WrapMethod(method = "startTicking(Lnet/minecraft/entity/Entity;)V")
    private void startTicking(Entity entity, Operation<Void> original) {
        synchronized (lock) {
            original.call(entity);
        }
    }

    @WrapMethod(method = "stopTicking(Lnet/minecraft/entity/Entity;)V")
    private void stopTicking(Entity entity, Operation<Void> original) {
        synchronized (lock) {
            original.call(entity);
        }
    }
}
