package com.axalotl.async.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.locks.ReentrantLock;

@Mixin(value = LivingEntity.class, priority = 1001)
public abstract class LivingEntityMixin extends Entity {
    @Unique
    private static final ReentrantLock lock = new ReentrantLock();

    public LivingEntityMixin(EntityType<?> type, Level world) {
        super(type, world);
    }

    @WrapMethod(method = "die")
    private synchronized void onDeath(DamageSource damageSource, Operation<Void> original) {
        original.call(damageSource);
    }

    @WrapMethod(method = "dropFromLootTable")
    private synchronized void dropLoot(DamageSource damageSource, boolean causedByPlayer, Operation<Void> original) {
        original.call(damageSource, causedByPlayer);
    }

    @WrapMethod(method = "blockedByShield")
    private void knockback(LivingEntity target, Operation<Void> original) {
        synchronized (lock) {
            original.call(target);
        }
    }

    @WrapMethod(method = "tickEffects")
    private void tickStatusEffects(Operation<Void> original) {
        synchronized (lock) {
            original.call();
        }
    }

    @Inject(method = "onClimbable", at = @At("HEAD"), cancellable = true)
    private void isClimbing(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }
}
