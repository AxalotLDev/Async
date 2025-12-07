package com.axalotl.async.common.mixin.entity;

import com.axalotl.async.common.parallelised.ConcurrentCollections;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.ExpirableValue;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import org.spongepowered.asm.mixin.*;

import java.util.Map;
import java.util.Optional;

@Mixin(Brain.class)
public class BrainMixin {

    @Shadow
    private final Map<MemoryModuleType<?>, Optional<? extends ExpirableValue<?>>> memories = ConcurrentCollections.newHashMap();

    @Unique
    private static final Object async$lock = new Object();

    @WrapMethod(method = "setMemoryInternal")
    private <U> void setMemoryInternal(MemoryModuleType<U> memoryType, Optional<? extends ExpirableValue<?>> memory, Operation<Void> original) {
        synchronized (async$lock) {
            original.call(memoryType, memory);
        }
    }

    @WrapMethod(method = "clearMemories")
    private void clearMemories(Operation<Void> original) {
        synchronized (async$lock) {
            original.call();
        }
    }
}