package com.axalotl.async.common.mixin.entity.spawn;

import com.axalotl.async.api.spawn.AsyncSpawnStateView;
import com.axalotl.async.common.ParallelProcessor;
import com.axalotl.async.common.config.AsyncConfig;
import com.axalotl.async.common.parallelised.utils.AsyncSpawnContext;
import com.axalotl.async.common.parallelised.utils.ParallelSpawnHelper;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.LocalMobCapCalculator;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.concurrent.atomic.AtomicIntegerArray;


@Mixin(value = NaturalSpawner.class, priority = 1100)
public abstract class NaturalSpawnerMixin {

    @Inject(method = "createState", at = @At("HEAD"), cancellable = true)
    private static void async$parallelCreateState(int spawnableChunkCount, Iterable<Entity> entities, NaturalSpawner.ChunkGetter chunkGetter, LocalMobCapCalculator localMobCapCalculator, CallbackInfoReturnable<NaturalSpawner.SpawnState> cir) {
        if (AsyncConfig.disabled || !AsyncConfig.enableAsyncSpawn) return;
        if (ParallelProcessor.executor == null) return;
        cir.setReturnValue(ParallelSpawnHelper.buildState(spawnableChunkCount, entities, chunkGetter, localMobCapCalculator));
    }


    @WrapMethod(method = "spawnForChunk")
    private static void async$bindState(ServerLevel level, LevelChunk chunk, NaturalSpawner.SpawnState state, List<MobCategory> spawningCategories, Operation<Void> original) {
        if (AsyncConfig.disabled || !AsyncConfig.enableAsyncSpawn) {
            original.call(level, chunk, state, spawningCategories);
            return;
        }
        AsyncSpawnContext.Frame frame = AsyncSpawnContext.FRAME.get();
        NaturalSpawner.SpawnState previous = frame.state;
        frame.state = state;
        frame.clearFlags();
        try {
            original.call(level, chunk, state, spawningCategories);
        } finally {
            frame.state = previous;
            frame.clearFlags();
        }
    }

    @WrapOperation(
            method = "spawnCategoryForPosition(Lnet/minecraft/world/entity/MobCategory;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/NaturalSpawner$SpawnPredicate;Lnet/minecraft/world/level/NaturalSpawner$AfterSpawnCallback;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;addFreshEntityWithPassengers(Lnet/minecraft/world/entity/Entity;)V"))
    private static void async$capAndAdd(ServerLevel level, Entity mob, Operation<Void> original, @Local(argsOnly = true) MobCategory mobCategory) {
        if (AsyncConfig.disabled || !AsyncConfig.enableAsyncSpawn) {
            original.call(level, mob);
            return;
        }

        AsyncSpawnContext.Frame frame = AsyncSpawnContext.FRAME.get();
        NaturalSpawner.SpawnState state = frame.state;
        if (!(state instanceof AsyncSpawnStateView view)) {
            original.call(level, mob);
            return;
        }

        AtomicIntegerArray backing = view.async$countsBacking();
        int ordinal = mobCategory.ordinal();

        while (true) {
            if (!view.async$canSpawnForCategoryGlobal(mobCategory)) {
                frame.skipAfterSpawn = true;
                return;
            }
            int current = backing.get(ordinal);
            if (backing.compareAndSet(ordinal, current, current + 1)) break;
        }

        frame.suppressCountIncrement = true;
        try {
            original.call(level, mob);
        } catch (Throwable t) {
            backing.decrementAndGet(ordinal);
            frame.suppressCountIncrement = false;
            throw t;
        }
    }
}
