package com.axalotl.async.common.mixin.world;

import com.axalotl.async.common.parallelised.fastutil.Long2LongConcurrentHashMap;
import com.axalotl.async.common.parallelised.fastutil.Long2ObjectConcurrentHashMap;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.ticks.LevelChunkTicks;
import net.minecraft.world.ticks.LevelTickAccess;
import net.minecraft.world.ticks.LevelTicks;
import net.minecraft.world.ticks.ScheduledTick;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelTicks.class)
public abstract class LevelTicksMixin<T> implements LevelTickAccess<@NotNull T> {

    @Shadow
    @Final
    @Mutable
    private Long2ObjectMap<LevelChunkTicks<@NotNull T>> allContainers;

    @Shadow
    @Final
    @Mutable
    private Long2LongMap nextTickForContainer;

    @Unique
    private static final Object async$lock = new Object();

    /**
     * Replace non-thread-safe collections with concurrent versions at construction time.
     */
    @Inject(method = "<init>", at = @At("TAIL"))
    private void replaceConcurrentCollections(java.util.function.LongPredicate p_193211_, CallbackInfo ci) {
        this.allContainers = new Long2ObjectConcurrentHashMap<>();
        Long2LongConcurrentHashMap newMap = new Long2LongConcurrentHashMap();
        newMap.defaultReturnValue(Long.MAX_VALUE);
        this.nextTickForContainer = newMap;
    }

    /**
     * Synchronize sortContainersToTick to prevent concurrent modification during iteration.
     */
    @WrapMethod(method = "sortContainersToTick")
    private void wrapSortContainersToTick(long gameTime, Operation<Void> original) {
        synchronized (async$lock) {
            original.call(gameTime);
        }
    }

    /**
     * Synchronize collectTicks to prevent race conditions.
     */
    @WrapMethod(method = "collectTicks")
    private void wrapCollectTicks(long p_193222_, int p_193223_, ProfilerFiller p_193224_, Operation<Void> original) {
        synchronized (async$lock) {
            original.call(p_193222_, p_193223_, p_193224_);
        }
    }

    /**
     * Synchronize schedule to prevent concurrent modification.
     */
    @WrapMethod(method = "schedule")
    private void wrapSchedule(ScheduledTick<@NotNull T> tick, Operation<Void> original) {
        synchronized (async$lock) {
            original.call(tick);
        }
    }

    /**
     * Synchronize addContainer to prevent concurrent modification.
     */
    @WrapMethod(method = "addContainer")
    private void wrapAddContainer(net.minecraft.world.level.ChunkPos pos, LevelChunkTicks<@NotNull T> ticks, Operation<Void> original) {
        synchronized (async$lock) {
            original.call(pos, ticks);
        }
    }

    /**
     * Synchronize removeContainer to prevent concurrent modification.
     */
    @WrapMethod(method = "removeContainer")
    private void wrapRemoveContainer(net.minecraft.world.level.ChunkPos pos, Operation<Void> original) {
        synchronized (async$lock) {
            original.call(pos);
        }
    }
}