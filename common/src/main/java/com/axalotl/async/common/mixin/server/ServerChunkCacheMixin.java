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

    @Unique
    private static final ThreadLocal<long[]> asyncMultiloader$asyncCacheKeys = ThreadLocal.withInitial(() -> {
        long[] keys = new long[4];
        java.util.Arrays.fill(keys, Long.MAX_VALUE);
        return keys;
    });

    @Unique
    private static final ThreadLocal<ChunkAccess[]> asyncMultiloader$asyncCacheChunks = ThreadLocal.withInitial(() -> new ChunkAccess[4]);

    @Unique
    private static long async$createCacheKey(int x, int z, ChunkStatus status) {
        return ((long) x & 0xfffffffL) | (((long) z & 0xfffffffL) << 28) | ((long) status.getIndex() << 56);
    }

    @Unique
    private static void async$addToCache(long key, @Nullable ChunkAccess chunk) {
        long[] keys = asyncMultiloader$asyncCacheKeys.get();
        ChunkAccess[] chunks = asyncMultiloader$asyncCacheChunks.get();
        for (int i = 3; i > 0; --i) {
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
        for (int i = 0; i < 4; ++i) {
            if (keys[i] == key) {
                return chunks[i];
            }
        }
        return null;
    }

    @Unique
    private static boolean async$isInCache(long key) {
        long[] keys = asyncMultiloader$asyncCacheKeys.get();
        for (int i = 0; i < 4; ++i) {
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

    //TODO: Implement our own getChunk without modifying the vanilla method
    @Inject(method = "getChunk(IILnet/minecraft/world/level/chunk/status/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/ChunkAccess;",
            at = @At("HEAD"), cancellable = true)
    private void async$getChunk(int x, int z, ChunkStatus leastStatus, boolean create,
                                CallbackInfoReturnable<ChunkAccess> cir) {
        if (Thread.currentThread() == this.mainThread) return;

        long cacheKey = async$createCacheKey(x, z, leastStatus);

        if (async$isInCache(cacheKey)) {
            ChunkAccess cached = async$getFromCache(cacheKey);
            if (cached != null || !create) {
                cir.setReturnValue(cached);
                return;
            }
        }

        ChunkAccess fast = async$tryGetChunkFast(x, z, leastStatus);
        if (fast != null) {
            async$addToCache(cacheKey, fast);
            cir.setReturnValue(fast);
            return;
        }

        CompletableFuture<ChunkResult<ChunkAccess>> future = CompletableFuture.supplyAsync(
                        () -> this.getChunkFutureMainThread(x, z, leastStatus, create),
                        this.mainThreadProcessor
                )
                .thenCompose(f -> f);

        ChunkAccess chunk = async$unwrap(future.join().orElse(null));
        async$addToCache(cacheKey, chunk);
        cir.setReturnValue(chunk);
    }

    @Unique
    private @Nullable ChunkAccess async$tryGetChunkFast(int x, int z, ChunkStatus leastStatus) {
        ChunkHolder holder = this.getVisibleChunkIfPresent(ChunkPos.asLong(x, z));
        if (holder == null) return null;

        ChunkAccess chunk = async$unwrap(holder.getChunkIfPresent(leastStatus));
        if (chunk != null) return chunk;

        CompletableFuture<ChunkResult<ChunkAccess>> future = holder.scheduleChunkGenerationTask(leastStatus, this.chunkMap);
        if (future.isDone() && !future.isCompletedExceptionally()) {
            return async$unwrap(future.join().orElse(null));
        }

        return null;
    }

    @Inject(method = "getChunkNow", at = @At("HEAD"), cancellable = true)
    private void async$getChunkNow(int chunkX, int chunkZ, CallbackInfoReturnable<LevelChunk> cir) {
        if (Thread.currentThread() == this.mainThread) return;

        long cacheKey = async$createCacheKey(chunkX, chunkZ, ChunkStatus.FULL);

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

        ChunkAccess chunk = async$unwrap(holder.getChunkIfPresent(ChunkStatus.FULL));
        if (chunk instanceof LevelChunk levelChunk) {
            async$addToCache(cacheKey, levelChunk);
            cir.setReturnValue(levelChunk);
            return;
        }

        CompletableFuture<ChunkResult<LevelChunk>> fullFuture = holder.getFullChunkFuture();
        if (fullFuture.isDone() && !fullFuture.isCompletedExceptionally()) {
            LevelChunk result = fullFuture.join().orElse(null);
            if (result != null) {
                async$addToCache(cacheKey, result);
                cir.setReturnValue(result);
                return;
            }
        }

        CompletableFuture<ChunkResult<LevelChunk>> tickingFuture = holder.getTickingChunkFuture();
        if (tickingFuture.isDone() && !tickingFuture.isCompletedExceptionally()) {
            LevelChunk result = tickingFuture.join().orElse(null);
            if (result != null) {
                async$addToCache(cacheKey, result);
                cir.setReturnValue(result);
                return;
            }
        }

        async$addToCache(cacheKey, null);
        cir.setReturnValue(null);
    }

    @Redirect(method = "tickSpawningChunk(Lnet/minecraft/world/level/chunk/LevelChunk;JLjava/util/List;Lnet/minecraft/world/level/NaturalSpawner$SpawnState;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/NaturalSpawner;spawnForChunk(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/LevelChunk;Lnet/minecraft/world/level/NaturalSpawner$SpawnState;Ljava/util/List;)V"))
    private void tickSpawningChunk(ServerLevel level, LevelChunk chunk, NaturalSpawner.SpawnState spawnState, List<MobCategory> categories) {
        ParallelProcessor.asyncSpawnForChunk(level, chunk, spawnState, categories);
    }
}