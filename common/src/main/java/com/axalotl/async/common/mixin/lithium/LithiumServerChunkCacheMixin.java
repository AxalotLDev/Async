package com.axalotl.async.common.mixin.lithium;

import com.axalotl.async.common.parallelised.utils.ChunkLoadTricks;
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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;

/**
 * This patch makes a number of optimizations to chunk retrieval which helps to alleviate some of the slowdown introduced
 * in Minecraft 1.13+.
 * - Scanning the recent request cache is made faster through doing a single linear integer scan. This works through
 * encoding the request's position and status level into a single integer.
 * - Chunk tickets are only created during cache-misses if they were not already created this tick. This prevents the
 * creation of duplicate tickets which would only be immediately discarded after an expensive lookup and sort.
 * - Lambdas are replaced where possible to use simple if-else logic, avoiding allocations and variable captures.
 * - The chunk retrieval logic does not try to begin executing other tasks while blocked unless the future isn't
 * already complete.
 * <p>
 * There are also some organizational and differences which help the JVM to better optimize code here, most of which
 * are documented.
 */
@Mixin(value = ServerChunkCache.class)
public abstract class LithiumServerChunkCacheMixin extends ChunkSource {
    @Shadow
    @Final
    public ServerChunkCache.MainThreadExecutor mainThreadProcessor;

    @Shadow
    @Final
    public ChunkMap chunkMap;

    @Unique
    private final Object async$cacheLock = new Object();

    @Shadow
    public abstract ChunkHolder getVisibleChunkIfPresent(long pos);

    @Shadow
    @Final
    Thread mainThread;

    @Shadow
    protected abstract boolean chunkAbsent(ChunkHolder holder, int maxLevel);

    @Shadow
    public abstract void tick(@NotNull BooleanSupplier shouldKeepTicking, boolean tickChunks);

    @Shadow
    abstract boolean runDistanceManagerUpdates();

    @Shadow
    @Final
    private DistanceManager distanceManager;

    @Shadow
    public abstract <T> void removeRegionTicket(TicketType<T> type, ChunkPos pos, int distance, T value);

    @Unique
    private long async$time;

    @Inject(method = "tick", at = @At("HEAD"))
    private void preTick(BooleanSupplier shouldKeepTicking, boolean tickChunks, CallbackInfo ci) {
        this.async$time++;
    }


    @Inject(
            method = "getChunk",
            at = @At("HEAD"),
            cancellable = true
    )
    public void getChunk(int x, int z, ChunkStatus status, boolean create, CallbackInfoReturnable<ChunkAccess> cir) {
        if (Thread.currentThread() != this.mainThread) {
            cir.setReturnValue(async$getChunkOffThread(x, z, status, create));
            return;
        }
        // Store a local reference to the cached keys array in order to prevent bounds checks later

        // Create a key which will identify this request in the cache
        long key = async$createCacheKey(x, z, status);

        for (int i = 0; i < 4; ++i) {
            // Consolidate the scan into one comparison, allowing the JVM to better optimize the function
            // This is considerably faster than scanning two arrays side-by-side
            if (key == this.async$cacheKeys[i]) {
                ChunkAccess chunk = this.async$cacheChunks[i];

                // If the chunk exists for the key, or we didn't need to create one, return the result
                if (chunk != null || !create) {
                    cir.setReturnValue(chunk);
                    return;
                }
            }
        }

        // We couldn't find the chunk in the cache, so perform a blocking retrieval of the chunk from storage
        ChunkAccess chunk = this.async$getChunkBlocking(x, z, status, create);

        if (chunk != null) {
            this.async$addToCache(key, chunk);
        } else if (create) {
            throw new IllegalStateException("Chunk not there when requested");
        }

        cir.setReturnValue(chunk);
    }

    @Unique
    private ChunkAccess async$getChunkOffThread(int x, int z, ChunkStatus status, boolean create) {
        final long pos = ChunkPos.asLong(x, z);
        final ChunkHolder holder = this.getVisibleChunkIfPresent(pos);
        final ChunkAccess ifPresent = holder == null ? null : holder.getChunkIfPresent(status);
        if (ifPresent != null) {
            if (ifPresent instanceof ImposterProtoChunk proto) {
                return proto.getWrapped();
            }
            return ifPresent;
        }

        return create ? this.async$syncLoad(x, z, status) : null;
    }

    @Unique
    private ChunkAccess async$syncLoad(final int chunkX, final int chunkZ, final ChunkStatus status) {
        ChunkPos chunkPos = new ChunkPos(chunkX, chunkZ);
        CompletableFuture<ChunkAccess> future = new CompletableFuture<>();

        this.mainThreadProcessor.execute(() -> {
            this.distanceManager.addTicket(TicketType.FORCED, chunkPos, ChunkLevel.byStatus(status), chunkPos);
            this.runDistanceManagerUpdates();
            ChunkHolder holder = this.getVisibleChunkIfPresent(chunkPos.toLong());

            if (holder == null) {
                this.removeRegionTicket(TicketType.FORCED, chunkPos, 0, chunkPos);
                future.completeExceptionally(new IllegalStateException("ChunkHolder is null"));
                return;
            }

            holder.scheduleChunkGenerationTask(status, this.chunkMap)
                    .whenCompleteAsync((optChunk, throwable) -> {
                        this.removeRegionTicket(TicketType.FORCED, chunkPos, 0, chunkPos);

                        if (throwable != null) {
                            future.completeExceptionally(throwable);
                            return;
                        }

                        ChunkAccess chunk = optChunk.orElse(null);
                        if (chunk instanceof ImposterProtoChunk readOnlyChunk) {
                            chunk = readOnlyChunk.getWrapped();
                        }

                        if (chunk == null) {
                            future.completeExceptionally(new IllegalStateException("Chunk not loaded when requested"));
                        } else {
                            future.complete(chunk);
                        }
                    }, this.mainThreadProcessor);
        });

        return future.join();
    }

    /**
     * Retrieves a chunk from the storages, blocking to work on other tasks if the requested chunk needs to be loaded
     * from disk or generated in real-time.
     *
     * @param x           The x-coordinate of the chunk
     * @param z           The z-coordinate of the chunk
     * @param leastStatus The minimum status level of the chunk
     * @param create      True if the chunk should be loaded/generated if it isn't already, otherwise false
     * @return A chunk if it was already present or loaded/generated by the {@param create} flag
     */
    @Unique
    private ChunkAccess async$getChunkBlocking(int x, int z, ChunkStatus leastStatus, boolean create) {
        final long key = ChunkPos.asLong(x, z);
        final int level = ChunkLevel.byStatus(leastStatus);

        ChunkHolder holder = this.getVisibleChunkIfPresent(key);

        // Recreate NeoForge chunk loading tricks
        ChunkAccess chunkAccess = ChunkLoadTricks.tryRetrieveCurrentlyLoading(holder);
        if (chunkAccess != null) {
            return chunkAccess;
        }

        // Vanilla: Check if the holder is present and is at least of the level we need
        if (this.chunkAbsent(holder, level)) {
            if (create) {
                // Vanilla: The chunk holder is missing, so we need to create a ticket in order to load it
                this.async$createChunkLoadTicket(x, z, level);

                // Vanilla: Tick the chunk manager to have our new ticket processed
                this.runDistanceManagerUpdates();

                // Vanilla: Try to fetch the holder again now that we have requested a load
                holder = this.getVisibleChunkIfPresent(key);

                // Vanilla: If the holder is still not available, we need to fail now... something is wrong.
                if (this.chunkAbsent(holder, level)) {
                    throw Util.pauseInIde(new IllegalStateException("No chunk holder after ticket has been added"));
                }
            } else {
                //Vanilla: Use UNLOADED_FUTURE. Lithium: Just return null immediately.
                // The holder is absent, and we weren't asked to create anything, so return null
                return null;
            }
        } else if (create && ((ChunkHolderExtended) holder).lithium$updateLastAccessTime(this.async$time)) {
            // Vanilla: Always create the ticket.
            // Lithium: Only create a new chunk ticket if one hasn't already been submitted this tick
            // This maintains vanilla behavior (preventing chunks from being immediately unloaded) while also
            // eliminating the cost of submitting a ticket for most chunk fetches
            this.async$createChunkLoadTicket(x, z, level);
        }

        // Lithium: Attempt to directly get the chunk from the finished future:
        if (!((GenerationChunkHolderAccessor) holder).invokeCannotBeLoaded(leastStatus)) {
            CompletableFuture<ChunkResult<ChunkAccess>> directlyAccessedFuture = ((GenerationChunkHolderAccessor) holder).lithium$getChunkFuturesByStatus().get(leastStatus.getIndex());
            if (directlyAccessedFuture != null && directlyAccessedFuture.isDone()) {
                ChunkAccess chunk = directlyAccessedFuture.join().orElse(null);
                if (chunk != null) {
                    return chunk;
                }
            }
        }

        // Vanilla: Always call holder.load(). Lithium: Fall back to vanilla in case the fast-path did not work.
        CompletableFuture<ChunkResult<ChunkAccess>> loadFuture = holder.scheduleChunkGenerationTask(leastStatus, this.chunkMap);

        // Vanilla: Always call runTasks(). Lithium: Only call runTasks() if it will perform work.
        if (!loadFuture.isDone()) {
            // Perform other chunk tasks while waiting for this future to complete
            // This returns when either the future is done or there are no other tasks remaining
            this.mainThreadProcessor.managedBlock(loadFuture::isDone);
        }

        // Wait for the result of the future and unwrap it, returning null if the chunk is absent
        return loadFuture.join().orElse(null);

    }

    @Unique
    private void async$createChunkLoadTicket(int x, int z, int level) {
        ChunkPos chunkPos = new ChunkPos(x, z);
        this.distanceManager.addTicket(TicketType.UNKNOWN, chunkPos, level, chunkPos);
    }

    /**
     * The array of keys (encoding positions and status levels) for the recent lookup cache
     */
    @Unique
    private final long[] async$cacheKeys = new long[4];

    /**
     * The array of values associated with each key in the recent lookup cache.
     */
    @Unique
    private final ChunkAccess[] async$cacheChunks = new ChunkAccess[4];

    /**
     * Encodes a chunk position and status into a long. Uses 28 bits for each coordinate value, and 8 bits for the
     * status.
     */
    @Unique
    private static long async$createCacheKey(int chunkX, int chunkZ, ChunkStatus status) {
        return ((long) chunkX & 0xfffffffL) | (((long) chunkZ & 0xfffffffL) << 28) | ((long) status.getIndex() << 56);
    }

    /**
     * Prepends the chunk with the given key to the recent lookup cache
     */
    @Unique
    private void async$addToCache(long key, ChunkAccess chunk) {
        synchronized (async$cacheLock) {
            for (int i = 3; i > 0; --i) {
                this.async$cacheKeys[i] = this.async$cacheKeys[i - 1];
                this.async$cacheChunks[i] = this.async$cacheChunks[i - 1];
            }

            this.async$cacheKeys[0] = key;
            this.async$cacheChunks[0] = chunk;
        }
    }

    /**
     * Reset our own caches whenever vanilla does the same
     */
    @Inject(method = "clearCache()V", at = @At("HEAD"))
    private void onCachesCleared(CallbackInfo ci) {
        Arrays.fill(this.async$cacheKeys, Long.MAX_VALUE);
        Arrays.fill(this.async$cacheChunks, null);
    }
}