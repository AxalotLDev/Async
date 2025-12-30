package com.axalotl.async.common.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.ExpirableValue;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Thread-safe Brain implementation using snapshot approach.
 *
 * Problem: Vanilla code does hasMemoryValue() then getMemory().get() -
 * another thread can clear memory between these calls causing NoSuchElementException.
 *
 * Solution: At tick start, snapshot all memories. During tick, getMemory() reads from
 * snapshot. This gives consistent view without blocking other entities.
 *
 * Priority 1500 to apply after Lithium (1010).
 */
@Mixin(value = Brain.class, priority = 1500)
public class BrainMixin<E extends LivingEntity> {

    @Shadow
    @Final
    private Map<MemoryModuleType<?>, Optional<? extends ExpirableValue<?>>> memories;

    /**
     * ThreadLocal snapshot - each thread (entity tick) gets its own snapshot.
     * Null when not inside tick().
     */
    @Unique
    private final ThreadLocal<Map<MemoryModuleType<?>, Optional<? extends ExpirableValue<?>>>> async$snapshot = new ThreadLocal<>();

    /**
     * Lock for write operations (setMemory, clearMemories).
     * Reads go through snapshot, so don't need lock.
     */
    @Unique
    private final Object async$writeLock = new Object();

    /**
     * Take snapshot at tick start.
     */
    @Inject(method = "tick", at = @At("HEAD"))
    private void async$takeSnapshot(ServerLevel level, E entity, CallbackInfo ci) {
        // Create snapshot of current memory state
        synchronized (async$writeLock) {
            async$snapshot.set(new HashMap<>(this.memories));
        }
    }

    /**
     * Clear snapshot after tick completes.
     */
    @Inject(method = "tick", at = @At("RETURN"))
    private void async$clearSnapshot(ServerLevel level, E entity, CallbackInfo ci) {
        async$snapshot.remove();
    }

    /**
     * Redirect getMemory to read from snapshot during tick.
     */
    @Inject(method = "getMemory", at = @At("HEAD"), cancellable = true)
    private <U> void async$getMemoryFromSnapshot(MemoryModuleType<U> type, CallbackInfoReturnable<Optional<U>> cir) {
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
     * Redirect hasMemoryValue to check snapshot during tick.
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
     * Redirect checkMemory to use snapshot during tick.
     */
    @Inject(method = "checkMemory", at = @At("HEAD"), cancellable = true)
    private void async$checkMemoryFromSnapshot(MemoryModuleType<?> type,
                                               net.minecraft.world.entity.ai.memory.MemoryStatus status,
                                               CallbackInfoReturnable<Boolean> cir) {
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

    @WrapMethod(method = "setMemoryInternal")
    private <U> void async$setMemoryInternal(MemoryModuleType<U> memoryType,
                                             Optional<? extends ExpirableValue<?>> memory,
                                             Operation<Void> original) {
        synchronized (async$writeLock) {
            original.call(memoryType, memory);
        }
    }

    @WrapMethod(method = "clearMemories")
    private void async$clearMemories(Operation<Void> original) {
        synchronized (async$writeLock) {
            original.call();
        }
    }
}