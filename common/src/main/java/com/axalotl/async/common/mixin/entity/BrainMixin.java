package com.axalotl.async.common.mixin.entity;

import com.axalotl.async.common.config.AsyncConfig;
import com.google.common.collect.Maps;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemorySlot;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(value = Brain.class, priority = 1500)
public class BrainMixin<E extends LivingEntity> {

    @Shadow
    private final Map<MemoryModuleType<?>, MemorySlot<?>> memories = Maps.newHashMap();

    @Unique
    private volatile Map<MemoryModuleType<?>, MemorySlot<?>> snapshot;

    @Unique
    private volatile boolean needsRebuild = true;

    @Unique
    private volatile boolean inTick;

    @Unique
    private final Object writeLock = new Object();

    @Inject(method = "tick", at = @At("HEAD"))
    private void buildSnapshot(ServerLevel level, E entity, CallbackInfo ci) {
        if (AsyncConfig.disabled) return;

        if (needsRebuild || snapshot == null) {
            synchronized (writeLock) {
                if (needsRebuild || snapshot == null) {
                    snapshot = new ConcurrentHashMap<>(this.memories);
                    needsRebuild = false;
                }
            }
        }

        inTick = true;
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void endTick(ServerLevel level, E entity, CallbackInfo ci) {
        inTick = false;
    }

    @Inject(method = "getMemory", at = @At("HEAD"), cancellable = true)
    private <U> void getMemory(MemoryModuleType<U> type, CallbackInfoReturnable<Optional<U>> cir) {
        if (AsyncConfig.disabled) return;

        Map<MemoryModuleType<?>, MemorySlot<?>> snapshot = this.snapshot;
        if (inTick && snapshot != null) {
            @SuppressWarnings("unchecked")
            MemorySlot<U> slot = (MemorySlot<U>) snapshot.get(type);
            Optional<U> result = (slot != null && slot.hasValue() && !slot.hasExpired())
                    ? Optional.ofNullable(slot.value())
                    : Optional.empty();
            cir.setReturnValue(result);
        }
    }

    @Inject(method = "hasMemoryValue", at = @At("HEAD"), cancellable = true)
    private void hasMemoryValue(MemoryModuleType<?> type, CallbackInfoReturnable<Boolean> cir) {
        if (AsyncConfig.disabled) return;

        Map<MemoryModuleType<?>, MemorySlot<?>> snapshot = this.snapshot;
        if (inTick && snapshot != null) {
            MemorySlot<?> slot = snapshot.get(type);
            cir.setReturnValue(slot != null && slot.hasValue() && !slot.hasExpired());
        }
    }

    @Inject(method = "checkMemory", at = @At("HEAD"), cancellable = true)
    private void checkMemory(MemoryModuleType<?> type, MemoryStatus status, CallbackInfoReturnable<Boolean> cir) {
        if (AsyncConfig.disabled) return;

        Map<MemoryModuleType<?>, MemorySlot<?>> snapshot = this.snapshot;
        if (inTick && snapshot != null) {
            MemorySlot<?> slot = snapshot.get(type);

            boolean result = switch (status) {
                case REGISTERED -> true;
                case VALUE_PRESENT -> slot != null && slot.hasValue() && !slot.hasExpired();
                case VALUE_ABSENT -> slot == null || !slot.hasValue() || slot.hasExpired();
            };

            cir.setReturnValue(result);
        }
    }

    @WrapMethod(method = "setMemoryInternal(Lnet/minecraft/world/entity/ai/memory/MemoryModuleType;Ljava/lang/Object;)V")
    private <U> void setMemory(MemoryModuleType<U> type, @Nullable U value, Operation<Void> original) {
        if (AsyncConfig.disabled) {
            original.call(type, value);
            return;
        }

        synchronized (writeLock) {
            original.call(type, value);
            if (snapshot != null && snapshot.containsKey(type)) {
                @SuppressWarnings("unchecked")
                MemorySlot<U> slot = (MemorySlot<U>) snapshot.get(type);
                if (slot != null && value != null) {
                    slot.set(value);
                }
            }
        }
    }

    @WrapMethod(method = "clearMemories")
    private void clearMemories(Operation<Void> original) {
        if (AsyncConfig.disabled) {
            original.call();
            return;
        }

        synchronized (writeLock) {
            original.call();
            needsRebuild = true;
        }
    }
}