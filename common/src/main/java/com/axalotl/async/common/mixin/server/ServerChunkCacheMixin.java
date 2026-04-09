package com.axalotl.async.common.mixin.server;

import com.axalotl.async.common.ParallelProcessor;
import com.axalotl.async.common.config.AsyncConfig;
import com.llamalad7.mixinextras.sugar.Local;
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

    @Unique
    public long currentTimeDiff;
    @Unique
    public List<MobCategory> currentSpawningCategories;

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
    private volatile NaturalSpawner.@Nullable SpawnState lastSpawnState;

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

        while (!future.isDone()) {
            ChunkAccess cached = tryGetChunk(x, z, targetStatus);
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
    private @Nullable ChunkAccess tryGetChunk(int x, int z, ChunkStatus leastStatus) {
        ChunkHolder holder = this.getVisibleChunkIfPresent(ChunkPos.pack(x, z));
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
    private void shortcutGetChunkNow(int x, int z, CallbackInfoReturnable<LevelChunk> cir) {
        if (Thread.currentThread() != this.mainThread) {
            final ChunkHolder holder = this.getVisibleChunkIfPresent(ChunkPos.pack(x, z));
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
        final int spawnableChunkCount = distanceManager.getNaturalSpawnChunkCount();
        CompletableFuture.runAsync(() -> lastSpawnState = NaturalSpawner.createState(
                spawnableChunkCount,
                this.level.getAllEntities(),
                this::getFullChunk,
                new LocalMobCapCalculator(this.chunkMap)
        ), ParallelProcessor.executor).whenComplete((_, e) -> {
            if (e != null) {
                LOGGER.error("Error in async create state, switching to synchronous", e);
                lastSpawnState = NaturalSpawner.createState(
                        spawnableChunkCount,
                        this.level.getAllEntities(),
                        this::getFullChunk,
                        new LocalMobCapCalculator(this.chunkMap)
                );
            }
        });
    }

    @Redirect(
            method = "tickChunks(Lnet/minecraft/util/profiling/ProfilerFiller;J)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/NaturalSpawner;createState(ILjava/lang/Iterable;Lnet/minecraft/world/level/NaturalSpawner$ChunkGetter;Lnet/minecraft/world/level/LocalMobCapCalculator;)Lnet/minecraft/world/level/NaturalSpawner$SpawnState;"
            )
    )
    private NaturalSpawner.SpawnState redirectCreateSpawnState(
            int spawnableChunkCount,
            Iterable<Entity> entities,
            NaturalSpawner.ChunkGetter chunkGetter,
            LocalMobCapCalculator localMobCapCalculator
    ) {
        if (AsyncConfig.disabled || !AsyncConfig.enableAsyncSpawn) {
            return NaturalSpawner.createState(
                    spawnableChunkCount,
                    entities,
                    chunkGetter,
                    localMobCapCalculator
            );
        }

        NaturalSpawner.SpawnState state = lastSpawnState;

        if (state == null) {
            state = NaturalSpawner.createState(
                    spawnableChunkCount,
                    entities,
                    chunkGetter,
                    localMobCapCalculator
            );
            lastSpawnState = state;
        }

        return state;
    }

    @Inject(method = "tickChunks(Lnet/minecraft/util/profiling/ProfilerFiller;J)V", at = @At("HEAD"))
    private void captureTimeDiff(ProfilerFiller profiler, long timeDiff, CallbackInfo ci) {
        currentTimeDiff = timeDiff;
    }

    @Inject(method = "tickChunks(Lnet/minecraft/util/profiling/ProfilerFiller;J)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ChunkMap;collectSpawningChunks(Ljava/util/List;)V"))
    private void captureCategories(ProfilerFiller profiler, long timeDiff, CallbackInfo ci, @Local(name = "spawningCategories") List<MobCategory> spawningCategories) {
        currentSpawningCategories = spawningCategories;
    }

    @Redirect(method = "tickChunks(Lnet/minecraft/util/profiling/ProfilerFiller;J)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ChunkMap;collectSpawningChunks(Ljava/util/List;)V"))
    private void redirectCollectSpawningChunks(ChunkMap instance, List<LevelChunk> output) {
        if (AsyncConfig.disabled || !AsyncConfig.enableAsyncSpawn) {
            this.chunkMap.collectSpawningChunks(output);
        }
        CompletableFuture.runAsync(() -> this.chunkMap.collectSpawningChunks(output), ParallelProcessor.executor).whenComplete((_, e) -> {
            if (e != null) {
                LOGGER.error("Error in async entity collectSpawningChunks, switching to synchronous", e);
                this.chunkMap.collectSpawningChunks(output);
            }
        });
    }

    @Redirect(method = "tickChunks(Lnet/minecraft/util/profiling/ProfilerFiller;J)V", at = @At(value = "INVOKE", target = "Ljava/util/List;iterator()Ljava/util/Iterator;"))
    private Iterator<LevelChunk> redirectTickSpawningChunk(List<LevelChunk> instance) {
        if (lastSpawnState == null) {
            return Collections.emptyIterator();
        }

        if (AsyncConfig.disabled || !AsyncConfig.enableAsyncSpawn) {
            synchronized(instance) {
                return new ArrayList<>(instance).iterator();
            }
        }

        CompletableFuture.runAsync(() -> {
            List<LevelChunk> chunksCopy;
            synchronized (instance) {
                chunksCopy = new ArrayList<>(instance);
            }

            for (LevelChunk chunk : chunksCopy) {
                if (chunk != null) {
                    this.tickSpawningChunk(chunk, currentTimeDiff, currentSpawningCategories, lastSpawnState);
                }
            }
        }, ParallelProcessor.executor).whenComplete((_, e) -> {
            if (e != null) {
                LOGGER.error("Error in async entity spawning, switching to synchronous", e);

                for (LevelChunk chunk : instance) {
                    if (chunk != null) {
                        this.tickSpawningChunk(chunk, currentTimeDiff, currentSpawningCategories, lastSpawnState);
                    }
                }
            }
        });

        return Collections.emptyIterator();
    }
}