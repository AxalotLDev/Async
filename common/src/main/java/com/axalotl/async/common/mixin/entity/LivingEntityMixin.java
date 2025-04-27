package com.axalotl.async.common.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.concurrent.locks.ReentrantLock;

@Mixin(value = LivingEntity.class, priority = 1001)
public abstract class LivingEntityMixin extends Entity {

    @Unique
    private static final ReentrantLock async$lock = new ReentrantLock();

    public LivingEntityMixin(EntityType<?> type, Level world) {
        super(type, world);
    }

    @WrapMethod(method = "die")
    private synchronized void die(DamageSource damageSource, Operation<Void> original) {
        original.call(damageSource);
    }

    @WrapMethod(method = "dropFromLootTable")
    private synchronized void dropFromLootTable(DamageSource damageSource, boolean causedByPlayer, Operation<Void> original) {
        original.call(damageSource, causedByPlayer);
    }

    @WrapMethod(method = "blockedByShield")
    private synchronized void knockback(LivingEntity defender, Operation<Void> original) {
        synchronized (async$lock) {
            original.call(defender);
        }
    }

    @WrapMethod(method = "tickEffects")
    private void tickStatusEffects(Operation<Void> original) {
        synchronized (async$lock) {
            original.call();
        }
    }

    @WrapMethod(method = "refreshDirtyAttributes")
    private void updateAttributes(Operation<Void> original) {
        synchronized (async$lock) {
            original.call();
        }
    }

//    @WrapMethod(method = "take")
//    private void take(Entity entity, int amount, Operation<Void> original) {
//        synchronized (async$lock) {
//            original.call(entity, amount);
//        }
//    }

    @WrapMethod(method = "onItemPickup")
    private void onItemPickup(ItemEntity itemEntity, Operation<Void> original) {
        async$lock.lock();
        try {
            if (!itemEntity.isRemoved()) {
                itemEntity.setRemoved(RemovalReason.KILLED);
                original.call(itemEntity);
            }
        } finally {
            async$lock.unlock();
        }
    }
}
