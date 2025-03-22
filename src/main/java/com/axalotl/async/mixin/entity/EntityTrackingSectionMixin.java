package com.axalotl.async.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.level.entity.Visibility;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.concurrent.locks.ReentrantLock;

@Mixin(EntitySection.class)
public class EntityTrackingSectionMixin {
    @Unique
    private static final ReentrantLock lock = new ReentrantLock();

    @WrapMethod(method = "add")
    private void add(EntityAccess entity, Operation<Void> original) {
        synchronized (lock) {
            original.call(entity);
        }
    }

    @WrapMethod(method = "remove")
    private boolean remove(EntityAccess entity, Operation<Boolean> original) {
        synchronized (lock) {
            return original.call(entity);
        }
    }

    @WrapMethod(method = "updateChunkStatus")
    private Visibility swapStatus(Visibility status, Operation<Visibility> original) {
        synchronized (lock) {
            return original.call(status);
        }
    }
}