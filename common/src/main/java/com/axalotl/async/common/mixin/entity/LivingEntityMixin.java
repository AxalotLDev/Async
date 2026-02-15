package com.axalotl.async.common.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Mixin(value = LivingEntity.class, priority = 1001)
public abstract class LivingEntityMixin extends Entity {

    @Shadow
    final private Map<Holder<MobEffect>, MobEffectInstance> activeEffects = new ConcurrentHashMap<>();

    @Shadow
    protected abstract void onEffectUpdated(MobEffectInstance effect, boolean reapply, Entity source);

    @Shadow
    protected abstract void onEffectsRemoved(java.util.Collection<MobEffectInstance> effects);

    @Unique
    private final AtomicBoolean async$dying = new AtomicBoolean(false);

    public LivingEntityMixin(EntityType<?> type, Level world) {
        super(type, world);
    }

    @WrapMethod(method = "die")
    private void die(DamageSource damageSource, Operation<Void> original) {
        if (async$dying.compareAndSet(false, true)) {
            original.call(damageSource);
        }
    }

    @WrapMethod(method = "knockback")
    private synchronized void knockback(double strength, double x, double z, Operation<Void> original) {
        original.call(strength, x, z);
    }

    @WrapMethod(method = "tickEffects")
    private void tickStatusEffects(Operation<Void> original) {
        if (this.level() instanceof ServerLevel serverlevel) {
            List<Holder<MobEffect>> effectsToTick = new ArrayList<>(this.activeEffects.keySet());

            for (Holder<MobEffect> holder : effectsToTick) {
                MobEffectInstance mobeffectinstance = this.activeEffects.get(holder);

                if (mobeffectinstance != null) {
                    if (!mobeffectinstance.tickServer(serverlevel, (LivingEntity) (Object) this,
                            () -> this.onEffectUpdated(mobeffectinstance, true, null))) {
                        this.activeEffects.remove(holder);
                        this.onEffectsRemoved(List.of(mobeffectinstance));
                    } else if (mobeffectinstance.getDuration() % 600 == 0) {
                        this.onEffectUpdated(mobeffectinstance, false, null);
                    }
                }
            }
        } else {
            original.call();
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