package com.axalotl.async.common.mixin.server;

import com.axalotl.async.common.ParallelProcessor;
import com.axalotl.async.common.config.AsyncConfig;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.LocalMobCapCalculator;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Mixin(NaturalSpawner.class)
public class NaturalSpawnerMixin {
    @Unique
    private static NaturalSpawner.SpawnState async$lastCachedSpawnState = null;
    @Unique
    private static CompletableFuture<NaturalSpawner.SpawnState> async$futureSpawnState = null;

    @WrapMethod(method = "createState")
    private static NaturalSpawner.SpawnState createState(int spawnableChunkCount, Iterable<Entity> entities, NaturalSpawner.ChunkGetter chunkGetter, LocalMobCapCalculator calculator, Operation<NaturalSpawner.SpawnState> original) {
        if (AsyncConfig.enableAsyncSpawn) {
            if (async$futureSpawnState == null || async$futureSpawnState.isDone()) {
                async$futureSpawnState = CompletableFuture.supplyAsync(() -> original.call(
                                spawnableChunkCount,
                                entities,
                                chunkGetter,
                                calculator
                        ), ParallelProcessor.tickPool
                ).exceptionally(e -> {
                    ParallelProcessor.LOGGER.error("Error in async create spawn state, switching to synchronous", e);
                    original.call(
                            spawnableChunkCount,
                            entities,
                            chunkGetter,
                            calculator
                    );
                    return null;
                });

                async$futureSpawnState.thenAccept(result -> async$lastCachedSpawnState = result);
            }

            NaturalSpawner.SpawnState spawnState = async$lastCachedSpawnState;
            if (spawnState == null) {
                spawnState = original.call(
                        spawnableChunkCount,
                        entities,
                        chunkGetter,
                        calculator
                );
                async$lastCachedSpawnState = spawnState;
            }
            return spawnState;
        } else return original.call(spawnableChunkCount, entities, chunkGetter, calculator);
    }

    @WrapMethod(method = "spawnForChunk")
    private static void spawnForChunk(ServerLevel level, LevelChunk chunk, NaturalSpawner.SpawnState spawnState, List<MobCategory> categories, Operation<Void> original) {
        if (AsyncConfig.enableAsyncSpawn) {
            CompletableFuture.runAsync(() -> original.call(level, chunk, spawnState, categories), ParallelProcessor.tickPool).exceptionally(e -> {
                ParallelProcessor.LOGGER.error("Error in async spawn, switching to synchronous", e);
                original.call(level, chunk, spawnState, categories);
                return null;
            });
        } else original.call(level, chunk, spawnState, categories);
    }
}