package com.axalotl.async.common.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.SpearUseGoal;
import net.minecraft.world.entity.monster.Monster;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(SpearUseGoal.class)
public abstract class SpearUseGoalLostTargetMixin {
    @WrapOperation(method = "tick", require = 1, allow = 1,
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/monster/Monster;getTarget()Lnet/minecraft/world/entity/LivingEntity;"))
    private LivingEntity async$lastKnownTarget(Monster mob, Operation<LivingEntity> original) {
        LivingEntity target = original.call(mob);
        return target != null ? target : mob.getTargetUnchecked();
    }
}
