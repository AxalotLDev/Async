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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;

@Mixin(value = ServerChunkCache.class, priority = 1500)
public abstract class ServerChunkCacheMixin extends ChunkSource {

    @Shadow @Final public ChunkMap chunkMap;
    @Shadow @Final Thread mainThread;
    @Shadow @Final public ServerChunkCache.MainThreadExecutor mainThreadProcessor;
    @Shadow @Final private DistanceManager distanceManager;
    @Shadow @Final private ServerLevel level;
    @Shadow private volatile NaturalSpawner.@Nullable SpawnState lastSpawnState;
    @Shadow private boolean spawnEnemies;

    @Shadow public abstract @Nullable ChunkHolder getVisibleChunkIfPresent(long pos);
    @Shadow protected abstract CompletableFuture<ChunkResult<ChunkAccess>> getChunkFutureMainThread(int x, int z, ChunkStatus leastStatus, boolean create);
    @Shadow protected abstract void getFullChunk(long chunkPos, Consumer<LevelChunk> fullChunkGetter);
    @Shadow public abstract void tickSpawningChunk(LevelChunk chunk, long timeInhabited, List<MobCategory> spawnCategories, NaturalSpawner.SpawnState spawnState);

    @Unique private boolean async$firstRunSpawnCounts = true;
    @Unique private final AtomicBoolean async$spawnCountsReady = new AtomicBoolean(false);
    @Unique private volatile NaturalSpawner.@Nullable SpawnState async$latestState;
    @Unique private long async$timeInhabited;
    @Unique private volatile CompletableFuture<Void> async$spawnFuture;

    @Unique private static final long INITIAL_PARK_NS = 50_000L;
    @Unique private static final long MAX_PARK_NS = 1_000_000L;


    @Inject(
            method = "getChunk(IILnet/minecraft/world/level/chunk/status/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/ChunkAccess;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void async$getChunk(int x, int z, ChunkStatus leastStatus, boolean create, CallbackInfoReturnable<ChunkAccess> cir) {
        if (Thread.currentThread() == this.mainThread) return;

        long pos = ChunkPos.asLong(x, z);
        ChunkHolder holder = this.getVisibleChunkIfPresent(pos);

        if (holder != null) {
            ChunkAccess ready = async$extractReady(holder, leastStatus);
            if (ready != null) {
                cir.setReturnValue(ready);
                return;
            }

            CompletableFuture<?> existingFuture = async$findPendingFuture(holder, leastStatus);
            if (existingFuture != null) {
                cir.setReturnValue(async$awaitWithProbing(existingFuture, pos, leastStatus));
                return;
            }
        }

        if (!create) {
            cir.setReturnValue(null);
            return;
        }
        CompletableFuture<?> ticketTask = CompletableFuture.runAsync(() -> this.getChunkFutureMainThread(x, z, leastStatus, true), this.mainThreadProcessor);
        cir.setReturnValue(async$awaitAfterTicket(ticketTask, pos, leastStatus));
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
        if (chunk instanceof LevelChunk lc) {
            cir.setReturnValue(lc);
            return;
        }

        cir.setReturnValue(holder.getTickingChunk());
    }

    @Unique
    private @Nullable ChunkAccess async$awaitAfterTicket(
            CompletableFuture<?> ticketTask, long pos, ChunkStatus status
    ) {
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
    private static @Nullable ChunkAccess async$extractReady(ChunkHolder holder, ChunkStatus status) {
        ChunkAccess chunk = holder.getChunkIfPresent(status);
        if (chunk != null) return async$unwrap(chunk);

        chunk = holder.getChunkIfPresentUnchecked(status);
        if (chunk != null) return async$unwrap(chunk);
        if (status == ChunkStatus.FULL) {
            LevelChunk ticking = holder.getTickingChunk();
            if (ticking != null) return ticking;
        }
        return null;
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

    @Inject(method = "tickChunks()V", at = @At("TAIL"))
    private void async$scheduleSpawnStatePrecompute(CallbackInfo ci) {
        if (AsyncConfig.disabled || !AsyncConfig.enableAsyncSpawn) return;

        if (async$firstRunSpawnCounts) {
            async$firstRunSpawnCounts = false;
            async$spawnCountsReady.set(true);
        }
        if (async$spawnCountsReady.getAndSet(false)) {
            final int i = distanceManager.getNaturalSpawnChunkCount();
            ParallelProcessor.tickPool.submit(() -> {
                async$latestState = NaturalSpawner.createState(i, this.level.getAllEntities(),
                        this::getFullChunk, new LocalMobCapCalculator(this.chunkMap));
                async$spawnCountsReady.set(true);
            });
        }
    }

    @Inject(method = "tickChunks(Lnet/minecraft/util/profiling/ProfilerFiller;J)V", at = @At("HEAD"))
    private void async$captureTimeInhabited(ProfilerFiller profiler, long timeInhabited, CallbackInfo ci) {
        async$timeInhabited = timeInhabited;
    }

    @WrapOperation(method = "tickChunks(Lnet/minecraft/util/profiling/ProfilerFiller;J)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/NaturalSpawner;createState(ILjava/lang/Iterable;Lnet/minecraft/world/level/NaturalSpawner$ChunkGetter;Lnet/minecraft/world/level/LocalMobCapCalculator;)Lnet/minecraft/world/level/NaturalSpawner$SpawnState;"))
    private NaturalSpawner.SpawnState async$wrapCreateState(
            int count, Iterable<Entity> entities, NaturalSpawner.ChunkGetter chunkGetter,
            LocalMobCapCalculator calculator, Operation<NaturalSpawner.SpawnState> original
    ) {
        if (AsyncConfig.disabled || !AsyncConfig.enableAsyncSpawn) {
            return original.call(count, entities, chunkGetter, calculator);
        }
        NaturalSpawner.SpawnState cached = async$latestState;
        if (cached != null) {
            return cached;
        }
        return original.call(count, entities, chunkGetter, calculator);
    }

    @WrapOperation(
            method = "tickChunks(Lnet/minecraft/util/profiling/ProfilerFiller;J)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ChunkMap;collectSpawningChunks(Ljava/util/List;)V")
    )
    private void async$wrapCollectSpawning(ChunkMap instance, List<LevelChunk> list, Operation<Void> original) {
        original.call(instance, list);

        if (AsyncConfig.disabled || !AsyncConfig.enableAsyncSpawn) return;

        List<LevelChunk> chunks = new ArrayList<>(list);
        list.clear();

        NaturalSpawner.SpawnState state = this.lastSpawnState;
        if (state == null || chunks.isEmpty()) {
            async$spawnFuture = null;
            return;
        }

        long time = async$timeInhabited;
        boolean enemies = this.spawnEnemies;
        long gameTime = this.level.getGameTime();

        async$spawnFuture = CompletableFuture.runAsync(() -> {
            Collections.shuffle(chunks);
            boolean doMobSpawning = this.level.getGameRules().get(GameRules.SPAWN_MOBS);
            List<MobCategory> categories;
            if (doMobSpawning) {
                boolean spawnPersistent = gameTime % 400L == 0L;
                categories = NaturalSpawner.getFilteredSpawningCategories(state, true, enemies, spawnPersistent);
            } else {
                categories = List.of();
            }
            for (LevelChunk chunk : chunks) {
                this.tickSpawningChunk(chunk, time, categories, state);
            }
        }, ParallelProcessor.tickPool);
    }

    @WrapOperation(method = "tickChunks(Lnet/minecraft/util/profiling/ProfilerFiller;J)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerChunkCache;tickSpawningChunk(Lnet/minecraft/world/level/chunk/LevelChunk;JLjava/util/List;Lnet/minecraft/world/level/NaturalSpawner$SpawnState;)V"))
    private void async$wrapTickSpawning(
            ServerChunkCache instance, LevelChunk chunk, long timeInhabited,
            List<MobCategory> categories, NaturalSpawner.SpawnState state,
            Operation<Void> original
    ) {
        if (AsyncConfig.disabled || !AsyncConfig.enableAsyncSpawn) {
            original.call(instance, chunk, timeInhabited, categories, state);
        }
    }



    @WrapOperation(method = "tickChunks(Lnet/minecraft/util/profiling/ProfilerFiller;J)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ChunkMap;forEachBlockTickingChunk(Ljava/util/function/Consumer;)V"))
    private void async$wrapBlockTicking(ChunkMap instance, Consumer<LevelChunk> consumer, Operation<Void> original) {
        CompletableFuture<Void> spawnFut = async$spawnFuture;
        if (spawnFut != null) {
            async$pumpUntilDone(spawnFut);
            if (spawnFut.isCompletedExceptionally()) {
                spawnFut.join();
            }
            async$spawnFuture = null;
        }

        if (AsyncConfig.disabled || !AsyncConfig.enableAsyncRandomTicks) {
            original.call(instance, consumer);
            return;
        }

        List<LevelChunk> chunks = new ArrayList<>();
        original.call(instance, (Consumer<LevelChunk>) chunks::add);

        if (chunks.isEmpty()) return;

        int poolSize = ParallelProcessor.getPoolSize();
        int batchSize = Math.max(1, (chunks.size() + poolSize - 1) / poolSize);
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (int i = 0; i < chunks.size(); i += batchSize) {
            int start = i;
            int end = Math.min(i + batchSize, chunks.size());
            futures.add(CompletableFuture.runAsync(() -> {
                for (int j = start; j < end; j++) {
                    consumer.accept(chunks.get(j));
                }
            }, ParallelProcessor.tickPool));
        }

        async$pumpUntilDone(CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)));
    }

    @Unique
    private void async$pumpUntilDone(CompletableFuture<?> future) {
        while (!future.isDone()) {
            boolean pumped = false;
            for (ServerLevel lvl : ParallelProcessor.getServer().getAllLevels()) {
                pumped |= lvl.getChunkSource().pollTask();
            }
            if (!pumped) Thread.onSpinWait();
        }
        if (future.isCompletedExceptionally()) {
            future.join();
        }
    }
}