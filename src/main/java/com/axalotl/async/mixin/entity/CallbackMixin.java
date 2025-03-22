package com.axalotl.async.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.concurrent.locks.ReentrantLock;

@Mixin(PersistentEntitySectionManager.Callback.class)
public abstract class CallbackMixin implements AutoCloseable {
    @Unique
    private static final ReentrantLock async$lock = new ReentrantLock();

    @WrapMethod(method = "onMove")
    private void onMove(Operation<Void> original) {
        synchronized (async$lock) {
            original.call();
        }
    }

    @WrapMethod(method = "onRemove")
    private void onRemove(Entity.RemovalReason reason, Operation<Void> original) {
        synchronized (async$lock) {
            original.call(reason);
        }
    }
}
