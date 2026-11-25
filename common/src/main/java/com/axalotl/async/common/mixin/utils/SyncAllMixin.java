package com.axalotl.async.common.mixin.utils;

import net.minecraft.advancements.criterion.SimpleCriterionTrigger;
import net.minecraft.util.ClassInstanceMultiMap;
import net.minecraft.util.profiling.ActiveProfiler;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.warden.AngerManagement;
import net.minecraft.world.level.gameevent.EuclideanGameEventListenerRegistry;
import net.minecraft.world.level.lighting.DynamicGraphMinFixedPoint;
import net.minecraft.world.level.pathfinder.BinaryHeap;
import net.minecraft.world.ticks.LevelChunkTicks;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = {
        BinaryHeap.class,
        LevelChunkTicks.class,
        DynamicGraphMinFixedPoint.class,
        PathNavigation.class,
        EuclideanGameEventListenerRegistry.class,
        SimpleCriterionTrigger.class,
        AngerManagement.class,
        ClassInstanceMultiMap.class,
        ActiveProfiler.class,
})
public class SyncAllMixin {
}