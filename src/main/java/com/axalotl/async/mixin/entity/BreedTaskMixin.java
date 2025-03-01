package com.axalotl.async.mixin.entity;

import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.ai.brain.task.BreedTask;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.locks.ReentrantLock;

@Mixin(BreedTask.class)
public abstract class BreedTaskMixin {
    @Shadow
    protected abstract AnimalEntity getBreedTarget(AnimalEntity animal);

    @Unique
    private static final ReentrantLock lock = new ReentrantLock();

    @Inject(method = "shouldKeepRunning(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/entity/passive/AnimalEntity;J)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/ai/brain/task/BreedTask;getBreedTarget(Lnet/minecraft/entity/passive/AnimalEntity;)Lnet/minecraft/entity/passive/AnimalEntity;"))
    private void shouldKeepRunning(ServerWorld serverWorld, AnimalEntity animalEntity, long l, CallbackInfoReturnable<Boolean> cir) {
        if (this.getBreedTarget(animalEntity) == null) {
            cir.cancel();
        }
    }

    @Inject(method = "keepRunning(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/entity/passive/AnimalEntity;J)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/ai/brain/task/BreedTask;getBreedTarget(Lnet/minecraft/entity/passive/AnimalEntity;)Lnet/minecraft/entity/passive/AnimalEntity;"), cancellable = true)
    private void canStillUse(ServerWorld serverWorld, AnimalEntity animalEntity, long l, CallbackInfo ci) {
        if (this.getBreedTarget(animalEntity) == null) {
            ci.cancel();
        }
    }

    @Inject(method = "getBreedTarget", at = @At("HEAD"), cancellable = true)
    private void syncBreedTarget(AnimalEntity animal, CallbackInfoReturnable<AnimalEntity> cir) {
        synchronized (lock) {
            cir.setReturnValue((AnimalEntity) animal.getBrain().getOptionalRegisteredMemory(MemoryModuleType.BREED_TARGET).orElse(null));
        }
    }
}
