package com.axalotl.async.common.mixin.server;

import com.axalotl.async.common.ParallelProcessor;
import com.axalotl.async.common.config.AsyncConfig;
import com.axalotl.async.common.parallelised.utils.CarpetCompat;
import com.axalotl.async.common.parallelised.utils.IAsyncChunkCache;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.ImmediateEventExecutor;
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
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;

@Mixin(value = ServerChunkCache.class, priority = 1500)
public abstract class ServerChunkCacheMixin extends ChunkSource implements IAsyncChunkCache {

    @Shadow @Final public ChunkMap chunkMap;
    @Shadow @Final Thread mainThread;
    @Shadow @Final public ServerChunkCache.MainThreadExecutor mainThreadProcessor;
    @Shadow @Final private DistanceManager distanceManager;
    @Shadow @Final private ServerLevel level;
    @Shadow private volatile NaturalSpawner.@Nullable SpawnState lastSpawnState;
    @Shadow private boolean spawnEnemies;
    @Shadow private final Set<ChunkHolder> chunkHoldersToBroadcast = ConcurrentHashMap.newKeySet();
    @Shadow private final List<LevelChunk> spawningChunks = Collections.synchronizedList(new ArrayList<>());

    @Shadow public abstract @Nullable ChunkHolder getVisibleChunkIfPresent(long pos);
    @Shadow protected abstract CompletableFuture<ChunkResult<ChunkAccess>> getChunkFutureMainThread(int x, int z, ChunkStatus leastStatus, boolean create);
    @Shadow protected abstract void getFullChunk(long chunkPos, Consumer<LevelChunk> fullChunkGetter);
    @Shadow public abstract void tickSpawningChunk(LevelChunk chunk, long timeInhabited, List<MobCategory> spawnCategories, NaturalSpawner.SpawnState spawnState);

    @Unique
    private static final int ASYNC_CACHE_SIZE = 4;

    @Unique
    private final ThreadLocal<long[]> async$cachePos = ThreadLocal.withInitial(() -> {
        long[] arr = new long[ASYNC_CACHE_SIZE];
        Arrays.fill(arr, ChunkPos.INVALID_CHUNK_POS);
        return arr;
    });

    @Unique
    private final ThreadLocal<ChunkStatus[]> async$cacheStatus = ThreadLocal.withInitial(() -> new ChunkStatus[ASYNC_CACHE_SIZE]);

    @Unique
    private final ThreadLocal<ChunkAccess[]> async$cacheChunk = ThreadLocal.withInitial(() -> new ChunkAccess[ASYNC_CACHE_SIZE]);

    @Unique
    private final ConcurrentHashMap<Long, DefaultPromise<ChunkAccess>> async$chunkPromises = new ConcurrentHashMap<>();

    @Unique
    private volatile NaturalSpawner.SpawnState async$latestState = null;

    @Unique
    private volatile CompletableFuture<Void> async$createStateFuture = null;

    @Inject(
            method = "getChunk(IILnet/minecraft/world/level/chunk/status/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/ChunkAccess;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void async$getChunk(int x, int z, ChunkStatus leastStatus, boolean create, CallbackInfoReturnable<ChunkAccess> cir) {
        if (Thread.currentThread() == this.mainThread) return;

        long pos = ChunkPos.asLong(x, z);
        long[] cPos = async$cachePos.get();
        ChunkStatus[] cStatus = async$cacheStatus.get();
        ChunkAccess[] cChunk = async$cacheChunk.get();

        for (int j = 0; j < ASYNC_CACHE_SIZE; ++j) {
            if (pos == cPos[j] && leastStatus == cStatus[j]) {
                ChunkAccess cached = cChunk[j];
                if (cached != null || !create) {
                    cir.setReturnValue(cached);
                    return;
                }
            }
        }

        ChunkAccess found = async$probeHolder(pos, leastStatus);
        if (found != null) {
            async$storeInCache(cPos, cStatus, cChunk, pos, leastStatus, found);
            cir.setReturnValue(found);
            return;
        }

        if (!create) {
            cir.setReturnValue(null);
            return;
        }

        ChunkAccess result = async$loadChunk(x, z, leastStatus, pos);
        if (result != null) {
            async$storeInCache(cPos, cStatus, cChunk, pos, leastStatus, result);
        }
        cir.setReturnValue(result);
    }

    @Unique
    private ChunkAccess async$loadChunk(int x, int z, ChunkStatus status, long pos) {
        long key = pos * 17L + status.getIndex();

        DefaultPromise<ChunkAccess> newPromise = new DefaultPromise<>(ImmediateEventExecutor.INSTANCE);
        DefaultPromise<ChunkAccess> existing = async$chunkPromises.putIfAbsent(key, newPromise);

        DefaultPromise<ChunkAccess> promise;
        if (existing == null) {
            promise = newPromise;
            async$initiateLoad(x, z, status, promise, key);
        } else {
            promise = existing;
        }

        while (!promise.isDone()) {
            ChunkAccess found = async$probeHolder(pos, status);
            if (found != null) {
                return found;
            }
            LockSupport.parkNanos(50_000L);
        }

        return promise.getNow();
    }

    @Unique
    private void async$initiateLoad(int x, int z, ChunkStatus status,
                                    DefaultPromise<ChunkAccess> promise, long key) {
        CompletableFuture.supplyAsync(
                () -> this.getChunkFutureMainThread(x, z, status, true),
                this.mainThreadProcessor
        ).thenCompose(f -> f).whenComplete((result, error) -> {
            if (error != null) {
                promise.tryFailure(error);
                async$chunkPromises.remove(key);
                return;
            }

            ChunkAccess chunk = result != null ? result.orElse(null) : null;
            if (chunk != null) {
                promise.trySuccess(async$unwrapImposter(chunk));
            } else {
                promise.tryFailure(new IllegalStateException("Chunk load returned null"));
            }
            async$chunkPromises.remove(key);
        });
    }

    @Unique
    private @Nullable ChunkAccess async$probeHolder(long pos, ChunkStatus status) {
        ChunkHolder holder = this.getVisibleChunkIfPresent(pos);
        if (holder == null) return null;

        ChunkAccess chunk = holder.getChunkIfPresent(status);
        if (chunk != null) return async$unwrapImposter(chunk);

        chunk = holder.getChunkIfPresentUnchecked(status);
        if (chunk != null) return async$unwrapImposter(chunk);

        if (status == ChunkStatus.FULL) {
            LevelChunk ticking = holder.getTickingChunk();
            if (ticking != null) return ticking;
        }

        ChunkAccess latest = holder.getLatestChunk();
        if (latest != null) {
            ChunkStatus latestStatus = latest.getPersistedStatus();
            if (!status.isAfter(latestStatus)) {
                return async$unwrapImposter(latest);
            }
        }
        return null;
    }

    @Unique
    private static ChunkAccess async$unwrapImposter(ChunkAccess chunk) {
        if (chunk instanceof ImposterProtoChunk imposter) {
            return imposter.getWrapped();
        }
        return chunk;
    }

    @Unique
    private static void async$storeInCache(long[] cPos, ChunkStatus[] cStatus, ChunkAccess[] cChunk,
                                           long pos, ChunkStatus status, ChunkAccess chunk) {
        for (int i = ASYNC_CACHE_SIZE - 1; i > 0; --i) {
            cPos[i] = cPos[i - 1];
            cStatus[i] = cStatus[i - 1];
            cChunk[i] = cChunk[i - 1];
        }
        cPos[0] = pos;
        cStatus[0] = status;
        cChunk[0] = chunk;
    }

    @Override
    public void async$clearWorkerCache() {
        long[] cPos = async$cachePos.get();
        ChunkStatus[] cStatus = async$cacheStatus.get();
        ChunkAccess[] cChunk = async$cacheChunk.get();
        Arrays.fill(cPos, ChunkPos.INVALID_CHUNK_POS);
        Arrays.fill(cStatus, null);
        Arrays.fill(cChunk, null);
    }

    @Inject(method = "getChunkNow", at = @At("HEAD"), cancellable = true)
    private void async$getChunkNow(int chunkX, int chunkZ, CallbackInfoReturnable<LevelChunk> cir) {
        if (Thread.currentThread() == this.mainThread) return;

        ChunkHolder holder = this.getVisibleChunkIfPresent(ChunkPos.asLong(chunkX, chunkZ));
        if (holder == null) {
            cir.setReturnValue(null);
            return;
        }

        ChunkAccess chunk = holder.getChunkIfPresent(ChunkStatus.FULL);
        if (chunk instanceof LevelChunk levelChunk) {
            cir.setReturnValue(levelChunk);
            return;
        }

        LevelChunk ticking = holder.getTickingChunk();
        if (ticking != null) {
            cir.setReturnValue(ticking);
            return;
        }

        cir.setReturnValue(null);
    }

    @Inject(method = "tickChunks()V", at = @At("TAIL"))
    private void async$tickChunksCreateState(CallbackInfo ci) {
        if (AsyncConfig.disabled || !AsyncConfig.enableAsyncSpawn) return;

        CompletableFuture<Void> pending = async$createStateFuture;
        if (pending != null && !pending.isDone()) return;

        final int i = distanceManager.getNaturalSpawnChunkCount();
        async$createStateFuture = CompletableFuture.runAsync(() -> async$latestState = NaturalSpawner.createState(
                i, this.level.getAllEntities(),
                this::getFullChunk, new LocalMobCapCalculator(this.chunkMap)), ParallelProcessor.tickPool).whenComplete((result, error) -> {
            if (error != null) {
                ParallelProcessor.LOGGER.error("Failed to calculate spawn state", error);
            }
        });
    }

    @WrapMethod(method = "tickChunks(Lnet/minecraft/util/profiling/ProfilerFiller;J)V")
    private void async$tickChunksSpawn(ProfilerFiller profiler, long timeInhabited, Operation<Void> original) {
        profiler.push("naturalSpawnCount");
        int i = this.distanceManager.getNaturalSpawnChunkCount();

        NaturalSpawner.SpawnState state = async$latestState;
        if (state == null) {
            state = NaturalSpawner.createState(i, this.level.getAllEntities(),
                    this::getFullChunk, new LocalMobCapCalculator(this.chunkMap));
            async$latestState = state;
        }
        lastSpawnState = state;

        CarpetCompat.updateChunkCount(this.level.dimension(), i);

        boolean flag = this.level.getGameRules().get(GameRules.SPAWN_MOBS);
        int j = this.level.getGameRules().get(GameRules.RANDOM_TICK_SPEED);
        List<MobCategory> list;
        if (flag) {
            boolean flag1 = this.level.getGameTime() % 400L == 0L;
            list = NaturalSpawner.getFilteredSpawningCategories(
                    Objects.requireNonNull(lastSpawnState), true, this.spawnEnemies, flag1);
        } else {
            list = List.of();
        }

        profiler.popPush("tickSpawningChunks");

        if (!AsyncConfig.disabled && AsyncConfig.enableAsyncSpawn) {
            NaturalSpawner.SpawnState currentState = lastSpawnState;
            if (currentState != null) {
                CompletableFuture.runAsync(() -> {
                    List<LevelChunk> chunks = new ArrayList<>();
                    this.chunkMap.collectSpawningChunks(chunks);
                    Util.shuffle(chunks, this.level.random);
                    for (LevelChunk levelchunk : chunks) {
                        if (levelchunk != null) {
                            this.tickSpawningChunk(levelchunk, timeInhabited, list, currentState);
                        }
                    }
                }, ParallelProcessor.tickPool).whenComplete((result, error) -> {
                    if (error != null) {
                        ParallelProcessor.LOGGER.error("Error in async entity spawning", error);
                    }
                });
            }
        } else {
            List<LevelChunk> list1 = this.spawningChunks;
            profiler.popPush("filteringSpawningChunks");
            this.chunkMap.collectSpawningChunks(list1);
            profiler.popPush("shuffleSpawningChunks");
            Util.shuffle(list1, this.level.random);
            profiler.popPush("tickSpawningChunks");

            for (LevelChunk levelchunk : list1) {
                this.tickSpawningChunk(levelchunk, timeInhabited, list, lastSpawnState);
            }
            list1.clear();
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