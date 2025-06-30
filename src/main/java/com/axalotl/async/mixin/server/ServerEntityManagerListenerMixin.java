package com.axalotl.async.mixin.server;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerEntityManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.concurrent.locks.ReentrantLock;

@Mixin(ServerEntityManager.Listener.class)
public abstract class ServerEntityManagerListenerMixin implements AutoCloseable {
    @Unique
    private static final ReentrantLock lock = new ReentrantLock();

    @WrapMethod(method = "updateEntityPosition")
    private void updateEntityPosition(Operation<Void> original) {
        synchronized (lock) {
            original.call();
        }
    }

    @WrapMethod(method = "remove")
    private void remove(Entity.RemovalReason reason, Operation<Void> original) {
        synchronized (lock) {
            original.call(reason);
        }
    }
}
