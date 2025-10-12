package com.axalotl.async.common.mixin.entity;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.PlayTagWithOtherKids;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayTagWithOtherKids.class)
public class PlayTagWithOtherKidsMixin {

    @Inject(method = "whoAreYouChasing", at = @At("HEAD"), cancellable = true)
    private static void onWhoAreYouChasing(LivingEntity baby, CallbackInfoReturnable<LivingEntity> cir) {
        cir.setReturnValue(baby.getBrain().getMemory(MemoryModuleType.INTERACTION_TARGET).orElse(null));
    }
}