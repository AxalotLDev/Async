package com.axalotl.async.parallelised;

import com.axalotl.async.Async;
import com.axalotl.async.ParallelProcessor;
import com.mojang.datafixers.DataFixer;
import net.minecraft.server.WorldGenerationProgressListener;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructureTemplateManager;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.ChunkStatusChangeListener;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.level.storage.LevelStorage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

import javax.annotation.Nullable;
import java.lang.ref.WeakReference;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;


/* 1.16.1 code; AKA the only thing that changed  */
//import net.minecraft.world.storage.SaveFormat.LevelSave;
/* */

/* 1.15.2 code; AKA the only thing that changed
import java.io.File;
/* */

public class ParaServerChunkProvider extends ServerChunkManager {

    protected ConcurrentHashMap<ChunkCacheAddress, ChunkCacheLine> chunkCache = new ConcurrentHashMap<>();
    protected AtomicInteger access = new AtomicInteger(Integer.MIN_VALUE);

    private static final int CACHE_DURATION_INTERVAL = 50;
    protected static final int CACHE_DURATION = 200;

    private static final int HASH_PRIME = 16777619;
    private static final int HASH_INIT = 0x811c9dc5;

    protected Thread cacheThread;
    Logger log = LogManager.getLogger();
    Marker chunkCleaner = MarkerManager.getMarker("ChunkCleaner");
    private final World world;

    public ParaServerChunkProvider(ServerWorld world, LevelStorage.Session session, DataFixer dataFixer, StructureTemplateManager structureManager, Executor workerExecutor, ChunkGenerator chunkGenerator, int viewDistance, int simulationDistance, boolean dsync, WorldGenerationProgressListener worldGenerationProgressListener, ChunkStatusChangeListener chunkStatusChangeListener, Supplier<PersistentStateManager> persistentStateManagerFactory) {
        super(world, session, dataFixer, structureManager, workerExecutor, chunkGenerator, viewDistance, simulationDistance, dsync, worldGenerationProgressListener, chunkStatusChangeListener, persistentStateManagerFactory);
        this.world = world;
        cacheThread = new Thread(this::chunkCacheCleanup, "Chunk Cache Cleaner " + world.getRegistryKey().getValue().getPath());
        cacheThread.start();
    }

    @SuppressWarnings("unused")
    private Chunk getChunkyThing(long chunkPos, ChunkStatus requiredStatus, boolean load) {
        Chunk cl;
        synchronized (this) {
            cl = super.getChunk(ChunkPos.getPackedX(chunkPos), ChunkPos.getPackedZ(chunkPos), requiredStatus, load);
        }
        return cl;
    }

    @Override
    @Nullable
    public Chunk getChunk(int chunkX, int chunkZ, ChunkStatus requiredStatus, boolean load) {
        if (Async.config.disabled) {
            if (ParallelProcessor.isThreadPooled("Main", Thread.currentThread())) {
                return CompletableFuture.supplyAsync(() -> this.getChunk(chunkX, chunkZ, requiredStatus, load), this.mainThreadExecutor).join();
            }
            return super.getChunk(chunkX, chunkZ, requiredStatus, load);
        }
        if (ParallelProcessor.isThreadPooled("Main", Thread.currentThread())) {
            return CompletableFuture.supplyAsync(() -> this.getChunk(chunkX, chunkZ, requiredStatus, load), this.mainThreadExecutor).join();
        }
        long i = ChunkPos.toLong(chunkX, chunkZ);

        Chunk c = lookupChunk(i, requiredStatus, false);
        if (c != null) {
            return c;
        }
        Chunk cl;
        synchronized (this) {
            if (chunkCache.containsKey(new ChunkCacheAddress(i, requiredStatus)) && (c = lookupChunk(i, requiredStatus, false)) != null) {
                return c;
            }
            cl = super.getChunk(chunkX, chunkZ, requiredStatus, load);
        }
        cacheChunk(i, cl, requiredStatus);
        return cl;
    }

    public Chunk lookupChunk(long chunkPos, ChunkStatus status, boolean compute) {
        int oldAccess = access.getAndIncrement();
        if (access.get() < oldAccess) {
            clearCache();
            return null;
        }
        ChunkCacheLine ccl;
        ccl = chunkCache.get(new ChunkCacheAddress(chunkPos, status));
        if (ccl != null) {
            ccl.updateLastAccess();
            return ccl.getChunk();
        }
        return null;
    }

    public void cacheChunk(long chunkPos, Chunk chunk, ChunkStatus status) {
        long oldAccess = access.getAndIncrement();
        if (access.get() < oldAccess) {
            clearCache();
        }

        ChunkCacheLine ccl;
        if ((ccl = chunkCache.get(new ChunkCacheAddress(chunkPos, status))) != null) {
            ccl.updateLastAccess();
            ccl.updateChunkRef(chunk);
        }
        ccl = new ChunkCacheLine(chunk);
        chunkCache.put(new ChunkCacheAddress(chunkPos, status), ccl);
    }

    public void chunkCacheCleanup() {
        while (world == null || world.getServer() == null) {
            log.debug(chunkCleaner, "ChunkCleaner Waiting for startup");
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        while (world.getServer().isRunning()) {
            try {
                Thread.sleep(CACHE_DURATION_INTERVAL * CACHE_DURATION);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            clearCache();
        }
        log.debug(chunkCleaner, "ChunkCleaner terminating");
    }

    public void clearCache() {
        chunkCache.clear();
    }

    protected static class ChunkCacheAddress {
        protected long chunk_pos;
        protected int status;
        protected int hash;

        public ChunkCacheAddress(long chunk_pos, ChunkStatus status) {
            super();
            this.chunk_pos = chunk_pos;
            this.status = status.getIndex();
            this.hash = makeHash(this.chunk_pos, this.status);
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            return (obj instanceof ChunkCacheAddress)
                    && ((ChunkCacheAddress) obj).status == this.status
                    && ((ChunkCacheAddress) obj).chunk_pos == this.chunk_pos;
        }

        public int makeHash(long chunk_pos, int status) {
            int hash = HASH_INIT;
            hash ^= status;
            for (int b = 56; b >= 0; b -= 8) {
                hash ^= (int) ((chunk_pos >> b) & 0xff);
                hash *= HASH_PRIME;
            }
            return hash;
        }
    }

    protected class ChunkCacheLine {
        WeakReference<Chunk> chunk;
        int lastAccess;

        public ChunkCacheLine(Chunk chunk) {
            this(chunk, access.get());
        }

        public ChunkCacheLine(Chunk chunk, int lastAccess) {
            this.chunk = new WeakReference<>(chunk);
            this.lastAccess = lastAccess;
        }

        public Chunk getChunk() {
            return chunk.get();
        }

        public void updateLastAccess() {
            lastAccess = access.get();
        }

        public void updateChunkRef(Chunk c) {
            if (chunk.get() == null) {
                chunk = new WeakReference<>(c);
            }
        }
    }
}
