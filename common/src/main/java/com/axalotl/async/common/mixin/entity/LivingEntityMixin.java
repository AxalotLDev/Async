package com.axalotl.async.common.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collections;
import java.util.List;
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

    @WrapMethod(method = "knockback")
    private synchronized void knockback(double strength, double x, double z, Operation<Void> original) {
        synchronized (async$lock) {
            original.call(strength, x, z);
        }
    }

    @WrapMethod(method = "tickEffects")
    private void tickStatusEffects(Operation<Void> original) {
        synchronized (async$lock) {
            original.call();
        }
    }

    @WrapOperation(
            method = "tickEffects",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/effect/MobEffectInstance;tickServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;Ljava/lang/Runnable;)Z"
            )
    )
    private boolean wrapTickEffect(MobEffectInstance instance, ServerLevel level, LivingEntity entity, Runnable onEffectUpdated, Operation<Boolean> original) {
        if (instance != null) {
            return original.call(instance, level, entity, onEffectUpdated);
        } else {
            return false;
        }
    }

    @WrapOperation(
            method = "tickEffects",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/List;of(Ljava/lang/Object;)Ljava/util/List;"
            )
    )
    private List<?> wrapListOf(Object element, Operation<List<?>> original) {
        if (element == null) {
            return Collections.emptyList();
        }
        return original.call(element);
    }

    @WrapMethod(method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z")
    private boolean addEffect(MobEffectInstance effect, Entity source, Operation<Boolean> original) {
        synchronized (async$lock) {
            return original.call(effect, source);
        }
    }

    @WrapMethod(method = "removeEffect")
    private boolean removeEffect(Holder<MobEffect> effect, Operation<Boolean> original) {
        synchronized (async$lock) {
            return original.call(effect);
        }
    }

    @WrapMethod(method = "removeAllEffects")
    private boolean removeAllEffects(Operation<Boolean> original) {
        synchronized (async$lock) {
            return original.call();
        }
    }

    @Inject(method = "causeFallDamage", at = @At("HEAD"), cancellable = true)
    private void causeFallDamage(double fallDistance, float multiplier, DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        BlockPos pos = new BlockPos(Mth.floor(this.getX()), Mth.floor(this.getY()), Mth.floor(this.getZ()));
        BlockState currentBlock = this.level().getBlockState(pos);

        if (currentBlock.is(BlockTags.CLIMBABLE)) {
            cir.setReturnValue(false);
        }
    }
}