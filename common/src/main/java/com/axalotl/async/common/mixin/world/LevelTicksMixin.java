package com.axalotl.async.common.mixin.world;

import com.axalotl.async.common.parallelised.fastutil.Long2LongConcurrentHashMap;
import com.axalotl.async.common.parallelised.fastutil.Long2ObjectConcurrentHashMap;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.util.Util;
import net.minecraft.world.ticks.LevelChunkTicks;
import net.minecraft.world.ticks.LevelTickAccess;
import net.minecraft.world.ticks.LevelTicks;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.*;

@Mixin(LevelTicks.class)
public abstract class LevelTicksMixin<T> implements LevelTickAccess<T> {
    @Shadow
    private final Long2ObjectMap<LevelChunkTicks<T>> allContainers = new Long2ObjectConcurrentHashMap<>();

    @Shadow
    private final Long2LongMap nextTickForContainer = Util.make(new Long2LongConcurrentHashMap(), p_193262_ -> p_193262_.defaultReturnValue(Long.MAX_VALUE));

    @Unique
    private final Object async$lock = new Object();

    @WrapMethod(method = "addContainer")
    private void wrapAddContainer(net.minecraft.world.level.ChunkPos pos, LevelChunkTicks<@NotNull T> container, Operation<Void> original) {
        synchronized (async$lock) {
            original.call(pos, container);
        }
    }

    @WrapMethod(method = "removeContainer")
    private void wrapRemoveContainer(net.minecraft.world.level.ChunkPos pos, Operation<Void> original) {
        synchronized (async$lock) {
            original.call(pos);
        }
    }
}