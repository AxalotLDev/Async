package com.axalotl.async.common.mixin.server;

import com.axalotl.async.common.ParallelProcessor;
import com.axalotl.async.common.config.AsyncConfig;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.level.*;
import net.minecraft.util.Util;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LocalMobCapCalculator;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.gamerules.GameRules;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;
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
    @Final
    public ServerChunkCache.MainThreadExecutor mainThreadProcessor;

    @Shadow
    public abstract @Nullable ChunkHolder getVisibleChunkIfPresent(long pos);

    @Shadow
    protected abstract CompletableFuture<ChunkResult<ChunkAccess>> getChunkFutureMainThread(int x, int z, ChunkStatus leastStatus, boolean create);

    @Shadow
    private final Set<ChunkHolder> chunkHoldersToBroadcast = ConcurrentHashMap.newKeySet();

    @Shadow
    @Final
    private DistanceManager distanceManager;

    @Shadow
    private volatile NaturalSpawner.@Nullable SpawnState lastSpawnState;

    @Shadow
    @Final
    private ServerLevel level;

    @Shadow
    protected abstract void getFullChunk(long chunkPos, Consumer<LevelChunk> fullChunkGetter);

    @Shadow
    private final List<LevelChunk> spawningChunks = Collections.synchronizedList(new ArrayList<>());

    @Shadow
    private boolean spawnEnemies;

    @Unique
    private boolean async$firstRunSpawnCounts = true;

    @Unique
    private final AtomicBoolean async$spawnCountsReady = new AtomicBoolean(false);

    @Shadow
    public abstract void tickSpawningChunk(LevelChunk chunk, long timeInhabited, List<MobCategory> spawnCategories, NaturalSpawner.SpawnState spawnState);

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

        while (!future.isDone()) {
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

    @Inject(method = "tickChunks()V", at = @At("TAIL"))
    private void tickChunks(CallbackInfo ci) {
        if (AsyncConfig.disabled || !AsyncConfig.enableAsyncSpawn) return;

        if (async$firstRunSpawnCounts) {
            async$firstRunSpawnCounts = false;
            async$spawnCountsReady.set(true);
        }
        if (async$spawnCountsReady.getAndSet(false)) {
            final int i = distanceManager.getNaturalSpawnChunkCount();
            ParallelProcessor.tickPool.submit(() -> {
                lastSpawnState = NaturalSpawner.createState(i, this.level.getAllEntities(), this::getFullChunk, new LocalMobCapCalculator(this.chunkMap));
                async$spawnCountsReady.set(true);
            });
        }
    }

    @WrapMethod(method = "tickChunks(Lnet/minecraft/util/profiling/ProfilerFiller;J)V")
    private void tickChunksSpawn(ProfilerFiller profiler, long timeInhabited, Operation<Void> original) {
        profiler.push("naturalSpawnCount");
        int i = this.distanceManager.getNaturalSpawnChunkCount();

        if (AsyncConfig.disabled || !AsyncConfig.enableAsyncSpawn || async$firstRunSpawnCounts) {
            lastSpawnState = NaturalSpawner.createState(i, this.level.getAllEntities(), this::getFullChunk, new LocalMobCapCalculator(this.chunkMap));
        }

        boolean flag = this.level.getGameRules().get(GameRules.SPAWN_MOBS);
        int j = this.level.getGameRules().get(GameRules.RANDOM_TICK_SPEED);
        List<MobCategory> list;
        if (flag) {
            boolean flag1 = this.level.getGameTime() % 400L == 0L;
            list = NaturalSpawner.getFilteredSpawningCategories(Objects.requireNonNull(lastSpawnState), true, this.spawnEnemies, flag1);
        } else {
            list = List.of();
        }

        if (!AsyncConfig.disabled && AsyncConfig.enableAsyncSpawn) {
            NaturalSpawner.SpawnState currentState = lastSpawnState;
            if (currentState != null) {
                CompletableFuture.runAsync(() -> {
                    profiler.popPush("filteringSpawningChunks");
                    List<LevelChunk> chunks = new ArrayList<>();
                    profiler.popPush("shuffleSpawningChunks");
                    this.chunkMap.collectSpawningChunks(chunks);
                    Util.shuffle(chunks, this.level.random);
                    profiler.popPush("tickSpawningChunks");
                    for (LevelChunk levelchunk : chunks) {
                        if (levelchunk != null) {
                            this.tickSpawningChunk(levelchunk, timeInhabited, list, currentState);
                        }
                    }
                }, ParallelProcessor.tickPool).whenComplete((r, e) -> {
                    if (e != null) {
                        ParallelProcessor.LOGGER.error("Error in async entity spawning, switching to synchronous", e);
                        List<LevelChunk> list1 = this.spawningChunks;
                        try {
                            profiler.popPush("filteringSpawningChunks");
                            this.chunkMap.collectSpawningChunks(list1);
                            profiler.popPush("shuffleSpawningChunks");
                            Util.shuffle(list1, this.level.random);
                            profiler.popPush("tickSpawningChunks");

                            for (LevelChunk levelchunk : list1) {
                                this.tickSpawningChunk(levelchunk, timeInhabited, list, lastSpawnState);
                            }
                        } finally {
                            list1.clear();
                        }
                    }
                });
            }
        } else {
            List<LevelChunk> list1 = this.spawningChunks;
            try {
                profiler.popPush("filteringSpawningChunks");
                this.chunkMap.collectSpawningChunks(list1);
                profiler.popPush("shuffleSpawningChunks");
                Util.shuffle(list1, this.level.random);
                profiler.popPush("tickSpawningChunks");

                for (LevelChunk levelchunk : list1) {
                    this.tickSpawningChunk(levelchunk, timeInhabited, list, lastSpawnState);
                }
            } finally {
                list1.clear();
            }
        }

        profiler.popPush("tickTickingChunks");
        this.chunkMap.forEachBlockTickingChunk((p_401730_) -> this.level.tickChunk(p_401730_, j));
        if (flag) {
            profiler.popPush("customSpawners");
            this.level.tickCustomSpawners(this.spawnEnemies);
        }
        profiler.pop();
    }
}