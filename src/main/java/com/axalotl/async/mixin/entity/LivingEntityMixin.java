package com.axalotl.async.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Mixin(value = LivingEntity.class, priority = 1001)
public abstract class LivingEntityMixin extends Entity {
    @Mutable
    @Shadow
    @Final
    private Map<RegistryEntry<StatusEffect>, StatusEffectInstance> activeStatusEffects;
    @Unique
    private static final ReentrantLock lock = new ReentrantLock();

    public LivingEntityMixin(EntityType<?> type, World world) {
        super(type, world);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void init(EntityType<?> entityType, World world, CallbackInfo ci) {
        this.activeStatusEffects = new ConcurrentHashMap<>();
    }

    @WrapMethod(method = "onDeath")
    private synchronized void onDeath(DamageSource damageSource, Operation<Void> original) {
        original.call(damageSource);
    }

    @WrapMethod(method = "dropLoot")
    private synchronized void dropLoot(ServerWorld world, DamageSource damageSource, boolean causedByPlayer, Operation<Void> original) {
        original.call(world, damageSource, causedByPlayer);
    }

    @WrapMethod(method = "knockback")
    private void knockback(LivingEntity target, Operation<Void> original) {
        synchronized (lock) {
            original.call(target);
        }
    }

    @WrapMethod(method = "tickStatusEffects")
    private void tickStatusEffects(Operation<Void> original) {
        synchronized (lock) {
            original.call();
        }
    }

    @Inject(method = "isClimbing", at = @At("HEAD"), cancellable = true)
    private void isClimbing(CallbackInfoReturnable<Boolean> cir) {
        BlockState blockState = this.getBlockStateAtPos();
        if (blockState == null) cir.setReturnValue(false);
    }
}
