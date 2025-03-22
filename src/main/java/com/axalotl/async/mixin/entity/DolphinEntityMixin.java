package com.axalotl.async.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Dolphin;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.concurrent.locks.ReentrantLock;

@Mixin(Dolphin.class)
public abstract class DolphinEntityMixin extends WaterAnimal {

    @Unique
    private static final ReentrantLock lock = new ReentrantLock();

    protected DolphinEntityMixin(EntityType<? extends WaterAnimal> entityType, Level world) {
        super(entityType, world);
    }

    @WrapMethod(method = "pickUpItem")
    private void loot(ItemEntity itemEntity, Operation<Void> original) {
        synchronized (lock) {
            if (!itemEntity.isRemoved()) {
                original.call(itemEntity);
            }
        }
    }
}
