package com.axalotl.async.common.mixin.server;

import com.axalotl.async.common.ParallelProcessor;
import com.axalotl.async.common.config.AsyncConfig;
import com.axalotl.async.common.parallelised.utils.IAsyncChunkCache;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.*;
import net.minecraft.util.Util;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LocalMobCapCalculator;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.EmptyLevelChunk;
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
import java.util.function.Consumer;

@Mixin(value = ServerChunkCache.class, priority = 1500)
public abstract class ServerChunkCacheMixin extends ChunkSource implements IAsyncChunkCache {

    @Shadow @Final public ChunkMap chunkMap;
    @Shadow @Final Thread mainThread;
    @Shadow @Final private DistanceManager distanceManager;
    @Shadow @Final private ServerLevel level;
    @Shadow private volatile NaturalSpawner.@Nullable SpawnState lastSpawnState;
    @Shadow private boolean spawnEnemies;

    @Shadow public abstract @Nullable ChunkHolder getVisibleChunkIfPresent(long pos);
    @Shadow protected abstract void getFullChunk(long chunkPos, Consumer<LevelChunk> fullChunkGetter);
    @Shadow public abstract void tickSpawningChunk(LevelChunk chunk, long timeInhabited, List<MobCategory> spawnCategories, NaturalSpawner.SpawnState spawnState);

    @Shadow private final Set<ChunkHolder> chunkHoldersToBroadcast = ConcurrentHashMap.newKeySet();
    @Shadow private final List<LevelChunk> spawningChunks = Collections.synchronizedList(new ArrayList<>());

    @Unique private static final int ASYNC_CACHE_SIZE = 4;

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
    private volatile Holder<Biome> async$emptyBiome;

    @Unique
    private Holder<Biome> async$getEmptyBiome() {
        Holder<Biome> biome = async$emptyBiome;
        if (biome == null) {
            biome = this.level.registryAccess()
                    .lookupOrThrow(Registries.BIOME)
                    .getOrThrow(Biomes.PLAINS);
            async$emptyBiome = biome;
        }
        return biome;
    }

    @Unique private boolean async$firstRunSpawnCounts = true;
    @Unique private final AtomicBoolean async$spawnCountsReady = new AtomicBoolean(false);

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
        ChunkHolder holder = this.getVisibleChunkIfPresent(pos);
        if (holder != null) {
            ChunkAccess found = async$pollAllPaths(holder, leastStatus);
            if (found != null) {
                found = async$unwrapImposter(found);
                async$storeInCache(cPos, cStatus, cChunk, pos, leastStatus, found);
                cir.setReturnValue(found);
                return;
            }
        }
        if (create) {
            cir.setReturnValue(new EmptyLevelChunk(this.level, new ChunkPos(x, z), async$getEmptyBiome()));
        } else {
            cir.setReturnValue(null);
        }
    }

    @Unique
    private static @Nullable ChunkAccess async$pollAllPaths(ChunkHolder holder, ChunkStatus status) {
        ChunkAccess chunk = holder.getChunkIfPresent(status);
        if (chunk != null) return chunk;

        chunk = holder.getChunkIfPresentUnchecked(status);
        if (chunk != null) return chunk;

        if (status == ChunkStatus.FULL) {
            LevelChunk ticking = holder.getTickingChunk();
            if (ticking != null) return ticking;
        }

        ChunkAccess latest = holder.getLatestChunk();
        if (latest != null) {
            ChunkStatus latestStatus = latest.getPersistedStatus();
            if (!status.isAfter(latestStatus)) {
                return latest;
            }
        }
        return null;
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

    @Unique
    private static ChunkAccess async$unwrapImposter(ChunkAccess chunk) {
        if (chunk instanceof ImposterProtoChunk imposter) {
            return imposter.getWrapped();
        }
        return chunk;
    }

    @Unique
    private static void async$storeInCache(
            long[] cPos, ChunkStatus[] cStatus, ChunkAccess[] cChunk,
            long pos, ChunkStatus status, ChunkAccess chunk
    ) {
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

        profiler.popPush("tickSpawningChunks");

        if (!AsyncConfig.disabled && AsyncConfig.enableAsyncSpawn) {
            NaturalSpawner.SpawnState currentState = lastSpawnState;
            if (currentState != null) {
                List<LevelChunk> chunks = new ArrayList<>();
                this.chunkMap.collectSpawningChunks(chunks);
                Util.shuffle(chunks, this.level.random);
                CompletableFuture.runAsync(() -> {
                    for (LevelChunk levelchunk : chunks) {
                        if (levelchunk != null) {
                            this.tickSpawningChunk(levelchunk, timeInhabited, list, currentState);
                        }
                    }
                }, ParallelProcessor.tickPool).exceptionally(e -> {
                    ParallelProcessor.LOGGER.error("Error in async entity spawning", e);
                    return null;
                });
            }
        } else {
            List<LevelChunk> list1 = this.spawningChunks;
            list1.clear();
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