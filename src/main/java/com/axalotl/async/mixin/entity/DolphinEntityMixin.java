package com.axalotl.async.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.mob.WaterCreatureEntity;
import net.minecraft.entity.passive.DolphinEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.concurrent.locks.ReentrantLock;

@Mixin(DolphinEntity.class)
public abstract class DolphinEntityMixin extends WaterCreatureEntity {

    @Unique
    private static final ReentrantLock lock = new ReentrantLock();

    protected DolphinEntityMixin(EntityType<? extends WaterCreatureEntity> entityType, World world) {
        super(entityType, world);
    }

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
