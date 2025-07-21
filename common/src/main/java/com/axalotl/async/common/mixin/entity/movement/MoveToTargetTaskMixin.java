package com.axalotl.async.common.mixin.entity.movement;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.MoveToTargetSink;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(MoveToTargetSink.class)
public class MoveToTargetTaskMixin {

    @Inject(method = "checkExtraStartConditions(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/Mob;)Z", at = @At("HEAD"), cancellable = true)
    private void cancelIfNoWalkTarget(ServerLevel level, Mob owner, CallbackInfoReturnable<Boolean> cir) {
        Brain<?> brain = owner.getBrain();
        Optional<WalkTarget> optional = brain.getMemory(MemoryModuleType.WALK_TARGET);
        if (optional.isEmpty()) {
            cir.cancel();
        }
    }
}
