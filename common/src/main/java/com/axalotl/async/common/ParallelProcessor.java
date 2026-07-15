package com.axalotl.async.common;

import com.axalotl.async.api.utils.AsyncCompatible;
import com.axalotl.async.common.config.AsyncConfig;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.entity.vehicle.boat.Boat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

import static com.axalotl.async.common.utils.TickStats.*;

public class ParallelProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(ParallelProcessor.class);

    @Getter
    @Setter
    private static MinecraftServer server;

    //Thread pool
    public static ExecutorService executor;
    private static final AtomicInteger THREAD_POOL_ID = new AtomicInteger();
    private static volatile boolean isShuttingDown = false;

    //Blacklist
    private static final Set<UUID> BLACKLISTED_ENTITIES = ConcurrentHashMap.newKeySet();
    private static final Set<Class<?>> BLOCKED_ENTITIES = Set.of(
            FallingBlockEntity.class,
            Shulker.class,
            AbstractBoat.class,
            Boat.class,
            EnderDragon.class
    );

    //Cache
    private static final Map<Class<?>, Boolean> ASYNC_API_CACHE = new ConcurrentHashMap<>();

    private static final Map<Class<?>, Boolean> SYNC_BY_CLASS = new ConcurrentHashMap<>();

    public static void onSyncRulesChanged() {
        SYNC_BY_CLASS.clear();
    }

    //Threads
    private static final Map<String, Set<WeakReference<Thread>>> MC_THREAD_TRACKER = new ConcurrentHashMap<>();

    //Batch
    private static final int MIN_ASYNC_BATCH = 64;

    private static final int TASKS_PER_THREAD = 8;

    private static final int MIN_CHUNK = 8;

    public static int chunkSizeFor(int workItems) {
        int targetTasks = Math.max(1, getPoolSize()) * TASKS_PER_THREAD;
        int chunkSize = (workItems + targetTasks - 1) / targetTasks;
        return Math.max(MIN_CHUNK, chunkSize);
    }

    public static void setupThreadPool(int parallelism, Class<?> asyncClass) {
        isShuttingDown = false;

        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable, "Async-Tick-Pool-Thread-" + THREAD_POOL_ID.getAndIncrement());
            registerThread("Async-Tick", thread);
            thread.setDaemon(true);
            thread.setPriority(Thread.NORM_PRIORITY - 1);
            thread.setContextClassLoader(asyncClass.getClassLoader());
            return thread;
        };

        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                parallelism,
                parallelism,
                0L,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(),
                threadFactory
        );
        pool.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
        pool.allowCoreThreadTimeOut(false);
        pool.prestartAllCoreThreads();

        executor = pool;
        LOGGER.info("Initialized Pool with {} threads", parallelism);
    }

    public static void registerThread(String poolName, Thread thread) {
        MC_THREAD_TRACKER
                .computeIfAbsent(poolName, _ -> ConcurrentHashMap.newKeySet())
                .add(new WeakReference<>(thread));
    }

    private static boolean isThreadInPool(Thread thread) {
        return MC_THREAD_TRACKER.getOrDefault("Async-Tick", Set.of()).stream()
                .map(WeakReference::get)
                .anyMatch(thread::equals);
    }

    public static boolean isServerExecutionThread() {
        return isThreadInPool(Thread.currentThread());
    }

    public static int getPoolSize() {
        if (executor instanceof ThreadPoolExecutor pool) {
            return pool.getCorePoolSize();
        }
        return 0;
    }

    public static void callEntityTickBatch(ServerLevel world, List<Entity> entities) {
        if (entities.isEmpty()) return;

        if (AsyncConfig.disabled) {
            entities.forEach(e -> tickEntity(world, e, false));
            return;
        }

        List<Entity> asyncEntities = new ArrayList<>();
        List<Entity> syncEntities = new ArrayList<>();
        final Set<Entity> seen = Collections.newSetFromMap(new IdentityHashMap<>(entities.size()));
        for (Entity entity : entities) {
            if (entity == null || entity.isRemoved() || !seen.add(entity)) {
                continue;
            }
            if (shouldTickSynchronously(entity)) {
                syncEntities.add(entity);
            } else {
                asyncEntities.add(entity);
            }
        }
        if (asyncEntities.size() < MIN_ASYNC_BATCH) {
            asyncEntities.forEach(e -> tickEntity(world, e, false));
            syncEntities.forEach(e -> tickEntity(world, e, false));
            return;
        }

        final int chunkSize = chunkSizeFor(asyncEntities.size());
        final List<Runnable> work = new ArrayList<>();
        for (int i = 0; i < asyncEntities.size(); i += chunkSize) {
            final List<Entity> chunk = asyncEntities.subList(i, Math.min(i + chunkSize, asyncEntities.size()));
            work.add(() -> {
                for (Entity entity : chunk) {
                    tickEntity(world, entity, true);
                }
            });
        }

        syncEntities.forEach(e -> tickEntity(world, e, false));
        runParallel(work);
    }

    public static <T> void forEachParallel(List<T> items, java.util.function.Consumer<T> action) {
        if (items.isEmpty()) return;
        if (items.size() < MIN_ASYNC_BATCH) {
            items.forEach(action);
            return;
        }

        int chunkSize = chunkSizeFor(items.size());
        List<Runnable> work = new ArrayList<>();
        for (int i = 0; i < items.size(); i += chunkSize) {
            List<T> slice = items.subList(i, Math.min(i + chunkSize, items.size()));
            work.add(() -> slice.forEach(action));
        }
        runParallel(work);
    }

    private static void runParallel(List<Runnable> work) {
        if (work.isEmpty()) return;

        final Queue<Runnable> queue = new ConcurrentLinkedQueue<>(work);
        final int workers = Math.max(1, getPoolSize());
        final CountDownLatch done = new CountDownLatch(workers);
        for (int i = 0; i < workers; i++) {
            executor.execute(() -> {
                try {
                    drain(queue);
                } finally {
                    done.countDown();
                }
            });
        }

        awaitCompletion(done);
    }

    private static void drain(Queue<Runnable> queue) {
        Runnable chunk;
        while ((chunk = queue.poll()) != null) {
            runChunk(chunk);
        }
    }

    private static void runChunk(Runnable chunk) {
        try {
            chunk.run();
        } catch (Throwable e) {
            LOGGER.error("Error during parallel tick chunk", e);
        }
    }

    private static void pumpMainThreadTasks() {
        for (ServerLevel lvl : server.getAllLevels()) {
            lvl.getChunkSource().pollTask();
        }
    }

    private static void awaitCompletion(CountDownLatch done) {
        try {
            while (!done.await(200, TimeUnit.MICROSECONDS)) {
                pumpMainThreadTasks();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static boolean shouldTickSynchronously(Entity entity) {
        if (isShuttingDown || AsyncConfig.disabled || entity.portalProcess != null || entity.level().isClientSide()) {
            return true;
        }

        if (!BLACKLISTED_ENTITIES.isEmpty() && BLACKLISTED_ENTITIES.contains(entity.getUUID())) {
            return true;
        }

        Boolean cached = SYNC_BY_CLASS.get(entity.getClass());
        if (cached != null) {
            return cached;
        }

        boolean sync = entitySupportsAsyncApi(entity)
                || entity instanceof Projectile
                || entity instanceof Player
                || BLOCKED_ENTITIES.contains(entity.getClass())
                || AsyncConfig.isEntitySynchronized(EntityType.getKey(entity.getType()));
        SYNC_BY_CLASS.put(entity.getClass(), sync);
        return sync;
    }

    public static boolean entitySupportsAsyncApi(Entity entity) {
        Class<?> cls = entity.getClass();
        Boolean cached = ASYNC_API_CACHE.get(cls);
        if (cached != null) return cached;
        boolean result = !"minecraft".equals(EntityType.getKey(entity.getType()).getNamespace()) && !cls.isAnnotationPresent(AsyncCompatible.class);
        ASYNC_API_CACHE.put(cls, result);
        return result;
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
                } else {
                    TICK_TIME_NS.computeIfAbsent(type, _ -> new LongAdder()).add(elapsed);
                    TICK_COUNT.computeIfAbsent(type, _ -> new LongAdder()).increment();
                }
            }
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public static void stop() {
        isShuttingDown = true;

        if (executor != null) {
            LOGGER.info("Waiting for Async poll to shutdown...");
            executor.shutdown();
            try {
                executor.awaitTermination(60L, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOGGER.warn("Interrupted while waiting for thread pool shutdown", e);
            }
        }

        AsyncConfig.clearCaches();
        BLACKLISTED_ENTITIES.clear();
        resetEntityTickStats();
    }
}