package com.axalotl.async.common.mixin.entity;

import com.axalotl.async.common.parallelised.ConcurrentCollections;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.ExpirableValue;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.Optional;

@Mixin(Brain.class)
public class BrainMixin {

    @Shadow
    @Final
    @Mutable
    private Map<MemoryModuleType<?>, Optional<? extends ExpirableValue<?>>> memories;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onInit(CallbackInfo ci) {
        this.memories = ConcurrentCollections.newHashMap();
    }

    @Inject(method = "getMemory", at = @At("HEAD"), cancellable = true)
    private <U> void cancelIfMemoryNullOrEmpty(MemoryModuleType<U> type, CallbackInfoReturnable<Optional<U>> cir) {
        Optional<? extends ExpirableValue<?>> optional = this.memories.get(type);
        if (optional.orElse(null) == null) {
            cir.cancel();
        }
    }
}