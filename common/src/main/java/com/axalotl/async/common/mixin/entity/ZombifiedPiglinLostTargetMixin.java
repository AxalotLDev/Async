package com.axalotl.async.common.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.ZombifiedPiglin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ZombifiedPiglin.class)
public abstract class ZombifiedPiglinLostTargetMixin {
    @WrapOperation(method = "maybeAlertOthers", require = 1, allow = 1,
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/monster/zombie/ZombifiedPiglin;getTarget()Lnet/minecraft/world/entity/LivingEntity;"))
    private LivingEntity async$lastKnownTarget(ZombifiedPiglin piglin, Operation<LivingEntity> original) {
        LivingEntity target = original.call(piglin);
        return target != null ? target : piglin.getTargetUnchecked();
    }
}
