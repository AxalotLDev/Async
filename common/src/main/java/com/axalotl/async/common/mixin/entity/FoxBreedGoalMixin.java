package com.axalotl.async.common.mixin.entity;

import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Fox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.atomic.AtomicBoolean;

@Mixin(Fox.FoxBreedGoal.class)
public class FoxBreedGoalMixin extends BreedGoal {

    public FoxBreedGoalMixin(Animal animal, double speedModifier) {
        super(animal, speedModifier);
    }

    @Unique
    private final AtomicBoolean async$hasBred = new AtomicBoolean(false);

    @Inject(method = "breed", at = @At("HEAD"), cancellable = true)
    private void asyncSafeBreed(CallbackInfo ci) {
        if (!async$hasBred.compareAndSet(false, true)) {
            ci.cancel();
        }
    }
}