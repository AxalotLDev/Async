package com.axalotl.async.common.mixin.entity;

import com.axalotl.async.common.parallelised.ConcurrentCollections;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.ExpirableValue;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Map;
import java.util.Optional;

@Mixin(Brain.class)
public class BrainMixin {

    @Shadow
    private final Map<MemoryModuleType<?>, Optional<? extends ExpirableValue<?>>> memories = ConcurrentCollections.newHashMap();

    @WrapMethod(method = "getMemory")
    private <U> Optional<U> wrapGetMemory(MemoryModuleType<U> type, Operation<Optional<U>> original) {
        Optional<U> result = original.call(type);
        if (result == null || result.isEmpty()) {
            return Optional.empty();
        }
        return result;
    }
}