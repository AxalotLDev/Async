package com.axalotl.async.common.mixin.server;

import com.axalotl.async.common.ParallelProcessor;
import io.netty.util.concurrent.FastThreadLocal;
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
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(value = ServerChunkCache.class, priority = 1500)
public abstract class ServerChunkCacheMixin extends ChunkSource {

    @Shadow @Final Thread mainThread;
    @Shadow @Final private ServerLevel level;
    @Shadow public abstract @Nullable ChunkHolder getVisibleChunkIfPresent(long pos);
    @Shadow @Final @Mutable private Set<ChunkHolder> chunkHoldersToBroadcast;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void async$replaceWithConcurrentSet(CallbackInfo ci) {
        this.chunkHoldersToBroadcast = ConcurrentHashMap.newKeySet();
    }

    @Unique
    private static final FastThreadLocal<long[]> asyncMultiloader$asyncCacheKeys =
            new FastThreadLocal<>() {
                @Override
                protected long[] initialValue() {
                    long[] keys = new long[4];
                    java.util.Arrays.fill(keys, Long.MAX_VALUE);
                    return keys;
                }
            };

    @Unique
    private static final FastThreadLocal<ChunkAccess[]> asyncMultiloader$asyncCacheChunks =
            new FastThreadLocal<>() {
                @Override
                protected ChunkAccess[] initialValue() {
                    return new ChunkAccess[4];
                }
            };

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

    @Unique
    private @Nullable ChunkAccess async$tryGetChunkFast(int x, int z, ChunkStatus leastStatus) {
        long pos = ChunkPos.asLong(x, z);
        ChunkHolder holder = this.getVisibleChunkIfPresent(pos);
        if (holder == null) return null;

        ChunkAccess chunk = async$unwrap(holder.getChunkIfPresent(leastStatus));
        if (chunk != null) return chunk;

        if (leastStatus != ChunkStatus.FULL) {
            chunk = async$unwrap(holder.getChunkIfPresent(ChunkStatus.FULL));
            if (chunk != null) return chunk;
        }

        return async$tryGetFromLevelChunkFutures(holder);
    }

    @Unique
    private @Nullable LevelChunk async$tryGetFromLevelChunkFutures(ChunkHolder holder) {
        CompletableFuture<ChunkResult<LevelChunk>> future = holder.getFullChunkFuture();
        if (future.isDone() && !future.isCompletedExceptionally()) {
            LevelChunk result = future.join().orElse(null);
            if (result != null) return result;
        }

        future = holder.getTickingChunkFuture();
        if (future.isDone() && !future.isCompletedExceptionally()) {
            LevelChunk result = future.join().orElse(null);
            if (result != null) return result;
        }

        future = holder.getEntityTickingChunkFuture();
        if (future.isDone() && !future.isCompletedExceptionally()) {
            return future.join().orElse(null);
        }

        return null;
    }

    @Unique
    private @Nullable ChunkAccess async$tryGetAnyChunk(int x, int z) {
        long pos = ChunkPos.asLong(x, z);
        ChunkHolder holder = this.getVisibleChunkIfPresent(pos);
        if (holder == null) return null;

        LevelChunk lc = async$tryGetFromLevelChunkFutures(holder);
        if (lc != null) return lc;

        ChunkAccess chunk = async$unwrap(holder.getChunkIfPresent(ChunkStatus.FULL));
        if (chunk instanceof LevelChunk) return chunk;

        return null;
    }

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

        if (!create) {
            cir.setReturnValue(null);
            return;
        }

        // create=true но fast path не нашёл — пробуем любой доступный статус
        ChunkAccess fallback = async$tryGetAnyChunk(x, z);
        if (fallback != null) {
            async$addToCache(cacheKey, fallback);
            cir.setReturnValue(fallback);
            return;
        }
        cir.setReturnValue(new LevelChunk(this.level, new ChunkPos(x, z)));
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
        }

        long pos = ChunkPos.asLong(chunkX, chunkZ);
        ChunkHolder holder = this.getVisibleChunkIfPresent(pos);

        if (holder == null) {
            cir.setReturnValue(null);
            return;
        }

        ChunkAccess chunk = async$unwrap(holder.getChunkIfPresent(ChunkStatus.FULL));
        if (chunk instanceof LevelChunk levelChunk) {
            async$addToCache(cacheKey, levelChunk);
            cir.setReturnValue(levelChunk);
            return;
        }

        LevelChunk levelChunk = async$tryGetFromLevelChunkFutures(holder);
        if (levelChunk != null) {
            async$addToCache(cacheKey, levelChunk);
            cir.setReturnValue(levelChunk);
            return;
        }

        cir.setReturnValue(null);
    }

    @Redirect(method = "tickSpawningChunk(Lnet/minecraft/world/level/chunk/LevelChunk;JLjava/util/List;Lnet/minecraft/world/level/NaturalSpawner$SpawnState;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/NaturalSpawner;spawnForChunk(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/LevelChunk;Lnet/minecraft/world/level/NaturalSpawner$SpawnState;Ljava/util/List;)V"))
    private void tickSpawningChunk(ServerLevel level, LevelChunk chunk, NaturalSpawner.SpawnState spawnState, List<MobCategory> categories) {
        ParallelProcessor.asyncSpawnForChunk(level, chunk, spawnState, categories);
    }
}