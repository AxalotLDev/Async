package com.axalotl.async.common.mixin.server;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.concurrent.locks.ReentrantLock;

@Mixin(PersistentEntitySectionManager.Callback.class)
public abstract class PersistentEntitySectionManagerCallbackMixin {

    @Shadow
    private EntitySection<?> currentSection;

    @Unique
    private final ReentrantLock async$lock = new ReentrantLock();

    @Unique
    private volatile boolean async$removed = false;

    @WrapMethod(method = "onMove")
    private void onMove(Operation<Void> original) {
        if (async$removed) {
            return;
        }

        if (!async$lock.tryLock()) {
            return;
        }

        try {
            if (!async$removed && currentSection != null) {
                original.call();
            }
        } finally {
            async$lock.unlock();
        }
    }

    @WrapMethod(method = "onRemove")
    private void onRemove(Entity.RemovalReason reason, Operation<Void> original) {
        async$lock.lock();
        try {
            if (async$removed) {
                return;
            }
            async$removed = true;

            if (currentSection != null) {
                original.call(reason);
            }
        } finally {
            async$lock.unlock();
        }
    }
}