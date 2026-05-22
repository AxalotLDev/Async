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
import java.util.concurrent.locks.LockSupport;
import java.util.Map;

import static com.axalotl.async.common.utils.TickStats.*;

public final class ParallelProcessor {

    private ParallelProcessor() {}

    public interface TickGuard {
        boolean async$tryBeginTick();
        void async$endTick();
        byte async$getSyncCache();
        void async$setSyncCache(byte v);
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(ParallelProcessor.class);

    private static volatile MinecraftServer server;

    public static MinecraftServer getServer() { return server; }

    public static volatile ExecutorService executor;
    public static final ThreadGroup ASYNC_GROUP = new ThreadGroup("Async-Tick-Pool");
    private static volatile boolean isShuttingDown = false;

    public static final MobCategory[] CATEGORIES = MobCategory.values();

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

    private static final ClassValue<Boolean> BLOCKED_CACHE = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> cls) {
            for (Class<?> base : BLOCKED_BASE_CLASSES) {
                if (base.isAssignableFrom(cls)) return Boolean.TRUE;
            }
            return Boolean.FALSE;
        }
    };

    private static boolean isBlocked(Class<?> cls) {
        return BLOCKED_CACHE.get(cls);
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

    private static final java.util.Map<Class<?>, Boolean> SYNC_CLASS_CACHE = new ConcurrentHashMap<>();

    public static void clearCaches() {
        SYNC_CLASS_CACHE.clear();
        ASYNC_API_SYNC_CACHE.clear();
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
        return Thread.currentThread() instanceof AsyncForkJoinWorkerThread;
    }

    public static int getPoolSize() {
        return executor instanceof ForkJoinPool pool ? pool.getParallelism() : 0;
    }

    public static void onEntityTickBatchEnd(long batchStartNanos) {
        if (RECORDING_TICKS_LEFT.get() > 0) {
            TOTAL_BATCH_WALL_NS.add(System.nanoTime() - batchStartNanos);
            RECORDING_TICKS_LEFT.decrementAndGet();
        }
        PortalTeleportationManager.drainPending();
    }

    public static void pumpUntilDone(CompletableFuture<?> future) {
        MinecraftServer s = server;
        if (s != null && s.isSameThread()) {
            s.blockingCount++;
            try {
                while (!future.isDone()) {
                    if (!s.pollTask()) {
                        LockSupport.parkNanos("Async pumpUntilDone", 100_000L);
                    }
                }
            } finally {
                s.blockingCount--;
            }
        } else {
            while (!future.isDone()) Thread.onSpinWait();
        }
        if (future.isCompletedExceptionally()) {
            try { future.join(); }
            catch (Exception e) { LOGGER.error("Async batch failed", e); }
        }
    }

    public static boolean shouldTickSynchronously(Entity entity) {
        if (isShuttingDown || AsyncConfig.disabled) return true;
        if (!BLACKLISTED_ENTITIES.isEmpty() && BLACKLISTED_ENTITIES.contains(entity.getUUID())) return true;

        TickGuard guard = (TickGuard) entity;
        byte cached = guard.async$getSyncCache();
        if (cached >= 0) return cached == 1;

        Class<?> cls = entity.getClass();
        boolean sync = isBlocked(cls)
                || entitySupportsAsyncApi(entity)
                || AsyncConfig.isEntitySynchronized(EntityType.getKey(entity.getType()));
        guard.async$setSyncCache(sync ? (byte)1 : (byte)0);
        return sync;
    }

    public static void tickEntity(ServerLevel world, Entity entity, boolean async) {
        long start = System.nanoTime();
        try {
            world.tickNonPassenger(entity);
        } catch (Exception e) {
            LOGGER.error("Error during {} tick. Entity: {}, UUID: {}", async ? "async" : "sync", entity.getType(), entity.getUUID(), e);
            if (async) {
                SYNC_CLASS_CACHE.put(entity.getClass(), Boolean.TRUE);
            }
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
