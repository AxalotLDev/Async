package com.axalotl.async.common.mixin.world;

import com.axalotl.async.api.fastutil.Long2LongConcurrentHashMap;
import com.axalotl.async.api.fastutil.Long2ObjectConcurrentHashMap;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.util.Util;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.ticks.LevelChunkTicks;
import net.minecraft.world.ticks.LevelTickAccess;
import net.minecraft.world.ticks.LevelTicks;
import net.minecraft.world.ticks.ScheduledTick;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.*;

@Mixin(LevelTicks.class)
public abstract class LevelTicksMixin<T> implements LevelTickAccess<T> {
    @Shadow
    private final Long2ObjectMap<LevelChunkTicks<T>> allContainers = new Long2ObjectConcurrentHashMap<>();

    @Shadow
    private final Long2LongMap nextTickForContainer = Util.make(new Long2LongConcurrentHashMap(), p_193262_ -> p_193262_.defaultReturnValue(Long.MAX_VALUE));

    @WrapMethod(method = "sortContainersToTick")
    private void wrapContainersToTick(long currentTick, Operation<Void> original) {
        synchronized (this) {
            original.call(currentTick);
        }
    }

    @WrapMethod(method = "collectTicks")
    private void wrapCollectTicks(long currentTick, int maxTicksToProcess, ProfilerFiller profiler, Operation<Void> original) {
        synchronized (this) {
            original.call(currentTick, maxTicksToProcess, profiler);
        }
    }

    @WrapMethod(method = "schedule")
    private void wrapSchedule(ScheduledTick<T> tick, Operation<Void> original) {
        synchronized (this) {
            original.call(tick);
        }
    }

    @WrapMethod(method = "addContainer")
    private void wrapAddContainer(net.minecraft.world.level.ChunkPos pos, LevelChunkTicks<@NotNull T> container, Operation<Void> original) {
        synchronized (this) {
            original.call(pos, container);
        }
    }

    @WrapMethod(method = "removeContainer")
    private void wrapRemoveContainer(net.minecraft.world.level.ChunkPos pos, Operation<Void> original) {
        synchronized (this) {
            original.call(pos);
        }
    }
}