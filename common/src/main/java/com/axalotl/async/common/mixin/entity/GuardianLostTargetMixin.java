package com.axalotl.async.common.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Guardian;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net.minecraft.world.entity.monster.Guardian$GuardianAttackGoal")
public abstract class GuardianLostTargetMixin {
    @WrapOperation(method = "canContinueToUse", require = 1, allow = 1,
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/monster/Guardian;getTarget()Lnet/minecraft/world/entity/LivingEntity;",
                    ordinal = 1))
    private LivingEntity async$lastKnownTarget(Guardian guardian, Operation<LivingEntity> original) {
        LivingEntity target = original.call(guardian);
        return target != null ? target : guardian.getTargetUnchecked();
    }
}
