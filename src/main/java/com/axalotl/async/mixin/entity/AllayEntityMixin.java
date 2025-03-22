package com.axalotl.async.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.concurrent.locks.ReentrantLock;

@Mixin(Allay.class)
public abstract class AllayEntityMixin {
    @Unique
    private static final ReentrantLock lock = new ReentrantLock();

    @WrapMethod(method = "pickUpItem")
    private void loot(ItemEntity itemEntity, Operation<Void> original) {
        synchronized (lock) {
            if (!itemEntity.isRemoved()) {
                original.call(itemEntity);
            }
        }
    }
}
