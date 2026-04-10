package com.axalotl.async.common.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.ai.behavior.TransportItemsBetweenContainers;
import net.minecraft.world.entity.animal.golem.CopperGolemAi;
import net.minecraft.world.entity.animal.golem.CopperGolemState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.function.Predicate;

@Mixin(CopperGolemAi.class)
public class CopperGolemAiMixin {
    @Unique
    private static final Object lock = new Object();

    @WrapMethod(method = "shouldQueueForTarget")
    private static Predicate<TransportItemsBetweenContainers.TransportItemTarget> shouldQueueForTarget(Operation<Predicate<TransportItemsBetweenContainers.TransportItemTarget>> original) {
        synchronized (lock) {
            return original.call();
        }
    }

    @WrapMethod(method = "onReachedTargetInteraction")
    private static TransportItemsBetweenContainers.OnTargetReachedInteraction onReachedTargetInteraction(CopperGolemState state, SoundEvent sound, Operation<TransportItemsBetweenContainers.OnTargetReachedInteraction> original) {
        synchronized (lock) {
            return original.call(state, sound);
        }
    }
}