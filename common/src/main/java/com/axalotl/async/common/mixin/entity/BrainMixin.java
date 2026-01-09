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
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe Brain implementation using snapshot approach.
 *
 * All reads during tick go through thread-safe snapshot.
 * Unregistered memory access returns safe defaults instead of throwing.
 */
@Mixin(value = Brain.class, priority = 1500)
public class BrainMixin<E extends LivingEntity> {

    @Shadow
    @Final
    private Map<MemoryModuleType<?>, Optional<? extends ExpirableValue<?>>> memories;

    @Unique
    private volatile Map<MemoryModuleType<?>, Optional<? extends ExpirableValue<?>>> async$cachedSnapshot;

    @Unique
    private volatile boolean async$needsRebuild = true;

    @Unique
    private final ThreadLocal<Boolean> async$inTick = ThreadLocal.withInitial(() -> false);

    @Unique
    private final Object async$writeLock = new Object();

    @Inject(method = "tick", at = @At("HEAD"))
    private void async$takeSnapshot(ServerLevel level, E entity, CallbackInfo ci) {
        if (AsyncConfig.disabled) {
            return;
        }

        if (async$needsRebuild || async$cachedSnapshot == null) {
            synchronized (async$writeLock) {
                if (async$needsRebuild || async$cachedSnapshot == null) {
                    async$cachedSnapshot = new ConcurrentHashMap<>(this.memories);
                    async$needsRebuild = false;
                }
            }
        }
        async$inTick.set(true);
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void async$clearSnapshot(ServerLevel level, E entity, CallbackInfo ci) {
        async$inTick.set(false);
    }

    /**
     * Thread-safe read from snapshot.
     * Returns Optional.empty() for unregistered memories (instead of throwing).
     */
    @Inject(method = "getMemory", at = @At("HEAD"), cancellable = true)
    private <U> void async$getMemoryFromSnapshot(MemoryModuleType<@NotNull U> type, CallbackInfoReturnable<Optional<U>> cir) {
        if (AsyncConfig.disabled) {
            return;
        }

        Map<MemoryModuleType<?>, Optional<? extends ExpirableValue<?>>> snapshot = async$cachedSnapshot;
        if (async$inTick.get() && snapshot != null) {
            Optional<? extends ExpirableValue<?>> value = snapshot.get(type);

            // Unregistered memory - return empty (safe for async, vanilla would throw)
            if (value == null) {
                cir.setReturnValue(Optional.empty());
                return;
            }

            @SuppressWarnings("unchecked")
            Optional<U> result = (Optional<U>) value.map(ExpirableValue::getValue);
            cir.setReturnValue(result);
        }
    }

    @Inject(method = "hasMemoryValue", at = @At("HEAD"), cancellable = true)
    private void async$hasMemoryValueFromSnapshot(MemoryModuleType<?> type, CallbackInfoReturnable<Boolean> cir) {
        if (AsyncConfig.disabled) {
            return;
        }

        Map<MemoryModuleType<?>, Optional<? extends ExpirableValue<?>>> snapshot = async$cachedSnapshot;
        if (async$inTick.get() && snapshot != null) {
            Optional<? extends ExpirableValue<?>> value = snapshot.get(type);

            // Unregistered = no value
            if (value == null) {
                cir.setReturnValue(false);
                return;
            }

            cir.setReturnValue(value.isPresent());
        }
    }

    @Inject(method = "checkMemory", at = @At("HEAD"), cancellable = true)
    private void async$checkMemoryFromSnapshot(MemoryModuleType<?> type, MemoryStatus status, CallbackInfoReturnable<Boolean> cir) {
        if (AsyncConfig.disabled) {
            return;
        }

        Map<MemoryModuleType<?>, Optional<? extends ExpirableValue<?>>> snapshot = async$cachedSnapshot;
        if (async$inTick.get() && snapshot != null) {
            Optional<? extends ExpirableValue<?>> value = snapshot.get(type);

            // Unregistered = false for all statuses (vanilla behavior)
            if (value == null) {
                cir.setReturnValue(false);
                return;
            }

            boolean result = switch (status) {
                case REGISTERED -> true;
                case VALUE_PRESENT -> value.isPresent();
                case VALUE_ABSENT -> value.isEmpty();
            };
            cir.setReturnValue(result);
        }
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    @WrapMethod(method = "setMemoryInternal")
    private <U> void async$setMemoryInternal(MemoryModuleType<@NotNull U> memoryType,
                                             Optional<? extends ExpirableValue<?>> memory,
                                             Operation<Void> original) {
        if (AsyncConfig.disabled) {
            original.call(memoryType, memory);
            return;
        }

        synchronized (async$writeLock) {
            original.call(memoryType, memory);

            Map<MemoryModuleType<?>, Optional<? extends ExpirableValue<?>>> snapshot = async$cachedSnapshot;
            if (snapshot != null && snapshot.containsKey(memoryType)) {
                snapshot.put(memoryType, memory);
            }
        }
    }

    @WrapMethod(method = "clearMemories")
    private void async$clearMemories(Operation<Void> original) {
        if (AsyncConfig.disabled) {
            original.call();
            return;
        }

        synchronized (async$writeLock) {
            original.call();
            async$needsRebuild = true;
        }
    }
}