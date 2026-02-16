package com.axalotl.async.common.parallelised.utils;

import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

/**
 * Shared fast non-blocking chunk access.
 * Same logic as ServerChunkCacheMixin's async getChunk path,
 * extracted so ChunkMapMixin (collectSpawningChunks, forEachBlockTickingChunk)
 * can use the same fast path.
 */
public final class AsyncChunkAccess {

    private AsyncChunkAccess() {}

    /**
     * Fast non-blocking LevelChunk retrieval from a ChunkHolder.
     *
     * Priority order:
     * 1. holder.getChunkIfPresent(FULL) — direct field read, fastest
     * 2. Completed futures fallback — for edge cases where chunk is loaded but not yet in status array
     *
     * This is the same path as ServerChunkCacheMixin.async$tryGetChunkFast
     * but returns LevelChunk directly and accepts a holder.
     */
    public static @Nullable LevelChunk getLoadedChunk(@Nullable ChunkHolder holder) {
        if (holder == null) return null;

        // Fast path: direct status read (no futures, no synchronization)
        ChunkAccess chunk = holder.getLatestChunk();
        if (chunk instanceof ImposterProtoChunk imposter) {
            chunk = imposter.getWrapped();
        }
        if (chunk instanceof LevelChunk lc) return lc;

        // Fallback: check completed futures (same as async$tryGetFromLevelChunkFutures)
        return tryGetFromFutures(holder);
    }

    /**
     * Try to get LevelChunk from already-completed futures.
     * Never blocks — only reads futures that are already done.
     */
    private static @Nullable LevelChunk tryGetFromFutures(ChunkHolder holder) {
        LevelChunk result;

        CompletableFuture<ChunkResult<LevelChunk>> future = holder.getFullChunkFuture();
        if (future.isDone() && !future.isCompletedExceptionally()) {
            result = future.join().orElse(null);
            if (result != null) return result;
        }

        future = holder.getTickingChunkFuture();
        if (future.isDone() && !future.isCompletedExceptionally()) {
            result = future.join().orElse(null);
            if (result != null) return result;
        }

        future = holder.getEntityTickingChunkFuture();
        if (future.isDone() && !future.isCompletedExceptionally()) {
            return future.join().orElse(null);
        }

        return null;
    }
}