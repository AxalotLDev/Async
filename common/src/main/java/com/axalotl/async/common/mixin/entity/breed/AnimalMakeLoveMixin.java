package com.axalotl.async.common.mixin.entity.breed;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.AnimalMakeLove;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.Animal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AnimalMakeLove.class)
public abstract class AnimalMakeLoveMixin {

    @Shadow
    protected abstract Animal getBreedTarget(Animal body);

    @Inject(method = "tick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/animal/Animal;J)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ai/behavior/AnimalMakeLove;getBreedTarget(Lnet/minecraft/world/entity/animal/Animal;)Lnet/minecraft/world/entity/animal/Animal;"), cancellable = true)
    private void tick(ServerLevel level, Animal body, long timestamp, CallbackInfo ci) {
        if (this.getBreedTarget(body) == null) {
            ci.cancel();
        }
    }

    @Inject(method = "canStillUse(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/animal/Animal;J)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ai/behavior/AnimalMakeLove;getBreedTarget(Lnet/minecraft/world/entity/animal/Animal;)Lnet/minecraft/world/entity/animal/Animal;"), cancellable = true)
    private void canStillUse(ServerLevel level, Animal body, long timestamp, CallbackInfoReturnable<Boolean> cir) {
        if (this.getBreedTarget(body) == null) {
            cir.cancel();
        }
    }

    @Inject(method = "getBreedTarget", at = @At("HEAD"), cancellable = true)
    private void syncBreedTarget(Animal body, CallbackInfoReturnable<Animal> cir) {
        synchronized (body) {
            cir.setReturnValue((Animal) body.getBrain().getMemory(MemoryModuleType.BREED_TARGET).orElse(null));
        }
    }
}
