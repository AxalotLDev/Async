package com.axalotl.async.common.mixin.entity;

import com.axalotl.async.common.config.AsyncConfig;
import com.google.common.collect.ImmutableList;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.memory.ExpirableValue;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.schedule.Activity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.Supplier;

@Mixin(value = Brain.class, priority = 1500)
public class BrainMixin<E extends LivingEntity> {

    @Shadow
    @Final
    private Map<MemoryModuleType<?>, Optional<? extends ExpirableValue<?>>> memories;

    @Shadow
    @Final
    @Mutable
    private Map<Integer, Map<Activity, Set<BehaviorControl<? super E>>>> availableBehaviorsByPriority;

    @Shadow
    @Final
    @Mutable
    private Set<Activity> activeActivities;

    @Mutable
    @Shadow
    private Set<Activity> coreActivities;

    @Unique
    private volatile Map<MemoryModuleType<?>, Optional<? extends ExpirableValue<?>>> async$snapshot;

    @Unique
    private volatile boolean async$needsRebuild = true;

    @Unique
    private volatile boolean async$inTick;

    @Unique
    private final Object async$writeLock = new Object();

    @Inject(method = "<init>", at = @At("RETURN"))
    private void async$makeConcurrent(Collection<?> memoryModuleTypes, Collection<?> sensorTypes,
                                      ImmutableList<?> memoryValues, Supplier<?> codec, CallbackInfo ci) {
        if (AsyncConfig.disabled) return;

        this.availableBehaviorsByPriority = new ConcurrentSkipListMap<>(this.availableBehaviorsByPriority);

        Set<Activity> newActive = Collections.newSetFromMap(new ConcurrentHashMap<>());
        newActive.addAll(this.activeActivities);
        this.activeActivities = newActive;

        Set<Activity> newCore = Collections.newSetFromMap(new ConcurrentHashMap<>());
        newCore.addAll(this.coreActivities);
        this.coreActivities = newCore;
    }

    @Redirect(method = "addActivityAndRemoveMemoriesWhenStopped", at = @At(value = "INVOKE", target = "Ljava/util/Map;computeIfAbsent(Ljava/lang/Object;Ljava/util/function/Function;)Ljava/lang/Object;", ordinal = 0))
    private Object async$concurrentInnerMap(Map<Object, Object> map, Object key, java.util.function.Function<Object, Object> mappingFunction) {
        if (AsyncConfig.disabled) return map.computeIfAbsent(key, mappingFunction);
        return map.computeIfAbsent(key, k -> new ConcurrentHashMap<>());
    }

    @Redirect(method = "addActivityAndRemoveMemoriesWhenStopped", at = @At(value = "INVOKE", target = "Ljava/util/Map;computeIfAbsent(Ljava/lang/Object;Ljava/util/function/Function;)Ljava/lang/Object;", ordinal = 1)
    )
    private Object async$concurrentInnerSet(Map<Object, Object> map, Object key, java.util.function.Function<Object, Object> mappingFunction) {
        if (AsyncConfig.disabled) return map.computeIfAbsent(key, mappingFunction);
        return map.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet());
    }

    @WrapMethod(method = "setCoreActivities")
    private void async$setCoreActivities(Set<Activity> newActivities, Operation<Void> original) {
        if (AsyncConfig.disabled) {
            original.call(newActivities);
            return;
        }
        Set<Activity> concurrent = Collections.newSetFromMap(new ConcurrentHashMap<>());
        concurrent.addAll(newActivities);
        this.coreActivities = concurrent;
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void async$buildSnapshot(ServerLevel level, E entity, CallbackInfo ci) {
        if (AsyncConfig.disabled) return;

        if (async$needsRebuild || async$snapshot == null) {
            synchronized (async$writeLock) {
                if (async$needsRebuild || async$snapshot == null) {
                    async$snapshot = new ConcurrentHashMap<>(this.memories);
                    async$needsRebuild = false;
                }
            }
        }

        async$inTick = true;
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void async$endTick(ServerLevel level, E entity, CallbackInfo ci) {
        async$inTick = false;
    }

    @Inject(method = "getMemory", at = @At("HEAD"), cancellable = true)
    private <U> void async$getMemory(MemoryModuleType<U> type, CallbackInfoReturnable<Optional<U>> cir) {
        if (AsyncConfig.disabled) return;

        Map<MemoryModuleType<?>, Optional<? extends ExpirableValue<?>>> snapshot = async$snapshot;
        if (async$inTick && snapshot != null) {
            Optional<? extends ExpirableValue<?>> value = snapshot.get(type);
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
    private void async$hasMemoryValue(MemoryModuleType<?> type, CallbackInfoReturnable<Boolean> cir) {
        if (AsyncConfig.disabled) return;

        Map<MemoryModuleType<?>, Optional<? extends ExpirableValue<?>>> snapshot = async$snapshot;
        if (async$inTick && snapshot != null) {
            Optional<? extends ExpirableValue<?>> value = snapshot.get(type);
            cir.setReturnValue(value != null && value.isPresent());
        }
    }

    @Inject(method = "checkMemory", at = @At("HEAD"), cancellable = true)
    private void async$checkMemory(MemoryModuleType<?> type, MemoryStatus status, CallbackInfoReturnable<Boolean> cir) {
        if (AsyncConfig.disabled) return;

        Map<MemoryModuleType<?>, Optional<? extends ExpirableValue<?>>> snapshot = async$snapshot;
        if (async$inTick && snapshot != null) {
            Optional<? extends ExpirableValue<?>> value = snapshot.get(type);

            boolean result = switch (status) {
                case REGISTERED -> true;
                case VALUE_PRESENT -> value != null && value.isPresent();
                case VALUE_ABSENT -> value == null || value.isEmpty();
            };

            cir.setReturnValue(result);
        }
    }

    @WrapMethod(method = "setMemoryInternal")
    private <U> void async$setMemory(MemoryModuleType<U> memoryType, Optional<? extends ExpirableValue<?>> memory, Operation<Void> original) {
        if (AsyncConfig.disabled) {
            original.call(memoryType, memory);
            return;
        }

        synchronized (async$writeLock) {
            original.call(memoryType, memory);
            if (async$snapshot != null && async$snapshot.containsKey(memoryType)) {
                async$snapshot.put(memoryType, memory);
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