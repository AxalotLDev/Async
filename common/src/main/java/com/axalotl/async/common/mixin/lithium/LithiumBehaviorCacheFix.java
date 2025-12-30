package com.axalotl.async.common.mixin.lithium;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Map;

/**
 * Disables Lithium's hasRequiredMemories caching for async compatibility.
 *
 * Lithium's cache uses non-thread-safe instance fields which break in async environment.
 * This mixin replaces Lithium's cached version with direct memory checks.
 *
 * Priority 2000 to override Lithium's @Overwrite (1000).
 */
@Mixin(value = Behavior.class, priority = 2000)
public class LithiumBehaviorCacheFix<E extends LivingEntity> {

    @Shadow
    @Final
    protected Map<MemoryModuleType<?>, MemoryStatus> entryCondition;

    /**
     * @author Async mod
     * @reason Disable Lithium's non-thread-safe caching for async entity ticking
     */
    @Overwrite
    public boolean hasRequiredMemories(E entity) {
        Brain<?> brain = entity.getBrain();

        for (Map.Entry<MemoryModuleType<?>, MemoryStatus> entry : this.entryCondition.entrySet()) {
            if (!brain.checkMemory(entry.getKey(), entry.getValue())) {
                return false;
            }
        }

        return true;
    }
}