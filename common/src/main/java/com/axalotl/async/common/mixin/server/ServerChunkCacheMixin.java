package com.axalotl.async.common.mixin.server;

import com.axalotl.async.common.ParallelProcessor;
import com.axalotl.async.common.config.AsyncConfig;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.*;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobCategory;
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
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;

@Mixin(value = ServerChunkCache.class, priority = 1500)
public abstract class ServerChunkCacheMixin extends ChunkSource {

    // ==================== Shadows ====================

    @Shadow @Final public ChunkMap chunkMap;
    @Shadow @Final
    private Thread mainThread;
    @Shadow @Final public ServerChunkCache.MainThreadExecutor mainThreadProcessor;
    @Shadow @Final private DistanceManager distanceManager;
    @Shadow @Final private ServerLevel level;
    @Shadow private volatile NaturalSpawner.@Nullable SpawnState lastSpawnState;
    @Shadow private boolean spawnEnemies;
    @Shadow @Final @Mutable private Set<ChunkHolder> chunkHoldersToBroadcast;
    @Shadow @Final private List<LevelChunk> spawningChunks; // ServerCore кеширует этот список

    @Shadow public abstract @Nullable ChunkHolder getVisibleChunkIfPresent(long key);
    @Shadow protected abstract CompletableFuture<ChunkResult<ChunkAccess>> getChunkFutureMainThread(int x, int z, ChunkStatus targetStatus, boolean loadOrGenerate);
    @Shadow protected abstract void getFullChunk(long chunkKey, Consumer<LevelChunk> output);
    @Shadow public abstract void tickSpawningChunk(LevelChunk chunk, long timeDiff, List<MobCategory> spawningCategories, NaturalSpawner.SpawnState spawnCookie);


    @Inject(method = "<init>", at = @At("TAIL"))
    private void async$replaceWithConcurrentSet(CallbackInfo ci) {
        this.chunkHoldersToBroadcast = ConcurrentHashMap.newKeySet();
    }

    @Unique private static final Logger LOGGER = LoggerFactory.getLogger("Async-ChunkTick");

    @Unique private volatile CompletableFuture<Void> async$spawnFuture;
    @Unique private long async$capturedTimeDiff;
    @Unique private boolean async$spawnLaunchedThisTick;

    @Unique private static final long INITIAL_PARK_NS = 50_000L;
    @Unique private static final long MAX_PARK_NS = 1_000_000L;


    @Inject(
            method = "getChunk(IILnet/minecraft/world/level/chunk/status/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/ChunkAccess;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void async$getChunk(int x, int z, ChunkStatus targetStatus, boolean loadOrGenerate, CallbackInfoReturnable<ChunkAccess> cir) {
        if (Thread.currentThread() == this.mainThread) return;

        long pos = ChunkPos.pack(x, z);
        ChunkHolder holder = this.getVisibleChunkIfPresent(pos);

        if (holder != null) {
            // Fast path: FULL status — try tickingChunk first (single field read,
            // skips futures array + CompletableFuture.getNow + ChunkResult unwrap)
            if (targetStatus == ChunkStatus.FULL) {
                LevelChunk ticking = holder.getTickingChunk();
                if (ticking != null) {
                    cir.setReturnValue(ticking);
                    return;
                }
            }

            ChunkAccess chunk = holder.getChunkIfPresent(targetStatus);
            if (chunk != null) {
                cir.setReturnValue(async$unwrap(chunk));
                return;
            }

            chunk = holder.getChunkIfPresentUnchecked(targetStatus);
            if (chunk != null) {
                cir.setReturnValue(async$unwrap(chunk));
                return;
            }

            CompletableFuture<?> existingFuture = async$findPendingFuture(holder, targetStatus);
            if (existingFuture != null) {
                cir.setReturnValue(async$awaitWithProbing(existingFuture, pos, targetStatus));
                return;
            }
        }

        if (!loadOrGenerate) {
            cir.setReturnValue(null);
            return;
        }
        CompletableFuture<?> ticketTask = CompletableFuture.runAsync(() -> this.getChunkFutureMainThread(x, z, targetStatus, true), this.mainThreadProcessor);
        cir.setReturnValue(async$awaitAfterTicket(ticketTask, pos, targetStatus));
    }

    @Inject(method = "getChunkNow", at = @At("HEAD"), cancellable = true)
    private void async$getChunkNow(int x, int z, CallbackInfoReturnable<LevelChunk> cir) {
        if (Thread.currentThread() == this.mainThread) return;

        ChunkHolder holder = this.getVisibleChunkIfPresent(ChunkPos.pack(x, z));
        if (holder == null) {
            cir.setReturnValue(null);
            return;
        }

        // Fast path: tickingChunk is a single field read
        LevelChunk ticking = holder.getTickingChunk();
        if (ticking != null) {
            cir.setReturnValue(ticking);
            return;
        }

        // Fallback: check futures array
        ChunkAccess chunk = holder.getChunkIfPresent(ChunkStatus.FULL);
        if (chunk instanceof LevelChunk lc) {
            cir.setReturnValue(lc);
            return;
        }

        cir.setReturnValue(null);
    }

    @Unique
    private static @Nullable ChunkAccess async$extractReady(ChunkHolder holder, ChunkStatus status) {
        if (status == ChunkStatus.FULL) {
            LevelChunk ticking = holder.getTickingChunk();
            if (ticking != null) return ticking;
        }

        ChunkAccess chunk = holder.getChunkIfPresent(status);
        if (chunk != null) return async$unwrap(chunk);

        chunk = holder.getChunkIfPresentUnchecked(status);
        if (chunk != null) return async$unwrap(chunk);

        return null;
    }

    @Unique
    private static @Nullable CompletableFuture<?> async$findPendingFuture(ChunkHolder holder, ChunkStatus status) {
        AtomicReferenceArray<?> futures = holder.futures;
        CompletableFuture<?> genFuture = (CompletableFuture<?>) futures.get(status.getIndex());
        if (genFuture != null && !genFuture.isDone()) {
            return genFuture;
        }
        if (status == ChunkStatus.FULL) {
            CompletableFuture<?> fullFuture = holder.getFullChunkFuture();
            if (!fullFuture.isDone()) {
                return fullFuture;
            }
        }
        return null;
    }

    @Unique
    private @Nullable ChunkAccess async$awaitWithProbing(CompletableFuture<?> future, long pos, ChunkStatus status) {
        long sleepNs = INITIAL_PARK_NS;
        while (!future.isDone()) {
            ChunkHolder holder = this.getVisibleChunkIfPresent(pos);
            if (holder != null) {
                ChunkAccess ready = async$extractReady(holder, status);
                if (ready != null) return ready;
            }
            LockSupport.parkNanos(sleepNs);
            sleepNs = Math.min(sleepNs << 1, MAX_PARK_NS);
        }
        return async$extractFromFuture(future);
    }

    @Unique
    private @Nullable ChunkAccess async$awaitAfterTicket(CompletableFuture<?> ticketTask,
                                                         long pos, ChunkStatus status) {
        long sleepNs = INITIAL_PARK_NS;
        for (;;) {
            if (ticketTask.isCompletedExceptionally()) {
                ticketTask.join();
            }

            ChunkHolder holder = this.getVisibleChunkIfPresent(pos);
            if (holder != null) {
                ChunkAccess ready = async$extractReady(holder, status);
                if (ready != null) return ready;

                CompletableFuture<?> directFuture = async$findPendingFuture(holder, status);
                if (directFuture != null) {
                    return async$awaitWithProbing(directFuture, pos, status);
                }

                ChunkAccess fromHolder = async$extractCompleted(holder, status);
                if (fromHolder != null) return fromHolder;
            }

            LockSupport.parkNanos(sleepNs);
            sleepNs = Math.min(sleepNs << 1, MAX_PARK_NS);
        }
    }

    @Unique
    private static @Nullable ChunkAccess async$extractCompleted(ChunkHolder holder, ChunkStatus status) {
        AtomicReferenceArray<?> futures = holder.futures;
        CompletableFuture<?> future = (CompletableFuture<?>) futures.get(status.getIndex());
        if (future != null && future.isDone()) {
            return async$extractFromFuture(future);
        }
        if (status == ChunkStatus.FULL) {
            CompletableFuture<?> fullFuture = holder.getFullChunkFuture();
            if (fullFuture.isDone()) {
                return async$extractFromFuture(fullFuture);
            }
        }
        return null;
    }

    @Unique
    private static @Nullable ChunkAccess async$extractFromFuture(CompletableFuture<?> future) {
        Object raw = future.join();
        if (raw instanceof ChunkResult<?> result) {
            Object chunk = result.orElse(null);
            if (chunk instanceof ChunkAccess ca) return async$unwrap(ca);
        }
        return null;
    }

    @Unique
    private static ChunkAccess async$unwrap(ChunkAccess chunk) {
        if (chunk instanceof ImposterProtoChunk imposter) {
            return imposter.getWrapped();
        }
        return chunk;
    }


    @WrapOperation(
            method = "tickChunks(Lnet/minecraft/util/profiling/ProfilerFiller;J)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/NaturalSpawner;createState(ILjava/lang/Iterable;Lnet/minecraft/world/level/NaturalSpawner$ChunkGetter;Lnet/minecraft/world/level/LocalMobCapCalculator;)Lnet/minecraft/world/level/NaturalSpawner$SpawnState;")
    )
    private NaturalSpawner.SpawnState async$captureTimeAndPassthrough(
            int spawnableChunkCount, Iterable<Entity> entities, NaturalSpawner.ChunkGetter chunkGetter,
            LocalMobCapCalculator localMobCapCalculator, Operation<NaturalSpawner.SpawnState> original,
            ProfilerFiller profiler, long timeDiff) {
        async$capturedTimeDiff = timeDiff;
        async$spawnLaunchedThisTick = false;
        async$spawnFuture = null;
        return original.call(spawnableChunkCount, entities, chunkGetter, localMobCapCalculator);
    }

    @WrapOperation(
            method = "tickChunks(Lnet/minecraft/util/profiling/ProfilerFiller;J)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerChunkCache;tickSpawningChunk(Lnet/minecraft/world/level/chunk/LevelChunk;JLjava/util/List;Lnet/minecraft/world/level/NaturalSpawner$SpawnState;)V")
    )
    private void async$interceptTickSpawning(ServerChunkCache instance, LevelChunk chunk,
                                             long timeDiff, List<MobCategory> spawningCategories,
                                             NaturalSpawner.SpawnState spawnCookie,
                                             Operation<Void> original) {
        if (AsyncConfig.disabled || !AsyncConfig.enableAsyncSpawn) {
            original.call(instance, chunk, timeDiff, spawningCategories, spawnCookie);
            return;
        }

        if (async$spawnLaunchedThisTick) return;
        async$spawnLaunchedThisTick = true;

        List<LevelChunk> list = this.spawningChunks;
        if (list == null || list.isEmpty()) return;

        LevelChunk[] chunks = list.toArray(new LevelChunk[0]);

        for (int i = chunks.length - 1; i > 0; i--) {
            int j = this.level.getRandom().nextInt(i + 1);
            LevelChunk tmp = chunks[i];
            chunks[i] = chunks[j];
            chunks[j] = tmp;
        }

        long capturedTimeDiff = async$capturedTimeDiff;

        it.unimi.dsi.fastutil.longs.LongOpenHashSet set =
                new it.unimi.dsi.fastutil.longs.LongOpenHashSet(chunks.length * 9);
        for (LevelChunk c : chunks) {
            ChunkPos cp = c.getPos();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    set.add(ChunkPos.pack(cp.x() + dx, cp.z() + dz));
                }
            }
        }
        ParallelProcessor.spawnableChunkPositions = set;

        int poolSize = ParallelProcessor.getPoolSize();
        int batchSize = Math.max(4, (chunks.length + poolSize - 1) / poolSize);
        int batchCount = (chunks.length + batchSize - 1) / batchSize;

        CompletableFuture<?>[] futures = new CompletableFuture<?>[batchCount];
        ServerLevel lvl = this.level;

        for (int b = 0, idx = 0; b < chunks.length; b += batchSize, idx++) {
            int start = b;
            int end = Math.min(b + batchSize, chunks.length);

            futures[idx] = CompletableFuture.runAsync(() -> {
                for (int i = start; i < end; i++) {
                    LevelChunk c = chunks[i];
                    try {
                        c.incrementInhabitedTime(capturedTimeDiff);
                        NaturalSpawner.spawnForChunk(lvl, c, spawnCookie, spawningCategories);
                    } catch (Exception e) {
                        LOGGER.error("Exception spawning in chunk {}", c.getPos(), e);
                    }
                }
            }, ParallelProcessor.executor);
        }

        async$spawnFuture = CompletableFuture.allOf(futures);
    }


    @WrapOperation(
            method = "tickChunks(Lnet/minecraft/util/profiling/ProfilerFiller;J)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ChunkMap;forEachBlockTickingChunk(Ljava/util/function/Consumer;)V")
    )
    private void async$waitSpawnThenBlockTick(ChunkMap instance, Consumer<LevelChunk> tickingChunkConsumer,
                                              Operation<Void> original) {
        CompletableFuture<Void> spawnFut = async$spawnFuture;
        if (spawnFut != null) {
            async$pumpUntilDone(spawnFut);
            if (spawnFut.isCompletedExceptionally()) {
                spawnFut.join();
            }
            async$spawnFuture = null;
            ParallelProcessor.spawnableChunkPositions = null;
        }

        if (AsyncConfig.disabled || !AsyncConfig.enableAsyncRandomTicks) {
            original.call(instance, tickingChunkConsumer);
            return;
        }

        List<LevelChunk> tickChunks = new ArrayList<>();
        original.call(instance, (Consumer<LevelChunk>) tickChunks::add);

        if (tickChunks.isEmpty()) return;

        int poolSize = ParallelProcessor.getPoolSize();
        int batchSize = Math.max(1, (tickChunks.size() + poolSize - 1) / poolSize);
        int batchCount = (tickChunks.size() + batchSize - 1) / batchSize;
        CompletableFuture<?>[] futures = new CompletableFuture<?>[batchCount];

        for (int b = 0, idx = 0; b < tickChunks.size(); b += batchSize, idx++) {
            int start = b;
            int end = Math.min(b + batchSize, tickChunks.size());
            futures[idx] = CompletableFuture.runAsync(() -> {
                for (int j = start; j < end; j++) {
                    try {
                        tickingChunkConsumer.accept(tickChunks.get(j));
                    } catch (Exception e) {
                        LOGGER.error("Exception ticking chunk {}", tickChunks.get(j).getPos(), e);
                    }
                }
            }, ParallelProcessor.executor);
        }

        async$pumpUntilDone(CompletableFuture.allOf(futures));
    }

    @Unique
    private void async$pumpUntilDone(CompletableFuture<?> future) {
        while (!future.isDone()) {
            if (!this.level.getChunkSource().pollTask()) {
                Thread.onSpinWait();
            }
        }
        if (future.isCompletedExceptionally()) {
            future.join();
        }
    }
}