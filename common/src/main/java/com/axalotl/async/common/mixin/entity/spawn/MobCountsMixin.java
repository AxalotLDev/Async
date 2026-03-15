package com.axalotl.async.common.mixin.entity.spawn;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.LocalMobCapCalculator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.concurrent.atomic.AtomicIntegerArray;

@Mixin(LocalMobCapCalculator.MobCounts.class)
public class MobCountsMixin {

    @Unique
    private final AtomicIntegerArray async$atomicCounts = new AtomicIntegerArray(MobCategory.values().length);

    @WrapMethod(method = "add")
    private void async$add(MobCategory category, Operation<Void> original) {
        async$atomicCounts.incrementAndGet(category.ordinal());
    }

    @WrapMethod(method = "canSpawn")
    private boolean async$canSpawn(MobCategory category, Operation<Boolean> original) {
        return async$atomicCounts.get(category.ordinal()) < category.getMaxInstancesPerChunk();
    }
}