package com.axalotl.async.mixin.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.AnimalMakeLove;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.Animal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.locks.ReentrantLock;

@Mixin(AnimalMakeLove.class)
public abstract class BreedTaskMixin {
    @Shadow
    protected abstract Animal getBreedTarget(Animal animal);

    @Unique
    private static final ReentrantLock lock = new ReentrantLock();

    @Inject(method = "canStillUse(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/animal/Animal;J)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ai/behavior/AnimalMakeLove;getBreedTarget(Lnet/minecraft/world/entity/animal/Animal;)Lnet/minecraft/world/entity/animal/Animal;"))
    private void shouldKeepRunning(ServerLevel serverWorld, Animal animalEntity, long l, CallbackInfoReturnable<Boolean> cir) {
        if (this.getBreedTarget(animalEntity) == null) {
            cir.cancel();
        }
    }

    @Inject(method = "tick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/animal/Animal;J)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ai/behavior/AnimalMakeLove;getBreedTarget(Lnet/minecraft/world/entity/animal/Animal;)Lnet/minecraft/world/entity/animal/Animal;"), cancellable = true)
    private void canStillUse(ServerLevel serverWorld, Animal animalEntity, long l, CallbackInfo ci) {
        if (this.getBreedTarget(animalEntity) == null) {
            ci.cancel();
        }
    }

    @Inject(method = "getBreedTarget", at = @At("HEAD"), cancellable = true)
    private void syncBreedTarget(Animal animal, CallbackInfoReturnable<Animal> cir) {
        synchronized (lock) {
            cir.setReturnValue((Animal) animal.getBrain().getMemory(MemoryModuleType.BREED_TARGET).orElse(null));
        }
    }
}
