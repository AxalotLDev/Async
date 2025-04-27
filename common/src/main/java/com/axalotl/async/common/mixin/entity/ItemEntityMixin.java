package com.axalotl.async.common.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.concurrent.locks.ReentrantLock;

@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin extends Entity {

    @Unique
    private static final ReentrantLock async$lock = new ReentrantLock();

    public ItemEntityMixin(EntityType<?> type, Level world) {
        super(type, world);
    }

    @WrapMethod(method = "mergeWithNeighbours()V")
    private void mergeWithNeighbours(Operation<Void> original) {
        synchronized (async$lock) {
            original.call();
        }
    }

    @Override
    public void move(MoverType type, Vec3 movement) {
        synchronized (async$lock) {
            super.move(type, movement);
        }
    }
}