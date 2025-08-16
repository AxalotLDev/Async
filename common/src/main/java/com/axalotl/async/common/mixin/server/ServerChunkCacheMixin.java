package com.axalotl.async.common.mixin.server;

import com.axalotl.async.common.AsyncCommon;
import com.axalotl.async.common.ParallelProcessor;
import com.axalotl.async.common.config.AsyncConfig;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LocalMobCapCalculator;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;

@Mixin(value = ServerChunkCache.class, priority = 1500)
public abstract class ServerChunkCacheMixin extends ChunkSource {
    @Shadow
    @Final
    public ChunkMap chunkMap;
    @Shadow
    @Final
    Thread mainThread;

    @Shadow
    public abstract @Nullable ChunkHolder getVisibleChunkIfPresent(long pos);

    @Shadow
    @Final
    public ServerLevel level;

    @Unique
    private static NaturalSpawner.SpawnState async$lastCachedSpawnState = null;
    @Unique
    private static CompletableFuture<NaturalSpawner.SpawnState> async$futureSpawnState = null;

    @Inject(method = "getChunk(IILnet/minecraft/world/level/chunk/status/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/ChunkAccess;",
            at = @At("HEAD"), cancellable = true)
    private void shortcutGetChunk(int x, int z, ChunkStatus leastStatus, boolean create, CallbackInfoReturnable<ChunkAccess> cir) {
        if (AsyncCommon.LITHIUM) return;
        if (Thread.currentThread() != this.mainThread) {
            final ChunkHolder holder = this.getVisibleChunkIfPresent(ChunkPos.asLong(x, z));
            if (holder != null) {
                final CompletableFuture<ChunkResult<ChunkAccess>> future = holder.scheduleChunkGenerationTask(leastStatus, this.chunkMap);
                if (future.isDone()) {
                    ChunkAccess chunk = future.getNow(ChunkHolder.UNLOADED_CHUNK).orElse(null);
                    if (chunk instanceof ImposterProtoChunk readOnlyChunk) chunk = readOnlyChunk.getWrapped();
                    if (chunk != null) {
                        cir.setReturnValue(chunk);
                        return;
                    }
                }
            }
        }
    }

    @Inject(method = "getChunkNow", at = @At("HEAD"), cancellable = true)
    private void shortcutGetChunkNow(int chunkX, int chunkZ, CallbackInfoReturnable<LevelChunk> cir) {
        if (Thread.currentThread() != this.mainThread) {
            final ChunkHolder holder = this.getVisibleChunkIfPresent(ChunkPos.asLong(chunkX, chunkZ));
            if (holder != null) {
                final CompletableFuture<ChunkResult<ChunkAccess>> future = holder.scheduleChunkGenerationTask(ChunkStatus.FULL, this.chunkMap);
                ChunkAccess chunk = future.getNow(ChunkHolder.UNLOADED_CHUNK).orElse(null);
                if (chunk instanceof LevelChunk worldChunk) {
                    cir.setReturnValue(worldChunk);
                    return;
                }
            }
        }
    }

    @Redirect(method = "tickChunks", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/NaturalSpawner;createState(ILjava/lang/Iterable;Lnet/minecraft/world/level/NaturalSpawner$ChunkGetter;Lnet/minecraft/world/level/LocalMobCapCalculator;)Lnet/minecraft/world/level/NaturalSpawner$SpawnState;"))
    private NaturalSpawner.SpawnState createState(int spawnableChunkCount, Iterable<Entity> entities, NaturalSpawner.ChunkGetter chunkGetter, LocalMobCapCalculator calculator) {
        if (AsyncConfig.enableAsyncSpawn) {
            if (async$futureSpawnState == null || async$futureSpawnState.isDone()) {
                async$futureSpawnState = CompletableFuture.supplyAsync(() ->
                        NaturalSpawner.createState(
                                spawnableChunkCount, entities, chunkGetter, calculator
                        ), ParallelProcessor.tickPool
                ).exceptionally(e -> {
                    ParallelProcessor.LOGGER.error("Error in async create state, switching to synchronous", e);
                    return NaturalSpawner.createState(
                            spawnableChunkCount, entities, chunkGetter, calculator
                    );
                });

                async$futureSpawnState.thenAccept(result -> async$lastCachedSpawnState = result);
            }

            NaturalSpawner.SpawnState spawnState = async$lastCachedSpawnState;
            if (spawnState == null) {
                spawnState = NaturalSpawner.createState(
                        spawnableChunkCount, entities, chunkGetter, calculator
                );
                async$lastCachedSpawnState = spawnState;
            }
            return spawnState;
        } else return NaturalSpawner.createState(spawnableChunkCount, entities, chunkGetter, calculator);
    }

    @Redirect(method = "tickChunks", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/NaturalSpawner;spawnForChunk(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/LevelChunk;Lnet/minecraft/world/level/NaturalSpawner$SpawnState;ZZZ)V"))
    private void spawnForChunk(ServerLevel level, LevelChunk chunk, NaturalSpawner.SpawnState spawnState, boolean spawnFriendlies, boolean spawnMonsters, boolean forcedDespawn) {
        if (AsyncConfig.enableAsyncSpawn) {
            CompletableFuture.runAsync(() -> NaturalSpawner.spawnForChunk(level, chunk, async$lastCachedSpawnState, spawnFriendlies, spawnMonsters, forcedDespawn), ParallelProcessor.tickPool).exceptionally(e -> {
                ParallelProcessor.LOGGER.error("Error in async spawn, switching to synchronous", e);
                NaturalSpawner.spawnForChunk(level, chunk, spawnState, spawnFriendlies, spawnMonsters, forcedDespawn);
                return null;
            });
        } else {
            NaturalSpawner.spawnForChunk(level, chunk, spawnState, spawnFriendlies, spawnMonsters, forcedDespawn);
        }
    }

    @Redirect(method = "tickChunks", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;tickCustomSpawners(ZZ)V"))
    private void tickCustomSpawners(ServerLevel instance, boolean spawnEnemies, boolean spawnFriendlies) {
        if (AsyncConfig.enableAsyncSpawn) {
            CompletableFuture.runAsync(() -> instance.tickCustomSpawners(spawnEnemies, spawnFriendlies), ParallelProcessor.tickPool).exceptionally(e -> {
                ParallelProcessor.LOGGER.error("Error in async tickCustomSpawners, switching to synchronous", e);
                instance.tickCustomSpawners(spawnEnemies, spawnFriendlies);
                return null;
            });
        } else {
            instance.tickCustomSpawners(spawnEnemies, spawnFriendlies);
        }
    }
}