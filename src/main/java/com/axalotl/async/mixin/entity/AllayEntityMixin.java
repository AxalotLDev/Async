package com.axalotl.async.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.entity.InventoryOwner;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.passive.AllayEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.concurrent.locks.ReentrantLock;

@Mixin(AllayEntity.class)
public abstract class AllayEntityMixin implements InventoryOwner {
    @Unique
    private static final ReentrantLock lock = new ReentrantLock();

    @WrapMethod(method = "loot")
    private void loot(ItemEntity item, Operation<Void> original) {
        lock.lock();
        try {
            if (!item.isRemoved() && item.getEntityWorld() != null)
                original.call(item);
        } finally {
            lock.unlock();
        }
    }
}

