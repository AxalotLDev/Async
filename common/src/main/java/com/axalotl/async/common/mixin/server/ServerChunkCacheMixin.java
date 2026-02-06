package com.axalotl.async.common.mixin.server;

import com.axalotl.async.common.ParallelProcessor;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
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
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * High-performance async getChunk/getChunkNow implementation.

 * Key design principles:
 * - Multi-level fast path: ThreadLocal cache → holder.getChunkIfPresent → cross-status fallback → VMP cascading futures
 * - create=false NEVER touches the main thread (biggest perf win for entity ticking)
 * - create=true falls back to main thread only as absolute last resort
 * - No scheduleChunkGenerationTask from off-thread (unsafe and can trigger unwanted loads)
 * - Larger ThreadLocal cache (8 slots) for better hit rate during entity AI chunk lookups

 * Inspired by: VMP (cascading futures), Lithium (direct futures access, optimized caching)
 */
@Mixin(value = ServerChunkCache.class, priority = 1500)
public abstract class ServerChunkCacheMixin extends ChunkSource {

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

    // ==================== ThreadLocal Cache (8 slots) ====================

    @Unique
    private static final int ASYNC_CACHE_SIZE = 8;

    @Unique
    private static final ThreadLocal<long[]> asyncMultiloader$asyncCacheKeys = ThreadLocal.withInitial(() -> {
        long[] keys = new long[ASYNC_CACHE_SIZE];
        java.util.Arrays.fill(keys, Long.MAX_VALUE);
        return keys;
    });

    @Unique
    private static final ThreadLocal<ChunkAccess[]> asyncMultiloader$asyncCacheChunks =
            ThreadLocal.withInitial(() -> new ChunkAccess[ASYNC_CACHE_SIZE]);

    @Unique
    private static long async$createCacheKey(int x, int z, ChunkStatus status) {
        return ((long) x & 0xfffffffL) | (((long) z & 0xfffffffL) << 28) | ((long) status.getIndex() << 56);
    }

    @Unique
    private static void async$addToCache(long key, @Nullable ChunkAccess chunk) {
        long[] keys = asyncMultiloader$asyncCacheKeys.get();
        ChunkAccess[] chunks = asyncMultiloader$asyncCacheChunks.get();
        for (int i = ASYNC_CACHE_SIZE - 1; i > 0; --i) {
            keys[i] = keys[i - 1];
            chunks[i] = chunks[i - 1];
        }
        keys[0] = key;
        chunks[0] = chunk;
    }

    @Unique
    private static @Nullable ChunkAccess async$getFromCache(long key) {
        long[] keys = asyncMultiloader$asyncCacheKeys.get();
        ChunkAccess[] chunks = asyncMultiloader$asyncCacheChunks.get();
        for (int i = 0; i < ASYNC_CACHE_SIZE; ++i) {
            if (keys[i] == key) {
                return chunks[i];
            }
        }
        return null;
    }

    @Unique
    private static boolean async$isInCache(long key) {
        long[] keys = asyncMultiloader$asyncCacheKeys.get();
        for (int i = 0; i < ASYNC_CACHE_SIZE; ++i) {
            if (keys[i] == key) return true;
        }
        return false;
    }

    @Unique
    private static @Nullable ChunkAccess async$unwrap(@Nullable ChunkAccess chunk) {
        if (chunk instanceof ImposterProtoChunk imposter) {
            return imposter.getWrapped();
        }
        return chunk;
    }

    // ==================== getChunk (off-thread) ====================

    @Inject(method = "getChunk(IILnet/minecraft/world/level/chunk/status/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/ChunkAccess;",
            at = @At("HEAD"), cancellable = true)
    private void async$getChunk(int x, int z, ChunkStatus leastStatus, boolean create,
                                CallbackInfoReturnable<ChunkAccess> cir) {
        if (Thread.currentThread() == this.mainThread) return;

        long cacheKey = async$createCacheKey(x, z, leastStatus);

        // Level 0: ThreadLocal cache hit
        if (async$isInCache(cacheKey)) {
            ChunkAccess cached = async$getFromCache(cacheKey);
            if (cached != null || !create) {
                cir.setReturnValue(cached);
                return;
            }
        }

        // Level 1-3: Multi-level fast path (no main thread involvement)
        ChunkAccess fast = async$tryGetChunkFast(x, z, leastStatus);
        if (fast != null) {
            async$addToCache(cacheKey, fast);
            cir.setReturnValue(fast);
            return;
        }

        // create=false: NEVER block on main thread — just return null
        // This is the biggest performance win for entity ticking / AI pathfinding
        if (!create) {
            async$addToCache(cacheKey, null);
            cir.setReturnValue(null);
            return;
        }

        // create=true: Last resort — schedule on main thread for ticket management
        // This path is rare during normal entity ticking (chunks are usually already loaded)
        CompletableFuture<ChunkResult<ChunkAccess>> future = CompletableFuture.supplyAsync(
                        () -> this.getChunkFutureMainThread(x, z, leastStatus, true),
                        this.mainThreadProcessor
                )
                .thenCompose(f -> f);

        ChunkAccess chunk = async$unwrap(future.join().orElse(null));
        if (chunk != null) {
            async$addToCache(cacheKey, chunk);
        }
        cir.setReturnValue(chunk);
    }

    // ==================== Multi-level fast path ====================

    /**
     * Attempts to retrieve a chunk without any blocking or main thread involvement.
     * Uses three levels of increasingly broad lookups:

     * Level 1: holder.getChunkIfPresent(requestedStatus) — exact status match via vanilla fast path
     * Level 2: holder.getChunkIfPresent(FULL) — a FULL chunk satisfies any lower status
     * Level 3: VMP-style cascading LevelChunk futures (full → ticking → entityTicking)

     * Does NOT call scheduleChunkGenerationTask (unsafe from off-thread, can trigger loads)
     */
    @Unique
    private @Nullable ChunkAccess async$tryGetChunkFast(int x, int z, ChunkStatus leastStatus) {
        long pos = ChunkPos.asLong(x, z);
        ChunkHolder holder = this.getVisibleChunkIfPresent(pos);
        if (holder == null) return null;

        // Level 1: Exact status check via vanilla getChunkIfPresent
        // Uses GenerationChunkHolder.futures AtomicReferenceArray internally — thread-safe
        ChunkAccess chunk = async$unwrap(holder.getChunkIfPresent(leastStatus));
        if (chunk != null) return chunk;

        // Level 2: Cross-status fallback — FULL chunk satisfies any lower status requirement
        // This catches the common case where entity AI requests BIOMES/NOISE but chunk is FULL
        if (leastStatus != ChunkStatus.FULL) {
            chunk = async$unwrap(holder.getChunkIfPresent(ChunkStatus.FULL));
            if (chunk != null) return chunk;
        }

        // Level 3: VMP-style cascading LevelChunk futures
        // These futures are set by ChunkHolder.updateFutures and are thread-safe to read
        return async$tryGetFromLevelChunkFutures(holder);
    }

    /**
     * VMP-inspired cascading future check.
     * Checks full → ticking → entityTicking futures without blocking.
     * Each future represents a higher "access level" of the chunk.
     * If any is completed, the chunk is available.
     */
    @Unique
    private @Nullable LevelChunk async$tryGetFromLevelChunkFutures(ChunkHolder holder) {
        // 1. Full chunk future (FULL status — loaded but not ticking)
        CompletableFuture<ChunkResult<LevelChunk>> fullFuture = holder.getFullChunkFuture();
        if (fullFuture.isDone() && !fullFuture.isCompletedExceptionally()) {
            LevelChunk result = fullFuture.join().orElse(null);
            if (result != null) return result;
        }

        // 2. Ticking chunk future (BLOCK_TICKING — actively ticking blocks)
        CompletableFuture<ChunkResult<LevelChunk>> tickingFuture = holder.getTickingChunkFuture();
        if (tickingFuture.isDone() && !tickingFuture.isCompletedExceptionally()) {
            LevelChunk result = tickingFuture.join().orElse(null);
            if (result != null) return result;
        }

        // 3. Entity ticking chunk future (ENTITY_TICKING — actively ticking entities)
        CompletableFuture<ChunkResult<LevelChunk>> entityFuture = holder.getEntityTickingChunkFuture();
        if (entityFuture.isDone() && !entityFuture.isCompletedExceptionally()) {
            return entityFuture.join().orElse(null);
        }

        return null;
    }

    // ==================== getChunkNow (off-thread) ====================

    @Inject(method = "getChunkNow", at = @At("HEAD"), cancellable = true)
    private void async$getChunkNow(int chunkX, int chunkZ, CallbackInfoReturnable<LevelChunk> cir) {
        if (Thread.currentThread() == this.mainThread) return;

        long cacheKey = async$createCacheKey(chunkX, chunkZ, ChunkStatus.FULL);

        // Cache check
        if (async$isInCache(cacheKey)) {
            ChunkAccess cached = async$getFromCache(cacheKey);
            if (cached instanceof LevelChunk levelChunk) {
                cir.setReturnValue(levelChunk);
                return;
            }
            cir.setReturnValue(null);
            return;
        }

        long pos = ChunkPos.asLong(chunkX, chunkZ);
        ChunkHolder holder = this.getVisibleChunkIfPresent(pos);

        if (holder == null) {
            async$addToCache(cacheKey, null);
            cir.setReturnValue(null);
            return;
        }

        // Fast path: direct status check
        ChunkAccess chunk = async$unwrap(holder.getChunkIfPresent(ChunkStatus.FULL));
        if (chunk instanceof LevelChunk levelChunk) {
            async$addToCache(cacheKey, levelChunk);
            cir.setReturnValue(levelChunk);
            return;
        }

        // VMP-style cascading futures (full → ticking → entityTicking)
        LevelChunk levelChunk = async$tryGetFromLevelChunkFutures(holder);
        if (levelChunk != null) {
            async$addToCache(cacheKey, levelChunk);
            cir.setReturnValue(levelChunk);
            return;
        }

        // Not available — never block for getChunkNow
        async$addToCache(cacheKey, null);
        cir.setReturnValue(null);
    }

    // ==================== Async spawning ====================

    @Redirect(method = "tickSpawningChunk(Lnet/minecraft/world/level/chunk/LevelChunk;JLjava/util/List;Lnet/minecraft/world/level/NaturalSpawner$SpawnState;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/NaturalSpawner;spawnForChunk(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/LevelChunk;Lnet/minecraft/world/level/NaturalSpawner$SpawnState;Ljava/util/List;)V"))
    private void tickSpawningChunk(ServerLevel level, LevelChunk chunk, NaturalSpawner.SpawnState spawnState, List<MobCategory> categories) {
        ParallelProcessor.asyncSpawnForChunk(level, chunk, spawnState, categories);
    }
}