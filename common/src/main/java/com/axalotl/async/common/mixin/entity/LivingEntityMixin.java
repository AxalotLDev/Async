package com.axalotl.async.common.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(value = LivingEntity.class, priority = 1001)
public abstract class LivingEntityMixin extends Entity {

    @Shadow
    final private Map<Holder<MobEffect>, MobEffectInstance> activeEffects = new ConcurrentHashMap<>();

    @Unique
    private static final Object async$lock = new Object();

    public LivingEntityMixin(EntityType<?> type, Level world) {
        super(type, world);
    }

    @WrapMethod(method = "die")
    private synchronized void die(DamageSource damageSource, Operation<Void> original) {
        original.call(damageSource);
    }

    @WrapMethod(method = "dropFromLootTable(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;Z)V")
    private synchronized void dropFromLootTable(ServerLevel level, DamageSource damageSource, boolean playerKill, Operation<Void> original) {
        original.call(level, damageSource, playerKill);
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

    @Redirect(
            method = "tickEffects",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;"
            )
    )
    private Object async$safeGet(Map<?, ?> map, Object key) {
        Object value = map.get(key);
        if (value == null) {
            map.remove(key);
        }
        return value;
    }

    @Inject(method = "onClimbable", at = @At("HEAD"), cancellable = true)
    private void isClimbing(CallbackInfoReturnable<Boolean> cir) {
        BlockState blockState = this.getInBlockState();
        if (blockState == null) cir.setReturnValue(false);
    }
}
