package com.axalotl.async.common.mixin.server;

import com.axalotl.async.common.ParallelProcessor;
import com.axalotl.async.common.config.AsyncConfig;
import com.axalotl.async.common.utils.LastChunkCache;
import com.google.common.collect.Lists;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.Util;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;

@Mixin(value = ServerChunkCache.class, priority = 1500)
public abstract class ServerChunkCacheMixin extends ChunkSource {
    @Unique
    private static final Logger ASYNC_LOGGER = LoggerFactory.getLogger(ServerChunkCacheMixin.class);

    @Unique
    private static final long CHUNK_WAIT_TIMEOUT_NANOSECONDS = TimeUnit.SECONDS.toNanos(60L);

    @Unique
    private static final long CHUNK_WAIT_PARK_NANOSECONDS = 10_000L;

    @Unique
    private static final double SPAWN_PLAYER_DISTANCE_SQUARED = 16_384.0;

    @Unique
    private static final ThreadLocal<LastChunkCache> LAST_CHUNK =
            ThreadLocal.withInitial(LastChunkCache::new);

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
    public abstract @Nullable ChunkHolder getVisibleChunkIfPresent(long positionKey);

    @Shadow
    protected abstract CompletableFuture<ChunkResult<ChunkAccess>> getChunkFutureMainThread(
            int chunkX,
            int chunkZ,
            ChunkStatus leastStatus,
            boolean create
    );

    @Shadow
    @Nullable
    private NaturalSpawner.SpawnState lastSpawnState;

    @Shadow
    @Final
    private DistanceManager distanceManager;

    @Shadow
    protected abstract void getFullChunk(long chunkPosition, Consumer<LevelChunk> fullChunkConsumer);

    @Shadow
    @Final
    ServerLevel level;

    @Shadow
    private long lastInhabitedUpdate;

    @Shadow
    private boolean spawnEnemies;

    @Shadow
    private boolean spawnFriendlies;

    @Inject(
            method = "getChunk(IILnet/minecraft/world/level/chunk/status/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/ChunkAccess;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void async$getChunk(
            int chunkX,
            int chunkZ,
            ChunkStatus leastStatus,
            boolean create,
            CallbackInfoReturnable<ChunkAccess> callbackInfo
    ) {
        if (Thread.currentThread() == this.mainThread) {
            return;
        }
        if (!ParallelProcessor.isServerExecutionThread()) {
            return;
        }

        MinecraftServer minecraftServer = ParallelProcessor.getServer();
        int currentTick = minecraftServer != null
                ? minecraftServer.getTickCount()
                : Integer.MIN_VALUE;
        long positionKey = ChunkPos.asLong(chunkX, chunkZ);
        LastChunkCache lastChunkCache = LAST_CHUNK.get();
        ChunkAccess lastChunk = lastChunkCache.find(
                this,
                positionKey,
                leastStatus,
                currentTick
        );
        if (lastChunk != null) {
            callbackInfo.setReturnValue(lastChunk);
            return;
        }

        ChunkAccess access = async$tryGetChunk(chunkX, chunkZ, leastStatus);
        if (access != null) {
            lastChunkCache.store(this, positionKey, leastStatus, access, currentTick);
            callbackInfo.setReturnValue(access);
            return;
        }

        CompletableFuture<ChunkResult<ChunkAccess>> chunkFuture = CompletableFuture.supplyAsync(
                () -> this.getChunkFutureMainThread(chunkX, chunkZ, leastStatus, create),
                this.mainThreadProcessor
        ).thenCompose(future -> future);

        long deadline = System.nanoTime() + CHUNK_WAIT_TIMEOUT_NANOSECONDS;
        while (!chunkFuture.isDone()) {
            if (System.nanoTime() > deadline) {
                chunkFuture.cancel(false);
                ASYNC_LOGGER.warn(
                        "Timed out after 60s waiting for chunk [{}, {}] at status {}; falling back to the vanilla blocking path",
                        chunkX,
                        chunkZ,
                        leastStatus
                );
                return;
            }
            ChunkAccess cachedChunk = async$tryGetChunk(chunkX, chunkZ, leastStatus);
            if (cachedChunk != null) {
                chunkFuture.cancel(false);
                callbackInfo.setReturnValue(cachedChunk);
                return;
            }
            LockSupport.parkNanos(CHUNK_WAIT_PARK_NANOSECONDS);
        }

        ChunkAccess chunk = chunkFuture.join().orElse(null);
        if (chunk instanceof ImposterProtoChunk imposterProtoChunk) {
            chunk = imposterProtoChunk.getWrapped();
        }
        callbackInfo.setReturnValue(chunk);
    }

    @Unique
    private @Nullable ChunkAccess async$tryGetChunk(
            int chunkX,
            int chunkZ,
            ChunkStatus leastStatus
    ) {
        ChunkHolder chunkHolder = this.getVisibleChunkIfPresent(ChunkPos.asLong(chunkX, chunkZ));
        if (chunkHolder == null) {
            return null;
        }

        ChunkAccess chunk = chunkHolder.getChunkIfPresent(leastStatus);
        if (chunk instanceof ImposterProtoChunk imposterProtoChunk) {
            return imposterProtoChunk.getWrapped();
        }
        return chunk;
    }

    @Inject(method = "getChunkNow", at = @At("HEAD"), cancellable = true)
    private void async$getChunkNow(
            int chunkX,
            int chunkZ,
            CallbackInfoReturnable<LevelChunk> callbackInfo
    ) {
        if (Thread.currentThread() == this.mainThread) {
            return;
        }
        if (!ParallelProcessor.isServerExecutionThread()) {
            return;
        }

        ChunkAccess chunk = async$tryGetChunk(chunkX, chunkZ, ChunkStatus.FULL);
        if (chunk instanceof LevelChunk levelChunk) {
            callbackInfo.setReturnValue(levelChunk);
        }
    }

    @WrapMethod(method = "tickChunks()V")
    private void async$tickChunks(Operation<Void> original) {
        long gameTime = this.level.getGameTime();
        long inhabitedTimeDelta = gameTime - this.lastInhabitedUpdate;
        this.lastInhabitedUpdate = gameTime;
        if (this.level.isDebug()) {
            return;
        }

        ProfilerFiller profiler = this.level.getProfiler();
        profiler.push("pollingChunks");
        profiler.push("filteringLoadedChunks");
        List<ServerChunkCache.ChunkAndHolder> chunks = Lists.newArrayListWithCapacity(
                this.chunkMap.size()
        );

        for (ChunkHolder chunkHolder : this.chunkMap.getChunks()) {
            LevelChunk chunk = chunkHolder.getTickingChunk();
            if (chunk != null) {
                chunks.add(new ServerChunkCache.ChunkAndHolder(chunk, chunkHolder));
            }
        }

        if (this.level.tickRateManager().runsNormally()) {
            profiler.popPush("naturalSpawnCount");
            int spawnChunkCount = this.distanceManager.getNaturalSpawnChunkCount();
            this.lastSpawnState = NaturalSpawner.createState(
                    spawnChunkCount,
                    this.level.getAllEntities(),
                    this::getFullChunk,
                    new LocalMobCapCalculator(this.chunkMap)
            );

            profiler.popPush("spawnAndTick");
            boolean spawnMobs = this.level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING);
            int randomTickSpeed = this.level.getGameRules().getInt(GameRules.RULE_RANDOMTICKING);
            boolean runSpawnCycle = this.level.getLevelData().getGameTime() % 400L == 0L;
            Util.shuffle(chunks, this.level.random);

            NaturalSpawner.SpawnState spawnState = this.lastSpawnState;
            if (!AsyncConfig.disabled
                    && AsyncConfig.enableAsyncSpawn
                    && ParallelProcessor.executor != null
                    && !ParallelProcessor.executor.isShutdown()
                    && !ParallelProcessor.executor.isTerminated()) {
                async$populatePlayersNearChunks(chunks, spawnState, spawnMobs);
                CompletableFuture.runAsync(
                        () -> async$spawnAndTickChunks(
                                chunks,
                                spawnState,
                                inhabitedTimeDelta,
                                spawnMobs,
                                randomTickSpeed,
                                runSpawnCycle
                        ),
                        ParallelProcessor.BACKGROUND
                ).exceptionally(exception -> {
                    ASYNC_LOGGER.error("Error in async entity spawning", exception);
                    return null;
                });
            } else {
                async$spawnAndTickChunks(
                        chunks,
                        spawnState,
                        inhabitedTimeDelta,
                        spawnMobs,
                        randomTickSpeed,
                        runSpawnCycle
                );
            }

            profiler.popPush("customSpawners");
            if (spawnMobs) {
                this.level.tickCustomSpawners(this.spawnEnemies, this.spawnFriendlies);
            }
        }

        profiler.popPush("broadcast");
        chunks.forEach(entry -> entry.holder().broadcastChanges(entry.chunk()));
        profiler.pop();
        profiler.pop();
    }

    @Unique
    private void async$populatePlayersNearChunks(
            List<ServerChunkCache.ChunkAndHolder> chunks,
            NaturalSpawner.SpawnState spawnState,
            boolean spawnMobs
    ) {
        if (!spawnMobs || (!this.spawnEnemies && !this.spawnFriendlies)) {
            return;
        }

        for (ServerChunkCache.ChunkAndHolder entry : chunks) {
            ChunkPos chunkPosition = entry.chunk().getPos();
            List<ServerPlayer> nearbyPlayers = async$playersCloseForSpawning(chunkPosition);
            spawnState.localMobCapCalculator.playersNearChunk.put(
                    chunkPosition.toLong(),
                    nearbyPlayers
            );
        }
    }

    @Unique
    private List<ServerPlayer> async$playersCloseForSpawning(ChunkPos chunkPosition) {
        List<ServerPlayer> nearbyPlayers = this.chunkMap.getPlayersCloseForSpawning(chunkPosition);
        if (!nearbyPlayers.isEmpty()) {
            return nearbyPlayers;
        }

        List<ServerPlayer> computedPlayers = new ArrayList<>();
        double chunkCenterX = chunkPosition.getMinBlockX() + 8.0;
        double chunkCenterZ = chunkPosition.getMinBlockZ() + 8.0;
        for (ServerPlayer player : this.level.players()) {
            if (player.isSpectator()) {
                continue;
            }
            double horizontalDistanceX = player.getX() - chunkCenterX;
            double horizontalDistanceZ = player.getZ() - chunkCenterZ;
            double squaredDistance = horizontalDistanceX * horizontalDistanceX
                    + horizontalDistanceZ * horizontalDistanceZ;
            if (squaredDistance < SPAWN_PLAYER_DISTANCE_SQUARED) {
                computedPlayers.add(player);
            }
        }
        return computedPlayers;
    }

    @Unique
    private void async$spawnAndTickChunks(
            List<ServerChunkCache.ChunkAndHolder> chunks,
            NaturalSpawner.SpawnState spawnState,
            long inhabitedTimeDelta,
            boolean spawnMobs,
            int randomTickSpeed,
            boolean runSpawnCycle
    ) {
        for (ServerChunkCache.ChunkAndHolder entry : chunks) {
            LevelChunk chunk = entry.chunk();
            ChunkPos chunkPosition = chunk.getPos();
            if (!this.level.isNaturalSpawningAllowed(chunkPosition)
                    || !this.chunkMap.anyPlayerCloseEnoughForSpawning(chunkPosition)) {
                continue;
            }

            chunk.incrementInhabitedTime(inhabitedTimeDelta);
            if (spawnMobs
                    && (this.spawnEnemies || this.spawnFriendlies)
                    && this.level.getWorldBorder().isWithinBounds(chunkPosition)) {
                NaturalSpawner.spawnForChunk(
                        this.level,
                        chunk,
                        spawnState,
                        this.spawnFriendlies,
                        this.spawnEnemies,
                        runSpawnCycle
                );
            }
            if (this.level.shouldTickBlocksAt(chunkPosition.toLong())) {
                this.level.tickChunk(chunk, randomTickSpeed);
            }
        }
    }
}
