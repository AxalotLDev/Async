package com.axalotl.async.common.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

import java.util.Optional;

@Mixin(value = Brain.class, priority = 1500)
public class BrainMixin {

    @WrapMethod(method = "getMemory")
    private <U> Optional<U> getMemory(MemoryModuleType<U> type, Operation<Optional<U>> original) {
        synchronized (this) {
            return original.call(type);
        }
    }

    @WrapMethod(method = "hasMemoryValue")
    private boolean hasMemoryValue(MemoryModuleType<?> type, Operation<Boolean> original) {
        synchronized (this) {
            return original.call(type);
        }
    }

    @WrapMethod(method = "checkMemory")
    private boolean checkMemory(MemoryModuleType<?> type, MemoryStatus status, Operation<Boolean> original) {
        synchronized (this) {
            return original.call(type, status);
        }
    }

    @WrapMethod(method = "setMemoryInternal(Lnet/minecraft/world/entity/ai/memory/MemoryModuleType;Ljava/lang/Object;)V")
    private <U> void setMemory(MemoryModuleType<U> type, @Nullable U value, Operation<Void> original) {
        synchronized (this) {
            original.call(type, value);
        }
    }

    @WrapMethod(method = "clearMemories")
    private void clearMemories(Operation<Void> original) {
        synchronized (this) {
            original.call();
        }
    }
}