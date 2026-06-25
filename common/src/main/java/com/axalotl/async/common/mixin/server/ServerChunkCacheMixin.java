package com.axalotl.async.common.mixin.server;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;

@Mixin(value = ServerChunkCache.class, priority = 1500)
public abstract class ServerChunkCacheMixin extends ChunkSource {
    @Unique
    private static final Logger LOGGER = LoggerFactory.getLogger(ServerChunkCacheMixin.class);

    @Shadow
    @Final
    public ChunkMap chunkMap;

    @Shadow
    @Final
    Thread mainThread;

    @Shadow
    @Final
    public ServerChunkCache.MainThreadExecutor mainThreadProcessor;

    @Shadow
    public abstract @Nullable ChunkHolder getVisibleChunkIfPresent(long pos);

    @Shadow
    protected abstract CompletableFuture<ChunkResult<ChunkAccess>> getChunkFutureMainThread(int x, int z, ChunkStatus leastStatus, boolean create);

    @Shadow
    @Nullable
    private NaturalSpawner.@Nullable SpawnState lastSpawnState;
    @Shadow
    @Final
    private DistanceManager distanceManager;

    @Shadow
    protected abstract void getFullChunk(long chunkPos, Consumer<LevelChunk> fullChunkGetter);

    @Shadow
    @Final
    ServerLevel level;
    @Shadow
    private long lastInhabitedUpdate;
    @Shadow
    private boolean spawnEnemies;
    @Shadow
    private boolean spawnFriendlies;
    @Unique
    private boolean async$firstRunSpawnCounts = true;

    @Unique
    private final AtomicBoolean async$spawnCountsReady = new AtomicBoolean(false);

    @Inject(method = "getChunk(IILnet/minecraft/world/level/chunk/status/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/ChunkAccess;", at = @At("HEAD"), cancellable = true)
    private void async$getChunk(int x, int z, ChunkStatus leastStatus, boolean create, CallbackInfoReturnable<ChunkAccess> cir) {
        if (Thread.currentThread() == this.mainThread) return;

        ChunkAccess access = async$tryGetChunk(x, z, leastStatus);
        if (access != null) {
            cir.setReturnValue(access);
            return;
        }

        CompletableFuture<ChunkResult<ChunkAccess>> future = CompletableFuture.supplyAsync(
                () -> this.getChunkFutureMainThread(x, z, leastStatus, create),
                this.mainThreadProcessor
        ).thenCompose(f -> f);

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
        while (!future.isDone()) {
            if (System.nanoTime() > deadline) {
                future.cancel(false);
                return;
            }
            ChunkAccess cached = async$tryGetChunk(x, z, leastStatus);
            if (cached != null) {
                future.cancel(false);
                cir.setReturnValue(cached);
                return;
            }
            LockSupport.parkNanos(10_000);
        }

        ChunkAccess chunk = future.join().orElse(null);
        if (chunk instanceof ImposterProtoChunk imposter) {
            chunk = imposter.getWrapped();
        }
        cir.setReturnValue(chunk);
    }

    @Unique
    private @Nullable ChunkAccess async$tryGetChunk(int x, int z, ChunkStatus leastStatus) {
        ChunkHolder holder = this.getVisibleChunkIfPresent(ChunkPos.asLong(x, z));
        if (holder == null) return null;

        ChunkAccess chunk = holder.getChunkIfPresent(leastStatus);
        if (chunk != null) {
            if (chunk instanceof ImposterProtoChunk imposter) {
                return imposter.getWrapped();
            }
            return chunk;
        }

        return null;
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
                }
            }
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void tick(CallbackInfo ci) {
        if (AsyncConfig.disabled || !AsyncConfig.enableAsyncSpawn) return;

        if (async$firstRunSpawnCounts) {
            async$firstRunSpawnCounts = false;
            async$spawnCountsReady.set(true);
        }
        if (async$spawnCountsReady.getAndSet(false)) {
            int l = this.distanceManager.getNaturalSpawnChunkCount();
            if (ParallelProcessor.executor != null
                    && !ParallelProcessor.executor.isShutdown()
                    && !ParallelProcessor.executor.isTerminated()) {
                ParallelProcessor.executor.submit(() -> {
                    this.lastSpawnState = NaturalSpawner.createState(
                            l,
                            this.level.getAllEntities(),
                            this::getFullChunk,
                            new LocalMobCapCalculator(this.chunkMap)
                    );

                    async$spawnCountsReady.set(true);
                });
            }
        }
    }

    @WrapMethod(method = "tickChunks()V")
    private void tickChunksSpawn(Operation<Void> original) {
        long gameTime = this.level.getGameTime();
        long delta = gameTime - this.lastInhabitedUpdate;
        this.lastInhabitedUpdate = gameTime;
        if (!this.level.isDebug()) {
            ProfilerFiller profiler = this.level.getProfiler();
            profiler.push("pollingChunks");
            profiler.push("filteringLoadedChunks");
            List<ServerChunkCache.ChunkAndHolder> chunks = Lists.newArrayListWithCapacity(this.chunkMap.size());

            for (ChunkHolder holder : this.chunkMap.getChunks()) {
                LevelChunk chunk = holder.getTickingChunk();
                if (chunk != null) {
                    chunks.add(new ServerChunkCache.ChunkAndHolder(chunk, holder));
                }
            }

            if (this.level.tickRateManager().runsNormally()) {
                profiler.popPush("naturalSpawnCount");
                int spawnChunkCount = this.distanceManager.getNaturalSpawnChunkCount();

                if (AsyncConfig.disabled || !AsyncConfig.enableAsyncSpawn || async$firstRunSpawnCounts) {
                    lastSpawnState = NaturalSpawner.createState(
                            spawnChunkCount,
                            this.level.getAllEntities(),
                            this::getFullChunk,
                            new LocalMobCapCalculator(this.chunkMap)
                    );
                }

                profiler.popPush("spawnAndTick");
                boolean doMobSpawning = this.level.getGameRules()
                        .getBoolean(GameRules.RULE_DOMOBSPAWNING);
                int randomTickSpeed = this.level.getGameRules()
                        .getInt(GameRules.RULE_RANDOMTICKING);
                boolean spawnCycle = this.level.getLevelData()
                        .getGameTime() % 400L == 0L;
                Util.shuffle(chunks, this.level.random);
                if (!AsyncConfig.disabled
                        && AsyncConfig.enableAsyncSpawn
                        && lastSpawnState != null
                        && ParallelProcessor.executor != null
                        && !ParallelProcessor.executor.isShutdown()
                        && !ParallelProcessor.executor.isTerminated()) {
                    NaturalSpawner.SpawnState state = lastSpawnState;
                    CompletableFuture.runAsync(() -> {
                        if (!ParallelProcessor.executor.isShutdown()) {
                            for (ServerChunkCache.ChunkAndHolder entry : chunks) {
                                LevelChunk chunk = entry.chunk();
                                ChunkPos pos = chunk.getPos();
                                if (this.level.isNaturalSpawningAllowed(pos)
                                        && this.chunkMap.anyPlayerCloseEnoughForSpawning(pos)) {
                                    chunk.incrementInhabitedTime(delta);
                                    if (doMobSpawning
                                            && (this.spawnEnemies || this.spawnFriendlies)
                                            && this.level.getWorldBorder().isWithinBounds(pos)) {
                                        NaturalSpawner.spawnForChunk(
                                                this.level,
                                                chunk,
                                                state,
                                                this.spawnFriendlies,
                                                this.spawnEnemies,
                                                spawnCycle
                                        );
                                    }
                                    if (this.level.shouldTickBlocksAt(pos.toLong())) {
                                        this.level.tickChunk(chunk, randomTickSpeed);
                                    }
                                }
                            }
                        }
                    }, ParallelProcessor.executor).exceptionally(e -> {
                        LOGGER.error("Error in async entity spawning, switching to synchronous", e);
                        for (ServerChunkCache.ChunkAndHolder entry : chunks) {
                            LevelChunk chunk = entry.chunk();
                            ChunkPos pos = chunk.getPos();
                            if (this.level.isNaturalSpawningAllowed(pos)
                                    && this.chunkMap.anyPlayerCloseEnoughForSpawning(pos)) {
                                chunk.incrementInhabitedTime(delta);
                                if (doMobSpawning
                                        && (this.spawnEnemies || this.spawnFriendlies)
                                        && this.level.getWorldBorder().isWithinBounds(pos)) {
                                    NaturalSpawner.spawnForChunk(
                                            this.level,
                                            chunk,
                                            lastSpawnState,
                                            this.spawnFriendlies,
                                            this.spawnEnemies,
                                            spawnCycle
                                    );
                                }
                                if (this.level.shouldTickBlocksAt(pos.toLong())) {
                                    this.level.tickChunk(chunk, randomTickSpeed);
                                }
                            }
                        }
                        return null;
                    });

                } else {
                    for (ServerChunkCache.ChunkAndHolder entry : chunks) {
                        LevelChunk chunk = entry.chunk();
                        ChunkPos pos = chunk.getPos();
                        if (this.level.isNaturalSpawningAllowed(pos)
                                && this.chunkMap.anyPlayerCloseEnoughForSpawning(pos)) {
                            chunk.incrementInhabitedTime(delta);
                            if (doMobSpawning
                                    && (this.spawnEnemies || this.spawnFriendlies)
                                    && this.level.getWorldBorder().isWithinBounds(pos)) {
                                NaturalSpawner.spawnForChunk(
                                        this.level,
                                        chunk,
                                        Objects.requireNonNull(lastSpawnState),
                                        this.spawnFriendlies,
                                        this.spawnEnemies,
                                        spawnCycle
                                );
                            }
                            if (this.level.shouldTickBlocksAt(pos.toLong())) {
                                this.level.tickChunk(chunk, randomTickSpeed);
                            }
                        }
                    }
                }
                profiler.popPush("customSpawners");
                if (doMobSpawning) {
                    this.level.tickCustomSpawners(this.spawnEnemies, this.spawnFriendlies);
                }
            }
            profiler.popPush("broadcast");
            chunks.forEach(e -> e.holder().broadcastChanges(e.chunk()));
            profiler.pop();
            profiler.pop();
        }
    }
}
