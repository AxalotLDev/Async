package com.axalotl.async.common.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
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

/**
 * Thread-safe Brain implementation using optimized snapshot approach.

 * Optimization: Reuse snapshot if memories haven't changed since last tick.
 * Most ticks don't modify memory, so we avoid expensive map copy.

 * Uses Reference2ObjectOpenHashMap (same as Lithium's collections.brain)
 * for faster reference-based key lookups.

 * Requires disabling Lithium's mixin.ai.task.memory_change_counting in fabric.mod.json:
 * "custom": { "lithium:options": { "mixin.ai.task.memory_change_counting": false } }
 */
@Mixin(value = Brain.class, priority = 1500)
public class BrainMixin<E extends LivingEntity> {

    @Shadow
    @Final
    private Map<MemoryModuleType<?>, Optional<? extends ExpirableValue<?>>> memories;

    /**
     * Cached snapshot - reused if memories haven't changed.
     * Uses Reference2ObjectOpenHashMap for faster lookups (same as Lithium).
     */
    @Unique
    private volatile Map<MemoryModuleType<?>, Optional<? extends ExpirableValue<?>>> async$cachedSnapshot;

    /**
     * Flag indicating memories have changed and snapshot needs rebuild.
     */
    @Unique
    private volatile boolean async$memoriesChanged = true;

    /**
     * ThreadLocal snapshot reference for current tick.
     */
    @Unique
    private final ThreadLocal<Map<MemoryModuleType<?>, Optional<? extends ExpirableValue<?>>>> async$snapshot = new ThreadLocal<>();

    /**
     * Lock for write operations.
     */
    @Unique
    private final Object async$writeLock = new Object();

    /**
     * Take snapshot at tick start.
     * Optimization: only create new snapshot if memories changed.
     */
    @Inject(method = "tick", at = @At("HEAD"))
    private void async$takeSnapshot(ServerLevel level, E entity, CallbackInfo ci) {
        if (async$memoriesChanged) {
            synchronized (async$writeLock) {
                if (async$memoriesChanged) {
                    // Use Reference2ObjectOpenHashMap for faster reference-based lookups
                    async$cachedSnapshot = new Reference2ObjectOpenHashMap<>(this.memories);
                    async$memoriesChanged = false;
                }
            }
        }
        async$snapshot.set(async$cachedSnapshot);
    }

    /**
     * Clear snapshot reference after tick.
     */
    @Inject(method = "tick", at = @At("RETURN"))
    private void async$clearSnapshot(ServerLevel level, E entity, CallbackInfo ci) {
        async$snapshot.remove();
    }

    /**
     * Read from snapshot during tick.
     */
    @Inject(method = "getMemory", at = @At("HEAD"), cancellable = true)
    private <U> void async$getMemoryFromSnapshot(MemoryModuleType<@NotNull U> type, CallbackInfoReturnable<Optional<U>> cir) {
        Map<MemoryModuleType<?>, Optional<? extends ExpirableValue<?>>> snapshot = async$snapshot.get();
        if (snapshot != null) {
            // During tick - read from snapshot for consistency
            Optional<? extends ExpirableValue<?>> value = snapshot.get(type);
            if (value == null) {
                cir.setReturnValue(Optional.empty());
            } else {
                @SuppressWarnings("unchecked")
                Optional<U> result = (Optional<U>) value.map(ExpirableValue::getValue);
                cir.setReturnValue(result);
            }
        }
        // Outside tick - let vanilla handle it
    }

    /**
     * Check snapshot during tick.
     */
    @Inject(method = "hasMemoryValue", at = @At("HEAD"), cancellable = true)
    private void async$hasMemoryValueFromSnapshot(MemoryModuleType<?> type, CallbackInfoReturnable<Boolean> cir) {
        Map<MemoryModuleType<?>, Optional<? extends ExpirableValue<?>>> snapshot = async$snapshot.get();
        if (snapshot != null) {
            // During tick - check snapshot
            Optional<? extends ExpirableValue<?>> value = snapshot.get(type);
            cir.setReturnValue(value != null && value.isPresent());
        }
        // Outside tick - let vanilla handle it
    }

    /**
     * Use snapshot for checkMemory during tick.
     */
    @Inject(method = "checkMemory", at = @At("HEAD"), cancellable = true)
    private void async$checkMemoryFromSnapshot(MemoryModuleType<?> type, MemoryStatus status, CallbackInfoReturnable<Boolean> cir) {
        Map<MemoryModuleType<?>, Optional<? extends ExpirableValue<?>>> snapshot = async$snapshot.get();
        if (snapshot != null) {
            Optional<? extends ExpirableValue<?>> value = snapshot.get(type);
            boolean hasValue = value != null && value.isPresent();

            boolean result = switch (status) {
                case VALUE_PRESENT -> hasValue;
                case VALUE_ABSENT -> !hasValue;
                case REGISTERED -> value != null; // key exists in map
            };
            cir.setReturnValue(result);
        }
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    @WrapMethod(method = "setMemoryInternal")
    private <U> void async$setMemoryInternal(MemoryModuleType<@NotNull U> memoryType,
                                             Optional<? extends ExpirableValue<?>> memory,
                                             Operation<Void> original) {
        synchronized (async$writeLock) {
            async$memoriesChanged = true;
            original.call(memoryType, memory);
        }
    }

    @WrapMethod(method = "clearMemories")
    private void async$clearMemories(Operation<Void> original) {
        synchronized (async$writeLock) {
            async$memoriesChanged = true;
            original.call();
        }
    }
}