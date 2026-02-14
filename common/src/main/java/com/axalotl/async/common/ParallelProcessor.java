package com.axalotl.async.common;

import com.axalotl.async.common.config.AsyncConfig;
import com.axalotl.async.common.parallelised.utils.VanishCompat;
import io.netty.util.internal.shaded.org.jctools.queues.MpscUnboundedArrayQueue;
import io.netty.util.internal.shaded.org.jctools.queues.SpscLinkedQueue;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.chunk.LevelChunk;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ParallelProcessor {

    public static final Logger LOGGER = LogManager.getLogger(
        ParallelProcessor.class
    );

    @Getter
    @Setter
    private static MinecraftServer server;

    public static ForkJoinPool tickPool;
    public static final AtomicInteger currentEntities = new AtomicInteger();
    private static final Object ENTITY_ADD_LOCK = new Object();
    private static final AtomicInteger threadPoolID = new AtomicInteger();
    private static final Set<UUID> blacklistedEntity =
        ConcurrentHashMap.newKeySet();
    private static volatile boolean isShuttingDown = false;

    // Thread-local flag: set to true for all threads created by our ForkJoinPool.
    // Checked by isServerExecutionThread() - O(1) instead of streaming WeakReferences.
    private static final ThreadLocal<Boolean> IS_ASYNC_TICK_THREAD =
        ThreadLocal.withInitial(() -> Boolean.FALSE);

    private static final int ENTITY_GRAIN = 64;
    private static final int DESPAWN_GRAIN = 128;
    private static volatile ForkJoinTask<?> currentSpawnTask;

    // jctools queues from Netty's shaded bundle - specialized for our access patterns.
    //
    // Entity/despawn/spawn queues are SPSC: the main server tick thread is the sole
    // producer (callEntityTick/asyncDespawn/asyncSpawnForChunk) and sole consumer
    // (postEntityTick drains them). SpscLinkedQueue uses plain stores with StoreStore
    // barriers - zero CAS overhead, optimal for this single-threaded collect-then-drain pattern.
    //
    // External task queue is MPSC: pool worker threads produce (addTask from random tick
    // submissions), main thread consumes (postEntityTick). MpscUnboundedArrayQueue uses
    // array-backed chunks for cache-friendly traversal with lock-free CAS on the producer
    // side and wait-free consumption.
    private static final Queue<EntityTickEntry> pendingEntityQueue =
        new SpscLinkedQueue<>();
    private static final Queue<Entity> pendingDespawnQueue =
        new SpscLinkedQueue<>();
    // Double-buffer for spawn work. Both produce and drain happen on the main thread,
    // so no concurrent data structure is needed. The main thread fills spawnCollect during
    // chunk iteration, then submitSpawnCycle swaps it with spawnSubmit and hands the full
    // list to a pool thread. Zero drain, zero copy - just a reference swap.
    private static ArrayList<SpawnEntry> spawnCollect = new ArrayList<>();
    private static ArrayList<SpawnEntry> spawnSubmit = new ArrayList<>();
    private static final Queue<CompletableFuture<?>> externalTaskQueue =
        new MpscUnboundedArrayQueue<>(256);

    // Lightweight records holding references. No data copying - just pointers.
    record EntityTickEntry(ServerLevel world, Entity entity) {}

    record SpawnEntry(
        ServerLevel level,
        LevelChunk chunk,
        NaturalSpawner.SpawnState spawnState,
        List<MobCategory> categories
    ) {}

    public static final Set<Class<?>> BLOCKED_ENTITIES = Set.of(
        FallingBlockEntity.class,
        Shulker.class,
        AbstractBoat.class
    );

    // Worker thread subclass that sets the ThreadLocal flag on the actual worker thread
    // during onStart(), which runs on the worker thread itself (not the factory caller).
    private static final class AsyncTickWorkerThread
        extends ForkJoinWorkerThread
    {

        AsyncTickWorkerThread(ForkJoinPool pool) {
            super(pool);
        }

        @Override
        protected void onStart() {
            super.onStart();
            IS_ASYNC_TICK_THREAD.set(Boolean.TRUE);
        }
    }

    public static void setupThreadPool(int parallelism, Class<?> asyncClass) {
        isShuttingDown = false;

        tickPool = new ForkJoinPool(
            parallelism,
            pool -> {
                AsyncTickWorkerThread worker = new AsyncTickWorkerThread(pool);
                worker.setName(
                    "Async-Tick-Pool-Thread-" + threadPoolID.getAndIncrement()
                );
                worker.setDaemon(true);
                worker.setPriority(Thread.NORM_PRIORITY);
                worker.setContextClassLoader(asyncClass.getClassLoader());
                return worker;
            },
            (t, e) ->
                LOGGER.error(
                    "Uncaught exception in thread {}: {}",
                    t.getName(),
                    e
                ),
            true
        );

        LOGGER.info("Initialized Pool with {} threads", parallelism);
        VanishCompat.apply();
    }

    // Called by UtilMixin to register Minecraft's background executor threads.
    // We mark them via ThreadLocal so isServerExecutionThread() returns true for
    // any thread involved in async tick work. The poolName parameter is ignored -
    // all registered threads are treated equally.
    public static void registerThread(String poolName, Thread thread) {
        // No-op: threads from our own pool are marked in AsyncTickWorkerThread.onStart().
        // External threads registered here don't need the flag since they aren't
        // part of our async tick pool. Keeping this method for mixin compatibility.
    }

    public static boolean isServerExecutionThread() {
        return IS_ASYNC_TICK_THREAD.get();
    }

    public static void callEntityTick(ServerLevel world, Entity entity) {
        if (isShuttingDown) {
            world.tickNonPassenger(entity);
            return;
        }

        if (shouldTickSynchronously(entity)) {
            world.tickNonPassenger(entity);
            return;
        }

        pendingEntityQueue.add(new EntityTickEntry(world, entity));
    }

    public static boolean shouldTickSynchronously(Entity entity) {
        // Ordered cheapest-to-most-expensive checks.
        // Config.disabled is a simple volatile boolean read.
        if (AsyncConfig.disabled) return true;

        // instanceof checks are JIT-optimized to type tag comparisons (single branch)
        if (entity instanceof ServerPlayer) return true;
        if (entity instanceof Projectile) return true;
        if (entity instanceof AbstractMinecart) return true;

        // Client-side check - should never happen on server but guard anyway
        if (entity.level().isClientSide()) return true;

        // Class identity check against small immutable set (hash lookup)
        if (BLOCKED_ENTITIES.contains(entity.getClass())) return true;

        // Portal check - simple null check on a field, very cheap
        if (entity.portalProcess != null) return true;

        // UUID-based blacklist check - only now do we call getUUID() which may allocate.
        // The blacklist is typically empty, so the contains() call is O(1) on empty set.
        if (blacklistedEntity.contains(entity.getUUID())) return true;

        // Config lookup - may involve string hashing. Most expensive check, goes last.
        return AsyncConfig.isEntitySynchronized(
            EntityType.getKey(entity.getType())
        );
    }

    public static Object getEntityAddLock() {
        return ENTITY_ADD_LOCK;
    }

    static final class EntityTickBatch extends RecursiveAction {

        private final EntityTickEntry[] entries;
        private final int from;
        private final int to;

        EntityTickBatch(EntityTickEntry[] entries, int from, int to) {
            this.entries = entries;
            this.from = from;
            this.to = to;
        }

        @Override
        protected void compute() {
            int size = to - from;
            if (size <= ENTITY_GRAIN) {
                for (int i = from; i < to; i++) {
                    EntityTickEntry e = entries[i];
                    e.world().tickNonPassenger(e.entity());
                }
            } else {
                int mid = (from + to) >>> 1;
                invokeAll(
                    new EntityTickBatch(entries, from, mid),
                    new EntityTickBatch(entries, mid, to)
                );
            }
        }
    }

    static final class DespawnBatch extends RecursiveAction {

        private final Entity[] entities;
        private final int from;
        private final int to;

        DespawnBatch(Entity[] entities, int from, int to) {
            this.entities = entities;
            this.from = from;
            this.to = to;
        }

        @Override
        protected void compute() {
            int size = to - from;
            if (size <= DESPAWN_GRAIN) {
                for (int i = from; i < to; i++) {
                    entities[i].checkDespawn();
                }
            } else {
                int mid = (from + to) >>> 1;
                invokeAll(
                    new DespawnBatch(entities, from, mid),
                    new DespawnBatch(entities, mid, to)
                );
            }
        }
    }

    public static void asyncSpawnForChunk(
        ServerLevel level,
        LevelChunk chunk,
        NaturalSpawner.SpawnState spawnState,
        List<MobCategory> categories
    ) {
        if (!chunk.loaded) return;

        if (
            isShuttingDown ||
            AsyncConfig.disabled ||
            !AsyncConfig.enableAsyncSpawn
        ) {
            NaturalSpawner.spawnForChunk(level, chunk, spawnState, categories);
            return;
        }

        if (categories.isEmpty()) return;

        // Capture a snapshot of categories since the caller may mutate the list.
        // List.copyOf is zero-copy if the source is already unmodifiable (common case).
        spawnCollect.add(
            new SpawnEntry(level, chunk, spawnState, List.copyOf(categories))
        );
    }

    private static void submitSpawnCycle() {
        if (spawnCollect.isEmpty()) return;

        // If the previous cycle is still running, join it instead of discarding work.
        // In practice it should always be done - postEntityTick waits for completion -
        // but joining guarantees we never silently drop spawn work.
        ForkJoinTask<?> prev = currentSpawnTask;
        if (prev != null && !prev.isDone()) {
            prev.quietlyJoin();
        }

        // Swap buffers: hand the full list to the pool thread, start collecting into
        // the (now empty) previous submit buffer. No drain, no copy, no allocation.
        ArrayList<SpawnEntry> ready = spawnCollect;
        spawnCollect = spawnSubmit;
        spawnCollect.clear();
        spawnSubmit = ready;

        // Spawn work must run serially (shared SpawnState mutation) but is offloaded
        // to a pool thread so the main thread can proceed with entity tick submission.
        currentSpawnTask = tickPool.submit(() -> {
            for (int i = 0, n = ready.size(); i < n; i++) {
                SpawnEntry s = ready.get(i);
                NaturalSpawner.spawnForChunk(
                    s.level(),
                    s.chunk(),
                    s.spawnState(),
                    s.categories()
                );
            }
        });
    }

    public static void asyncDespawn(Entity entity) {
        if (
            isShuttingDown ||
            AsyncConfig.disabled ||
            !AsyncConfig.enableAsyncSpawn
        ) {
            entity.checkDespawn();
            return;
        }

        pendingDespawnQueue.add(entity);
    }

    public static void addTask(CompletableFuture<?> future) {
        externalTaskQueue.add(future);
    }

    public static void postEntityTick() {
        if (AsyncConfig.disabled) return;

        submitSpawnCycle();

        // --- Drain entity queue into array for RecursiveAction subdivision ---
        // poll()-based drain is safe even if another thread adds concurrently.
        // RecursiveAction needs random access for splitting, so we collect to an array.
        ForkJoinTask<?> entityTask = null;
        int entityCount = 0;
        EntityTickEntry[] entityBatch = null;

        {
            EntityTickEntry entry;
            ArrayList<EntityTickEntry> buf = null;
            while ((entry = pendingEntityQueue.poll()) != null) {
                if (buf == null) buf = new ArrayList<>();
                buf.add(entry);
            }
            if (buf != null) {
                entityCount = buf.size();
                entityBatch = buf.toArray(new EntityTickEntry[entityCount]);
                currentEntities.set(entityCount);
                entityTask = new EntityTickBatch(entityBatch, 0, entityCount);
                tickPool.execute(entityTask);
            }
        }

        // --- Drain despawn queue ---
        ForkJoinTask<?> despawnTask = null;
        {
            Entity entry;
            ArrayList<Entity> buf = null;
            while ((entry = pendingDespawnQueue.poll()) != null) {
                if (buf == null) buf = new ArrayList<>();
                buf.add(entry);
            }
            if (buf != null) {
                int dCount = buf.size();
                Entity[] despawnBatch = buf.toArray(new Entity[dCount]);
                despawnTask = new DespawnBatch(despawnBatch, 0, dCount);
                tickPool.execute(despawnTask);
            }
        }

        // --- Drain external tasks ---
        CompletableFuture<Void> externalFuture = null;
        {
            CompletableFuture<?> f;
            ArrayList<CompletableFuture<?>> externalTasks = null;
            while ((f = externalTaskQueue.poll()) != null) {
                if (externalTasks == null) externalTasks = new ArrayList<>();
                externalTasks.add(f);
            }
            if (externalTasks != null) {
                externalFuture = CompletableFuture.allOf(
                    externalTasks.toArray(CompletableFuture[]::new)
                );
            }
        }

        // --- Wait for completion while doing useful work ---
        // Instead of pure spin-wait, we interleave chunk source polling (which processes
        // light updates, chunk loads, etc.) with brief parks when there's no work.
        while (true) {
            boolean allDone =
                (entityTask == null || entityTask.isDone()) &&
                (despawnTask == null || despawnTask.isDone()) &&
                (externalFuture == null || externalFuture.isDone());

            if (allDone) break;

            boolean didWork = false;
            for (ServerLevel world : server.getAllLevels()) {
                didWork |= world.getChunkSource().pollTask();
            }

            if (!didWork) {
                LockSupport.parkNanos(1_000L);
            }
        }

        // --- Join and report errors ---
        if (entityTask != null) {
            entityTask.quietlyJoin();
            if (entityTask.isCompletedAbnormally()) {
                LOGGER.error(
                    "Entity tick batch error",
                    entityTask.getException()
                );
            }
            // entityBatch is a local array - no need to null-fill, it's immediately GC-eligible
            currentEntities.set(0);
        }

        if (despawnTask != null) {
            despawnTask.quietlyJoin();
            if (despawnTask.isCompletedAbnormally()) {
                LOGGER.error("Despawn batch error", despawnTask.getException());
            }
            // despawnBatch is local, GC-eligible immediately
        }

        // Final poll to process any tasks generated during the async tick
        for (ServerLevel world : server.getAllLevels()) {
            world.getChunkSource().pollTask();
        }
    }

    public static void stop() {
        isShuttingDown = true;

        ForkJoinTask<?> spawn = currentSpawnTask;
        if (spawn != null && !spawn.isDone()) {
            spawn.quietlyJoin();
        }

        // Drain and await remaining external tasks
        ArrayList<CompletableFuture<?>> remaining = new ArrayList<>();
        CompletableFuture<?> f;
        while ((f = externalTaskQueue.poll()) != null) {
            remaining.add(f);
        }
        if (!remaining.isEmpty()) {
            CompletableFuture.allOf(
                remaining.toArray(CompletableFuture[]::new)
            ).join();
        }

        if (tickPool != null) {
            tickPool.shutdown();
            boolean quiesced = tickPool.awaitQuiescence(10, TimeUnit.SECONDS);
            if (!quiesced) {
                LOGGER.warn("Pool did not stop in time, forcing shutdown");
                tickPool.shutdownNow();
            }
        }

        AsyncConfig.clearCaches();
        blacklistedEntity.clear();
        pendingEntityQueue.clear();
        pendingDespawnQueue.clear();
        spawnCollect.clear();
        spawnSubmit.clear();
    }
}
