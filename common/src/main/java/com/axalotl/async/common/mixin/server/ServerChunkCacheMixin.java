package com.axalotl.async.common.mixin.server;

import com.axalotl.async.common.ParallelProcessor;
import com.axalotl.async.common.config.AsyncConfig;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.level.*;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
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
    private Thread mainThread;

    @Shadow
    @Final
    public ServerChunkCache.MainThreadExecutor mainThreadProcessor;

    @Shadow
    public abstract @Nullable ChunkHolder getVisibleChunkIfPresent(long key);

    @Shadow
    protected abstract CompletableFuture<ChunkResult<ChunkAccess>> getChunkFutureMainThread(int x, int z, ChunkStatus targetStatus, boolean loadOrGenerate);

    @Shadow
    private final Set<ChunkHolder> chunkHoldersToBroadcast = ConcurrentHashMap.newKeySet();

    @Shadow
    @Final
    private DistanceManager distanceManager;

    @Shadow
    @Final
    private ServerLevel level;

    @Shadow
    protected abstract void getFullChunk(long chunkKey, Consumer<LevelChunk> output);

    @Shadow
    private final List<LevelChunk> spawningChunks = Collections.synchronizedList(new ArrayList<>());

    @Shadow
    private boolean spawnEnemies;

    @Shadow
    public abstract void tickSpawningChunk(LevelChunk chunk, long timeDiff, List<MobCategory> spawningCategories, NaturalSpawner.SpawnState spawnCookie);

    @Unique
    private AtomicBoolean isSpawnStateComputing;

    @Unique
    private AtomicReference<NaturalSpawner.SpawnState> readySpawnState;

    @Unique
    private boolean wasAsyncSpawnEnabled;

    @Unique
    private final Object lock = new Object();

    @Unique
    private List<Runnable> batch;

    @Unique
    private CompletableFuture<Void> pendingSpawnBatch;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void init(CallbackInfo ci) {
        this.isSpawnStateComputing = new AtomicBoolean(false);
        this.readySpawnState = new AtomicReference<>();
        this.wasAsyncSpawnEnabled = false;
        this.batch = new ArrayList<>();
        this.pendingSpawnBatch = CompletableFuture.completedFuture(null);
    }

    @Inject(method = "getChunk(IILnet/minecraft/world/level/chunk/status/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/ChunkAccess;", at = @At("HEAD"), cancellable = true)
    private void getChunk(int x, int z, ChunkStatus targetStatus, boolean loadOrGenerate, CallbackInfoReturnable<ChunkAccess> cir) {
        if (Thread.currentThread() == this.mainThread) return;

        ChunkAccess access = tryGetChunk(x, z, targetStatus);
        if (access != null) {
            cir.setReturnValue(access);
            return;
        }

        CompletableFuture<ChunkResult<ChunkAccess>> future = CompletableFuture.supplyAsync(
                () -> this.getChunkFutureMainThread(x, z, targetStatus, loadOrGenerate),
                this.mainThreadProcessor
        ).thenCompose(f -> f);

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
        while (!future.isDone()) {
            if (System.nanoTime() > deadline) {
                future.cancel(false);
                return;
            }
            ChunkAccess cached = tryGetChunk(x, z, targetStatus);
            if (cached != null) {
                future.cancel(false);
                cir.setReturnValue(cached);
                return;
            }
            LockSupport.parkNanos(10_000);
        }

        ChunkAccess chunk = future.join().orElse(null);
        if (chunk instanceof ImposterProtoChunk imp) chunk = imp.getWrapped();
        cir.setReturnValue(chunk);
    }

    @Unique
    private @Nullable ChunkAccess tryGetChunk(int x, int z, ChunkStatus leastStatus) {
        ChunkHolder holder = this.getVisibleChunkIfPresent(ChunkPos.pack(x, z));
        if (holder == null) return null;
        ChunkAccess chunk = holder.getChunkIfPresent(leastStatus);
        if (chunk instanceof ImposterProtoChunk imp) return imp.getWrapped();
        return chunk;
    }

    @Inject(method = "getChunkNow", at = @At("HEAD"), cancellable = true)
    private void shortcutGetChunkNow(int x, int z, CallbackInfoReturnable<LevelChunk> cir) {
        if (Thread.currentThread() == this.mainThread) return;
        final ChunkHolder holder = this.getVisibleChunkIfPresent(ChunkPos.pack(x, z));
        if (holder != null) {
            ChunkAccess chunk = holder.getChunkIfPresent(ChunkStatus.FULL);
            cir.setReturnValue(chunk instanceof LevelChunk wc ? wc : null);
        } else {
            cir.setReturnValue(null);
        }
    }

    @Inject(method = "tickChunks(Lnet/minecraft/util/profiling/ProfilerFiller;J)V", at = @At("HEAD"))
    private void onSpawnTickStart(CallbackInfo ci) {
        boolean asyncEnabled = !AsyncConfig.disabled && AsyncConfig.enableAsyncSpawn;

        if (!asyncEnabled) {
            wasAsyncSpawnEnabled = false;
            return;
        }

        if (!wasAsyncSpawnEnabled) {
            wasAsyncSpawnEnabled = true;
            readySpawnState.set(null);
            isSpawnStateComputing.set(false);
            pendingSpawnBatch = CompletableFuture.completedFuture(null);
        }
    }

    @WrapOperation(method = "tickChunks(Lnet/minecraft/util/profiling/ProfilerFiller;J)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ChunkMap;forEachBlockTickingChunk(Ljava/util/function/Consumer;)V"))
    private void parallelRandomTicks(ChunkMap map, Consumer<LevelChunk> tickingChunkConsumer, Operation<Void> original) {
        if (AsyncConfig.disabled || !AsyncConfig.enableAsyncRandomTicks) {
            original.call(map, tickingChunkConsumer);
            return;
        }

        List<LevelChunk> chunks = new ArrayList<>();
        original.call(map, (Consumer<LevelChunk>) chunks::add);
        ParallelProcessor.forEachParallel(chunks, tickingChunkConsumer);
    }

    @Redirect(method = "tickChunks(Lnet/minecraft/util/profiling/ProfilerFiller;J)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/NaturalSpawner;createState(ILjava/lang/Iterable;Lnet/minecraft/world/level/NaturalSpawner$ChunkGetter;Lnet/minecraft/world/level/LocalMobCapCalculator;)Lnet/minecraft/world/level/NaturalSpawner$SpawnState;"))
    private NaturalSpawner.SpawnState redirectCreateSpawnState(int spawnableChunkCount, Iterable<Entity> entities, NaturalSpawner.ChunkGetter chunkGetter, LocalMobCapCalculator localMobCapCalculator) {
        if (!AsyncConfig.disabled && AsyncConfig.enableAsyncSpawn) {
            NaturalSpawner.SpawnState ready = readySpawnState.getAndSet(null);
            if (ready != null) {
                return ready;
            }
        }
        return NaturalSpawner.createState(spawnableChunkCount, entities, chunkGetter, localMobCapCalculator);
    }

    @WrapMethod(method = "tickSpawningChunk")
    private void redirectTickSpawningChunk(LevelChunk chunk, long timeDiff, List<MobCategory> spawningCategories, NaturalSpawner.SpawnState spawnCookie, Operation<Void> original) {
        if (AsyncConfig.disabled || !AsyncConfig.enableAsyncSpawn) {
            original.call(chunk, timeDiff, spawningCategories, spawnCookie);
            return;
        }

        synchronized (lock) {
            batch.add(() -> original.call(chunk, timeDiff, spawningCategories, spawnCookie));
        }
    }

    @Inject(method = "tickChunks(Lnet/minecraft/util/profiling/ProfilerFiller;J)V", at = @At("TAIL"))
    private void dispatchSpawnBatch(CallbackInfo ci) {
        List<Runnable> currentBatch;
        synchronized (lock) {
            if (batch.isEmpty()) return;
            currentBatch = new ArrayList<>(batch);
            batch.clear();
        }

        if (AsyncConfig.disabled || !AsyncConfig.enableAsyncSpawn) {
            runSpawnBatch(currentBatch);
            return;
        }

        pendingSpawnBatch = pendingSpawnBatch
                .exceptionally(_ -> null)
                .thenCompose(_ -> runSpawnBatchParallel(currentBatch));
    }

    @Unique
    private CompletableFuture<Void> runSpawnBatchParallel(List<Runnable> tasks) {
        int chunkSize = ParallelProcessor.chunkSizeFor(tasks.size());
        List<CompletableFuture<Void>> slices = new ArrayList<>();
        for (int i = 0; i < tasks.size(); i += chunkSize) {
            List<Runnable> slice = tasks.subList(i, Math.min(i + chunkSize, tasks.size()));
            slices.add(CompletableFuture.runAsync(() -> runSpawnBatch(slice), ParallelProcessor.executor));
        }
        return CompletableFuture.allOf(slices.toArray(new CompletableFuture[0]));
    }

    @Unique
    private void runSpawnBatch(List<Runnable> tasks) {
        for (Runnable task : tasks) {
            try {
                task.run();
            } catch (Throwable e) {
                LOGGER.error("Error in async entity spawning", e);
            }
        }
    }

    @Inject(method = "tickChunks()V", at = @At("TAIL"))
    private void scheduleSpawnStateRecompute(CallbackInfo ci) {
        if (AsyncConfig.disabled || !AsyncConfig.enableAsyncSpawn) return;
        if (readySpawnState.get() != null) return;
        if (isSpawnStateComputing.getAndSet(true)) return;

        final int chunkCount = distanceManager.getNaturalSpawnChunkCount();
        final Iterable<Entity> entities = this.level.getAllEntities();
        pendingSpawnBatch = pendingSpawnBatch
                .exceptionally(_ -> null)
                .thenRunAsync(() -> {
                    try {
                        readySpawnState.set(NaturalSpawner.createState(
                                chunkCount, entities, this::getFullChunk, new LocalMobCapCalculator(this.chunkMap)));
                    } finally {
                        isSpawnStateComputing.set(false);
                    }
                }, ParallelProcessor.executor)
                .whenComplete((_, e) -> {
                    if (e != null) {
                        LOGGER.error("Error computing async spawn state", e);
                    }
                });
    }
}