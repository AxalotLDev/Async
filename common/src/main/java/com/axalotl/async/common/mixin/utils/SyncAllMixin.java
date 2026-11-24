package com.axalotl.async.common.mixin.utils;

import net.minecraft.util.profiling.ActiveProfiler;
import net.minecraft.world.entity.monster.warden.AngerManagement;
import net.minecraft.world.level.gameevent.EuclideanGameEventListenerRegistry;
import net.minecraft.world.level.lighting.DynamicGraphMinFixedPoint;
import net.minecraft.world.ticks.LevelChunkTicks;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = {
        LevelChunkTicks.class,
        EuclideanGameEventListenerRegistry.class,
        AngerManagement.class,
        DynamicGraphMinFixedPoint.class,
        ActiveProfiler.class,
})
public class SyncAllMixin {
}