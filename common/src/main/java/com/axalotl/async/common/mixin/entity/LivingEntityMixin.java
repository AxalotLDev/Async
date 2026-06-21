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
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(value = LivingEntity.class)
public abstract class LivingEntityMixin extends Entity {

    @Shadow
    final private Map<Holder<MobEffect>, MobEffectInstance> activeEffects = new ConcurrentHashMap<>();

    public LivingEntityMixin(EntityType<?> type, Level world) {
        super(type, world);
    }

    @WrapMethod(method = "die")
    private synchronized void die(DamageSource source, Operation<Void> original) {
        original.call(source);
    }

    @WrapMethod(method = "dropFromLootTable(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;Z)V")
    private synchronized void dropFromLootTable(ServerLevel level, DamageSource source, boolean playerKilled, Operation<Void> original) {
        original.call(level, source, playerKilled);
    }

    @WrapMethod(method = "knockback(DDDLnet/minecraft/world/damagesource/DamageSource;FZ)V")
    private void knockback(double power, double xd, double zd, DamageSource source, float damage, boolean comesFromEffect, Operation<Void> original) {
        synchronized (this) {
            original.call(power, xd, zd, source, damage, comesFromEffect);
        }
    }

    @WrapMethod(method = "tickEffects")
    private void tickStatusEffects(Operation<Void> original) {
        synchronized (this) {
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
    private boolean wrapTickEffect(MobEffectInstance instance, ServerLevel serverLevel, LivingEntity target, Runnable onEffectUpdate, Operation<Boolean> original) {
        return instance != null ? original.call(instance, serverLevel, target, onEffectUpdate) : false;
    }

    @WrapOperation(
            method = "tickEffects",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/List;of(Ljava/lang/Object;)Ljava/util/List;"
            )
    )
    private List<?> wrapListOf(Object e1, Operation<List<?>> original) {
        return e1 != null ? original.call(e1) : Collections.emptyList();
    }

    @WrapMethod(method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z")
    private boolean addEffect(MobEffectInstance newEffect, Entity source, Operation<Boolean> original) {
        synchronized (this) {
            return newEffect != null ? original.call(newEffect, source) : false;
        }
    }

    @WrapMethod(method = "removeEffect")
    private boolean removeEffect(Holder<MobEffect> effect, Operation<Boolean> original) {
        synchronized (this) {
            return effect != null ? original.call(effect) : false;
        }
    }

    @WrapMethod(method = "hasEffect")
    public boolean hasEffect(Holder<MobEffect> effect, Operation<Boolean> original) {
        return effect != null ? original.call(effect) : false;
    }

    @WrapMethod(method = "removeAllEffects")
    private boolean removeAllEffects(Operation<Boolean> original) {
        synchronized (this) {
            return original.call();
        }
    }

    @Inject(method = "causeFallDamage", at = @At("HEAD"), cancellable = true)
    private void causeFallDamage(double fallDistance, float damageModifier, DamageSource damageSource, CallbackInfoReturnable<Boolean> cir) {
        BlockPos pos = new BlockPos(Mth.floor(this.getX()), Mth.floor(this.getY()), Mth.floor(this.getZ()));
        BlockState currentBlock = this.level().getBlockState(pos);

        if (currentBlock.is(BlockTags.CLIMBABLE)) {
            cir.setReturnValue(false);
        }
    }

    @Redirect(method = "refreshDirtyAttributes", at = @At(value = "INVOKE", target = "Ljava/util/Set;iterator()Ljava/util/Iterator;"))
    private Iterator<AttributeInstance> snapshotIterator(Set<AttributeInstance> set) {
        Set<AttributeInstance> snapshot;
        synchronized (this) {
            snapshot = new HashSet<>(set);
            set.clear();
        }
        return snapshot.iterator();
    }
}