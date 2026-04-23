package com.axalotl.async.common;

import com.axalotl.async.api.utils.AsyncCompatible;
import com.axalotl.async.common.config.AsyncConfig;
import com.axalotl.async.common.parallelised.utils.PortalTeleportationManager;
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
import net.minecraft.world.entity.vehicle.boat.Boat;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.WeakReference;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.List;
import java.util.Map;

import static com.axalotl.async.common.utils.TickStats.*;

public final class ParallelProcessor {

    private ParallelProcessor() {}

    private static final Logger LOGGER = LoggerFactory.getLogger(ParallelProcessor.class);

    private static volatile MinecraftServer server;

    public static MinecraftServer getServer() { return server; }

    public static volatile ExecutorService executor;
    public static final ThreadGroup ASYNC_GROUP = new ThreadGroup("Async-Tick-Pool");
    private static volatile boolean isShuttingDown = false;

    public static final MobCategory[] CATEGORIES = MobCategory.values();

    public static volatile it.unimi.dsi.fastutil.longs.LongOpenHashSet spawnableChunkPositions;

    private static final Set<UUID> BLACKLISTED_ENTITIES = ConcurrentHashMap.newKeySet();
    private static final Class<?>[] BLOCKED_BASE_CLASSES = {
            AbstractBoat.class,
            FallingBlockEntity.class,
            Shulker.class,
            Boat.class,
            Projectile.class,
            ServerPlayer.class,
            AbstractMinecart.class
    };

    private static final java.util.Map<Class<?>, Boolean> BLOCKED_CACHE = new ConcurrentHashMap<>();

    private static boolean isBlocked(Class<?> cls) {
        Boolean cached = BLOCKED_CACHE.get(cls);
        if (cached != null) return cached;
        boolean blocked = false;
        for (Class<?> base : BLOCKED_BASE_CLASSES) {
            if (base.isAssignableFrom(cls)) { blocked = true; break; }
        }
        BLOCKED_CACHE.put(cls, blocked);
        return blocked;
    }

    private static final java.util.Map<Class<?>, Boolean> ASYNC_API_SYNC_CACHE = new ConcurrentHashMap<>();

    public static boolean entitySupportsAsyncApi(Entity entity) {
        Class<?> cls = entity.getClass();
        Boolean cached = ASYNC_API_SYNC_CACHE.get(cls);
        if (cached != null) return cached;
        boolean result = !"minecraft".equals(EntityType.getKey(entity.getType()).getNamespace()) && !cls.isAnnotationPresent(AsyncCompatible.class);
        ASYNC_API_SYNC_CACHE.put(cls, result);
        return result;
    }

    private static final Map<String, Set<WeakReference<Thread>>> MC_THREAD_TRACKER = new ConcurrentHashMap<>();

    public static void setServer(MinecraftServer server) {
        ParallelProcessor.server = server;
        PortalTeleportationManager.init(server);
    }

    public static void setupThreadPool(int parallelism) {
        executor = createPool(parallelism);
    }


    private static ForkJoinPool createPool(int parallelism) {
        return new ForkJoinPool(parallelism, asyncWorkerFactory(), (t, e) -> LOGGER.error("Uncaught exception in thread {}", t.getName(), e),
                /* asyncMode       */ true,
                /* corePoolSize    */ parallelism,
                /* maximumPoolSize */ parallelism,
                /* minimumRunnable */ 1,
                /* saturate        */ pool -> true,
                /* keepAliveTime   */ 60L, TimeUnit.SECONDS);
    }

    private static final AtomicInteger WORKER_COUNTER = new AtomicInteger();

    private static ForkJoinPool.ForkJoinWorkerThreadFactory asyncWorkerFactory() {
        int priority = AsyncConfig.getThreadPriority();
        return pool -> {
            AsyncForkJoinWorkerThread t = new AsyncForkJoinWorkerThread(pool);
            t.setDaemon(true);
            t.setPriority(priority);
            t.setUncaughtExceptionHandler((thr, ex) -> LOGGER.error("Uncaught exception in thread {}", thr.getName(), ex));
            registerThread("Async-Tick", t);
            return t;
        };
    }

    private static final class AsyncForkJoinWorkerThread extends ForkJoinWorkerThread {
        AsyncForkJoinWorkerThread(ForkJoinPool pool) {
            super(ASYNC_GROUP, pool, false);
        }

        @Override
        protected void onStart() {
            super.onStart();
            setName("Async-Tick-Pool-Thread-" + WORKER_COUNTER.getAndIncrement());
        }
    }

    public static void registerThread(String poolName, Thread thread) {
        MC_THREAD_TRACKER.computeIfAbsent(poolName, _ -> ConcurrentHashMap.newKeySet()).add(new WeakReference<>(thread));
    }

    public static boolean isServerExecutionThread() {
        return Thread.currentThread().getThreadGroup() == ASYNC_GROUP;
    }

    public static int getPoolSize() {
        return executor instanceof ForkJoinPool pool ? pool.getParallelism() : 0;
    }

    public static void callEntityTickBatch(ServerLevel world, List<Entity> entities) {
        if (entities.isEmpty()) return;

        boolean recording = RECORDING_TICKS_LEFT.get() > 0;
        long batchStart = recording ? System.nanoTime() : 0L;

        if (AsyncConfig.disabled) {
            for (Entity e : entities) tickEntity(world, e, false);
            if (recording) {
                TOTAL_BATCH_WALL_NS.add(System.nanoTime() - batchStart);
                RECORDING_TICKS_LEFT.decrementAndGet();
            }
            return;
        }

        int n = entities.size();
        Entity[] async = new Entity[n];
        Entity[] sync  = new Entity[n];
        int asyncCount = 0, syncCount = 0;
        for (int i = 0; i < n; i++) {
            Entity e = entities.get(i);
            if (shouldTickSynchronously(e)) sync[syncCount++] = e;
            else                            async[asyncCount++] = e;
        }

        CompletableFuture<Void> allAsync = null;
        if (asyncCount > 0) {
            int poolSize  = getPoolSize();
            int chunkSize = Math.max(1, (asyncCount + poolSize - 1) / poolSize);
            int batchCount = (asyncCount + chunkSize - 1) / chunkSize;
            CompletableFuture<?>[] futures = new CompletableFuture[batchCount];

            for (int b = 0, i = 0; i < asyncCount; i += chunkSize, b++) {
                final int start = i;
                final int end   = Math.min(i + chunkSize, asyncCount);
                futures[b] = CompletableFuture.runAsync(() -> {
                    for (int k = start; k < end; k++) tickEntity(world, async[k], true);
                }, executor);
            }
            allAsync = CompletableFuture.allOf(futures);
        }

        for (int i = 0; i < syncCount; i++) tickEntity(world, sync[i], false);

        if (allAsync != null) pumpUntilDone(allAsync);
        PortalTeleportationManager.drainPending();
        if (recording) {
            TOTAL_BATCH_WALL_NS.add(System.nanoTime() - batchStart);
            RECORDING_TICKS_LEFT.decrementAndGet();
        }
    }

    public static void pumpUntilDone(CompletableFuture<?> future) {
        MinecraftServer s = server;
        if (s != null && s.isSameThread()) {
            s.managedBlock(future::isDone);
        } else {
            while (!future.isDone()) Thread.onSpinWait();
        }
        if (future.isCompletedExceptionally()) {
            try { future.join(); }
            catch (Exception e) { LOGGER.error("Async batch failed", e); }
        }
    }

    public static boolean shouldTickSynchronously(Entity entity) {
        if (isShuttingDown || entity.level().isClientSide()) return true;
        return AsyncConfig.disabled
                || entitySupportsAsyncApi(entity)
                || isBlocked(entity.getClass())
                || BLACKLISTED_ENTITIES.contains(entity.getUUID())
                || AsyncConfig.isEntitySynchronized(EntityType.getKey(entity.getType()));
    }

    private static void tickEntity(ServerLevel world, Entity entity, boolean async) {
        long start = System.nanoTime();
        try {
            world.tickNonPassenger(entity);
        } catch (Exception e) {
            LOGGER.error("Error during {} tick. Entity: {}, UUID: {}",
                    async ? "async" : "sync", entity.getType(), entity.getUUID(), e);
        } finally {
            if (RECORDING_TICKS_LEFT.get() > 0) {
                EntityType<?> type = entity.getType();
                long elapsed = System.nanoTime() - start;
                if (async) {
                    ASYNC_TICK_TIME_NS.computeIfAbsent(type, _ -> new LongAdder()).add(elapsed);
                    ASYNC_TICK_COUNT.computeIfAbsent(type, _ -> new LongAdder()).increment();
                    TOTAL_ASYNC_TIME_NS.add(elapsed);
                    TOTAL_ASYNC_COUNT.increment();
                } else {
                    TICK_TIME_NS.computeIfAbsent(type, _ -> new LongAdder()).add(elapsed);
                    TICK_COUNT.computeIfAbsent(type, _ -> new LongAdder()).increment();
                    TOTAL_SYNC_TIME_NS.add(elapsed);
                    TOTAL_SYNC_COUNT.increment();
                }
            }
        }
    }

    public static void stop() {
        isShuttingDown = true;

        if (executor != null) {
            LOGGER.info("Waiting for Async pool to shutdown...");
            executor.shutdown();
            try {
                if (!executor.awaitTermination(60L, TimeUnit.SECONDS)) {
                    LOGGER.warn("Async pool did not terminate within 60 seconds, forcing shutdown");
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOGGER.warn("Interrupted while waiting for thread pool shutdown", e);
                executor.shutdownNow();
            }
        }

        AsyncConfig.clearCaches();
        BLACKLISTED_ENTITIES.clear();
        PortalTeleportationManager.shutdown();
        resetEntityTickStats();
    }
}
