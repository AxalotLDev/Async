package com.axalotl.async.common.mixin.lithium;

import net.caffeinemc.mods.lithium.common.world.chunk.ChunkHolderExtended;
import net.caffeinemc.mods.lithium.mixin.world.chunk_access.GenerationChunkHolderAccessor;
import net.minecraft.Util;
import net.minecraft.server.level.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;

@Mixin(ServerChunkCache.class)
public abstract class LithiumServerChunkCacheMixin extends ChunkSource {
    @Shadow
    @Final
    public ServerChunkCache.MainThreadExecutor mainThreadProcessor;
    @Shadow
    @Final
    private DistanceManager distanceManager;
    @Shadow
    @Final
    public ChunkMap chunkMap;
    @Shadow
    @Final
    Thread mainThread;
    @Unique
    private long async$time;
    @Unique
    private final long[] async$cacheKeys = new long[4];
    @Unique
    private final ChunkAccess[] async$cacheChunks = new ChunkAccess[4];

    @Shadow
    public abstract ChunkHolder getVisibleChunkIfPresent(long var1);

    @Shadow
    protected abstract boolean chunkAbsent(ChunkHolder var1, int var2);

    @Shadow
    public abstract void tick(@NotNull BooleanSupplier var1, boolean var2);

    @Shadow
    abstract boolean runDistanceManagerUpdates();

    @Inject(
            method = {"tick"},
            at = {@At("HEAD")}
    )
    private void preTick(BooleanSupplier shouldKeepTicking, boolean tickChunks, CallbackInfo ci) {
        ++this.async$time;
    }

    @Overwrite
    public ChunkAccess getChunk(int x, int z, @NotNull ChunkStatus status, boolean create) {
        if (Thread.currentThread() != this.mainThread) {
            return this.async$getChunkOffThread(x, z, status, create);
        } else {
            long key = async$createCacheKey(x, z, status);

            for (int i = 0; i < 4; ++i) {
                if (key == this.async$cacheKeys[i]) {
                    ChunkAccess chunk = this.async$cacheChunks[i];
                    if (chunk != null || !create) {
                        return chunk;
                    }
                }
            }

            ChunkAccess chunk = this.async$getChunkBlocking(x, z, status, create);
            if (chunk != null) {
                this.async$addToCache(key, chunk);
            } else if (create) {
                throw new IllegalStateException("Chunk not there when requested");
            }

            return chunk;
        }
    }

    @Unique
    private ChunkAccess async$getChunkOffThread(int x, int z, ChunkStatus status, boolean create) {
        final ChunkHolder holder = this.getVisibleChunkIfPresent(ChunkPos.asLong(x, z));
        if (holder != null) {
            final CompletableFuture<ChunkResult<ChunkAccess>> future = holder.scheduleChunkGenerationTask(status, this.chunkMap);
            if (future.isDone()) {
                ChunkAccess chunk = future.getNow(ChunkHolder.UNLOADED_CHUNK).orElse(null);
                if (chunk instanceof ImposterProtoChunk readOnlyChunk) chunk = readOnlyChunk.getWrapped();
                if (chunk != null) {
                    return chunk;
                }
            }
        }
        return CompletableFuture.supplyAsync(() -> this.getChunk(x, z, status, create), this.mainThreadProcessor).join();
    }

    @Unique
    private ChunkAccess async$getChunkBlocking(int x, int z, ChunkStatus leastStatus, boolean create) {
        long key = ChunkPos.asLong(x, z);
        int level = ChunkLevel.byStatus(leastStatus);
        ChunkHolder holder = this.getVisibleChunkIfPresent(key);
        if (this.chunkAbsent(holder, level)) {
            if (!create) {
                return null;
            }

            this.async$createChunkLoadTicket(x, z, level);
            this.runDistanceManagerUpdates();
            holder = this.getVisibleChunkIfPresent(key);
            if (this.chunkAbsent(holder, level)) {
                throw Util.pauseInIde(new IllegalStateException("No chunk holder after ticket has been added"));
            }
        } else if (create && ((ChunkHolderExtended) holder).lithium$updateLastAccessTime(this.async$time)) {
            this.async$createChunkLoadTicket(x, z, level);
        }

        if (!((GenerationChunkHolderAccessor) holder).invokeCannotBeLoaded(leastStatus)) {
            CompletableFuture<ChunkResult<ChunkAccess>> directlyAccessedFuture = ((GenerationChunkHolderAccessor) holder).lithium$getChunkFuturesByStatus().get(leastStatus.getIndex());
            if (directlyAccessedFuture != null && directlyAccessedFuture.isDone()) {
                ChunkAccess chunk = directlyAccessedFuture.join().orElse(null);
                if (chunk != null) {
                    return chunk;
                }
            }
        }

        CompletableFuture<ChunkResult<ChunkAccess>> loadFuture = holder.scheduleChunkGenerationTask(leastStatus, this.chunkMap);
        if (!loadFuture.isDone()) {
            ServerChunkCache.MainThreadExecutor var10000 = this.mainThreadProcessor;
            Objects.requireNonNull(loadFuture);
            var10000.managedBlock(loadFuture::isDone);
        }

        return loadFuture.join().orElse(null);
    }

    @Unique
    private void async$createChunkLoadTicket(int x, int z, int level) {
        ChunkPos chunkPos = new ChunkPos(x, z);
        this.distanceManager.addTicket(TicketType.UNKNOWN, chunkPos, level, chunkPos);
    }

    @Unique
    private static long async$createCacheKey(int chunkX, int chunkZ, ChunkStatus status) {
        return (long) chunkX & 268435455L | ((long) chunkZ & 268435455L) << 28 | (long) status.getIndex() << 56;
    }

    @Unique
    private void async$addToCache(long key, ChunkAccess chunk) {
        for (int i = 3; i > 0; --i) {
            this.async$cacheKeys[i] = this.async$cacheKeys[i - 1];
            this.async$cacheChunks[i] = this.async$cacheChunks[i - 1];
        }

        this.async$cacheKeys[0] = key;
        this.async$cacheChunks[0] = chunk;
    }

    @Inject(
            method = {"clearCache()V"},
            at = {@At("HEAD")}
    )
    private void onCachesCleared(CallbackInfo ci) {
        Arrays.fill(this.async$cacheKeys, Long.MAX_VALUE);
        Arrays.fill(this.async$cacheChunks, null);
    }
}
