package com.axalotl.async.common.mixin.entity;

import com.axalotl.async.common.config.AsyncConfig;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.ExpirableValue;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Mixin(value = Brain.class, priority = 1500)
public class BrainMixin<E extends LivingEntity> {

    @Shadow
    @Final
    private Map<MemoryModuleType<?>, Optional<? extends ExpirableValue<?>>> memories;

    @Unique
    private Map<MemoryModuleType<?>, Optional<? extends ExpirableValue<?>>> async$snapshot;

    @Unique
    private volatile boolean async$inTick;

    @Inject(method = "tick", at = @At("HEAD"))
    private void async$buildSnapshot(ServerLevel level, E entity, CallbackInfo ci) {
        if (AsyncConfig.disabled) return;
        async$snapshot = new HashMap<>(this.memories);
        async$inTick = true;
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void async$endTick(ServerLevel level, E entity, CallbackInfo ci) {
        async$inTick = false;
    }

    @Inject(method = "getMemory", at = @At("HEAD"), cancellable = true)
    private <U> void async$getMemory(MemoryModuleType<U> type, CallbackInfoReturnable<Optional<U>> cir) {
        if (AsyncConfig.disabled || !async$inTick) return;

        Map<MemoryModuleType<?>, Optional<? extends ExpirableValue<?>>> snapshot = async$snapshot;
        if (snapshot == null) return;

        Optional<? extends ExpirableValue<?>> value = snapshot.get(type);
        if (value == null) {
            cir.setReturnValue(Optional.empty());
            return;
        }

        @SuppressWarnings("unchecked")
        Optional<U> result = (Optional<U>) value.map(ExpirableValue::getValue);
        cir.setReturnValue(result);
    }

    @Inject(method = "hasMemoryValue", at = @At("HEAD"), cancellable = true)
    private void async$hasMemoryValue(MemoryModuleType<?> type, CallbackInfoReturnable<Boolean> cir) {
        if (AsyncConfig.disabled || !async$inTick) return;

        Map<MemoryModuleType<?>, Optional<? extends ExpirableValue<?>>> snapshot = async$snapshot;
        if (snapshot == null) return;

        Optional<? extends ExpirableValue<?>> value = snapshot.get(type);
        cir.setReturnValue(value != null && value.isPresent());
    }

    @Inject(method = "checkMemory", at = @At("HEAD"), cancellable = true)
    private void async$checkMemory(MemoryModuleType<?> type, MemoryStatus status, CallbackInfoReturnable<Boolean> cir) {
        if (AsyncConfig.disabled || !async$inTick) return;

        Map<MemoryModuleType<?>, Optional<? extends ExpirableValue<?>>> snapshot = async$snapshot;
        if (snapshot == null) return;

        Optional<? extends ExpirableValue<?>> value = snapshot.get(type);

        boolean result = switch (status) {
            case REGISTERED, VALUE_PRESENT -> value != null && value.isPresent();
            case VALUE_ABSENT -> value == null || value.isEmpty();
        };

        cir.setReturnValue(result);
    }

    @WrapMethod(method = "setMemoryInternal")
    private <U> void async$setMemory(
            MemoryModuleType<U> memoryType,
            Optional<? extends ExpirableValue<?>> memory,
            Operation<Void> original
    ) {
        if (AsyncConfig.disabled) {
            original.call(memoryType, memory);
            return;
        }

        synchronized (this) {
            original.call(memoryType, memory);
        }
    }
}