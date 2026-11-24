package com.axalotl.async.common.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.bee.Bee;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Bee.class)
public abstract class BeeLostTargetMixin {
    @WrapOperation(method = "aiStep", require = 1, allow = 1,
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/animal/bee/Bee;getTarget()Lnet/minecraft/world/entity/LivingEntity;",
                    ordinal = 1))
    private LivingEntity async$lastKnownTarget(Bee bee, Operation<LivingEntity> original) {
        LivingEntity target = original.call(bee);
        return target != null ? target : bee.getTargetUnchecked();
    }
}
