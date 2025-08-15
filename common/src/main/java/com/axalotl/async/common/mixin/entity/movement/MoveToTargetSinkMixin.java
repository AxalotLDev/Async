package com.axalotl.async.common.mixin.entity.movement;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.MoveToTargetSink;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.level.pathfinder.Path;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;


@Mixin(MoveToTargetSink.class)
public class MoveToTargetSinkMixin {

    @Shadow @Nullable
    private Path path;

    @Inject(method = "tick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/Mob;J)V", at = @At("HEAD"), cancellable = true)
    private void skipTickIfNoWalkTarget(ServerLevel level, Mob owner, long gameTime, CallbackInfo ci) {
        if (owner.getBrain().getMemory(MemoryModuleType.WALK_TARGET).isEmpty()) {
            ci.cancel();
        }
    }

    @Inject(method = "canStillUse(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/Mob;J)Z", at = @At("HEAD"), cancellable = true)
    private void skipIfNoWalkTarget(ServerLevel level, Mob entity, long gameTime, CallbackInfoReturnable<Boolean> cir) {
        if (entity.getBrain().getMemory(MemoryModuleType.WALK_TARGET).isEmpty()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "stop(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/Mob;J)V", at = @At("HEAD"))
    private void clearIfNoWalkTarget(ServerLevel level, Mob entity, long gameTime, CallbackInfo ci) {
        if (entity.getBrain().getMemory(MemoryModuleType.WALK_TARGET).isEmpty()) {
            entity.getNavigation().stop();
            entity.getBrain().eraseMemory(MemoryModuleType.PATH);
            this.path = null;
        }
    }
}