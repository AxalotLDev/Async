package com.axalotl.async.common.mixin.entity.breed;

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

@Mixin(AnimalMakeLove.class)
public abstract class AnimalMakeLoveMixin {

    @Unique
    private static final Object async$lock = new Object();

    @Shadow
    protected abstract Animal getBreedTarget(Animal animal);

    @Inject(method = "tick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/animal/Animal;J)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ai/behavior/AnimalMakeLove;getBreedTarget(Lnet/minecraft/world/entity/animal/Animal;)Lnet/minecraft/world/entity/animal/Animal;"), cancellable = true)
    private void tick(ServerLevel level, Animal owner, long gameTime, CallbackInfo ci) {
        if (this.getBreedTarget(owner) == null) {
            ci.cancel();
        }
    }

    @Inject(method = "canStillUse(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/animal/Animal;J)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ai/behavior/AnimalMakeLove;getBreedTarget(Lnet/minecraft/world/entity/animal/Animal;)Lnet/minecraft/world/entity/animal/Animal;"), cancellable = true)
    private void canStillUse(ServerLevel level, Animal entity, long gameTime, CallbackInfoReturnable<Boolean> cir) {
        if (this.getBreedTarget(entity) == null) {
            cir.cancel();
        }
    }


    @Inject(method = "getBreedTarget", at = @At("HEAD"), cancellable = true)
    private void syncBreedTarget(Animal animal, CallbackInfoReturnable<Animal> cir) {
        synchronized (async$lock) {
            cir.setReturnValue((Animal) animal.getBrain().getMemory(MemoryModuleType.BREED_TARGET).orElse(null));
        }
    }
}
