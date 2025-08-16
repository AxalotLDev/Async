package com.axalotl.async.common.mixin.server;

import com.axalotl.async.common.AsyncCommon;
import com.axalotl.async.common.ParallelProcessor;
import com.axalotl.async.common.config.AsyncConfig;
import com.google.common.collect.Lists;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.Util;
import net.minecraft.server.level.*;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

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

    @Shadow
    private long lastInhabitedUpdate;

    @Shadow
    @Final
    private DistanceManager distanceManager;

    @Shadow
    protected abstract void getFullChunk(long chunkPos, Consumer<LevelChunk> fullChunkGetter);

    @Shadow
    @Nullable
    private NaturalSpawner.SpawnState lastSpawnState;

    @Shadow
    private boolean spawnEnemies;

    @Shadow
    private boolean spawnFriendlies;

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

    @WrapMethod(method = "tickChunks")
    private void tickChunks(Operation<Void> original) {
        if (AsyncConfig.enableAsyncSpawn) {
            long i = this.level.getGameTime();
            long j = i - this.lastInhabitedUpdate;
            this.lastInhabitedUpdate = i;
            if (!this.level.isDebug()) {
                ProfilerFiller profilerfiller = this.level.getProfiler();
                profilerfiller.push("pollingChunks");
                profilerfiller.push("filteringLoadedChunks");
                List<ServerChunkCache.ChunkAndHolder> list = Lists.newArrayListWithCapacity(this.chunkMap.size());

                for (ChunkHolder chunkholder : this.chunkMap.getChunks()) {
                    LevelChunk levelchunk = chunkholder.getTickingChunk();
                    if (levelchunk != null) {
                        list.add(new ServerChunkCache.ChunkAndHolder(levelchunk, chunkholder));
                    }
                }

                if (this.level.tickRateManager().runsNormally()) {
                    profilerfiller.popPush("naturalSpawnCount");
                    int l = this.distanceManager.getNaturalSpawnChunkCount();
                    if (async$futureSpawnState == null || async$futureSpawnState.isDone()) {
                        async$futureSpawnState = CompletableFuture.supplyAsync(() ->
                                NaturalSpawner.createState(
                                        l, this.level.getAllEntities(), this::getFullChunk, new LocalMobCapCalculator(this.chunkMap)
                                ), ParallelProcessor.tickPool
                        ).exceptionally(e -> {
                            ParallelProcessor.LOGGER.error("Error in async create state, switching to synchronous", e);
                            return NaturalSpawner.createState(
                                    l, this.level.getAllEntities(), this::getFullChunk, new LocalMobCapCalculator(this.chunkMap)
                            );
                        });

                        async$futureSpawnState.thenAccept(result -> async$lastCachedSpawnState = result);
                    }

                    NaturalSpawner.SpawnState spawnState = async$lastCachedSpawnState;
                    if (spawnState == null) {
                        spawnState = NaturalSpawner.createState(
                                l, this.level.getAllEntities(), this::getFullChunk, new LocalMobCapCalculator(this.chunkMap)
                        );
                        async$lastCachedSpawnState = spawnState;
                    }
                    this.lastSpawnState = spawnState;
                    profilerfiller.popPush("spawnAndTick");
                    boolean flag1 = this.level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING);
                    Util.shuffle(list, this.level.random);
                    int k = this.level.getGameRules().getInt(GameRules.RULE_RANDOMTICKING);
                    boolean flag = this.level.getLevelData().getGameTime() % 400L == 0L;

                    for (ServerChunkCache.ChunkAndHolder serverchunkcache$chunkandholder : list) {
                        LevelChunk levelchunk1 = serverchunkcache$chunkandholder.chunk();
                        ChunkPos chunkpos = levelchunk1.getPos();
                        if ((this.level.isNaturalSpawningAllowed(chunkpos) && this.chunkMap.anyPlayerCloseEnoughForSpawning(chunkpos)) || this.distanceManager.shouldForceTicks(chunkpos.toLong())) {
                            levelchunk1.incrementInhabitedTime(j);
                            if (flag1 && (this.spawnEnemies || this.spawnFriendlies) && this.level.getWorldBorder().isWithinBounds(chunkpos)) {
                                NaturalSpawner.SpawnState finalSpawnState = spawnState;
                                CompletableFuture.runAsync(() -> NaturalSpawner.spawnForChunk(this.level, levelchunk1, finalSpawnState, this.spawnFriendlies, this.spawnEnemies, flag), ParallelProcessor.tickPool).exceptionally(e -> {
                                    ParallelProcessor.LOGGER.error("Error in async spawn, switching to synchronous", e);
                                    NaturalSpawner.spawnForChunk(this.level, levelchunk1, finalSpawnState, this.spawnFriendlies, this.spawnEnemies, flag);
                                    return null;
                                });
                            }

                            if (this.level.shouldTickBlocksAt(chunkpos.toLong())) {
                                this.level.tickChunk(levelchunk1, k);
                            }
                        }
                    }

                    profilerfiller.popPush("customSpawners");
                    if (flag1) {
                        CompletableFuture.runAsync(() -> this.level.tickCustomSpawners(this.spawnEnemies, this.spawnFriendlies), ParallelProcessor.tickPool).exceptionally(e -> {
                            ParallelProcessor.LOGGER.error("Error in async tickCustomSpawners, switching to synchronous", e);
                            this.level.tickCustomSpawners(this.spawnEnemies, this.spawnFriendlies);
                            return null;
                        });
                    }
                }

                profilerfiller.popPush("broadcast");
                list.forEach(p_184022_ -> p_184022_.holder().broadcastChanges(p_184022_.chunk()));
                profilerfiller.pop();
                profilerfiller.pop();
            }
        } else original.call();
    }
}