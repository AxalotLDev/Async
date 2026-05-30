package com.axalotl.async.common.mixin.entity.breed;

import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.animal.fox.Fox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(Fox.FoxBreedGoal.class)
public abstract class FoxBreedGoalMixin extends BreedGoal {

    public FoxBreedGoalMixin(Fox fox, double speedModifier) {
        super(fox, speedModifier);
    }

    @Unique
    private static final Map<String, Boolean> breedingPairs = new ConcurrentHashMap<>();

    @Inject(method = "start", at = @At("HEAD"))
    private void resetBreedingFlag(CallbackInfo ci) {
        breedingPairs.remove(getPairKey());
    }

    @Inject(method = "breed", at = @At("HEAD"), cancellable = true)
    private void preventDoubleBreed(CallbackInfo ci) {
        String pairKey = getPairKey();
        if (breedingPairs.putIfAbsent(pairKey, Boolean.TRUE) != null) {
            ci.cancel();
        }
    }

    @Unique
    private String getPairKey() {
        String s1 = this.animal.getUUID().toString();
        if (this.partner == null) return s1;
        String s2 = this.partner.getUUID().toString();
        return s1.compareTo(s2) <= 0 ? s1 + "|" + s2 : s2 + "|" + s1;
    }
}