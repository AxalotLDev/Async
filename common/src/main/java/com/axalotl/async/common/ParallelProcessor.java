package com.axalotl.async.common;

import com.axalotl.async.common.config.AsyncConfig;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.monster.Shulker;
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
            Boat.class
    );

    //Threads
    private static final Map<String, Set<WeakReference<Thread>>> MC_THREAD_TRACKER = new ConcurrentHashMap<>();

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

    @SuppressWarnings("unchecked")
    public static void callEntityTickBatch(ServerLevel world, List<Entity> entities) {
        if (entities.isEmpty()) return;

        if (AsyncConfig.disabled) {
            entities.forEach(e -> tickEntity(world, e, false));
            RECORDING_TICKS_LEFT.decrementAndGet();
            return;
        }

        final int poolSize = getPoolSize();
        final int chunkSize = (entities.size() + poolSize - 1) / poolSize;

        final List<Future<Void>> futures = new ArrayList<>();

        for (int i = 0; i < entities.size(); i += chunkSize) {
            final List<Entity> chunk = entities.subList(i, Math.min(i + chunkSize, entities.size()));
            Future<Void> future = (Future<Void>) executor.submit(() -> {
                for (Entity entity : chunk) {
                    if (!shouldTickSynchronously(entity)) {
                        tickEntity(world, entity, true);
                    }
                }
            });
            futures.add(future);
        }

        entities.stream()
                .filter(ParallelProcessor::shouldTickSynchronously)
                .forEach(e -> tickEntity(world, e, false));

        waitForFutures(futures);
        RECORDING_TICKS_LEFT.updateAndGet(v -> v > 0 ? v - 1 : 0);
    }

    private static void waitForFutures(List<Future<Void>> futures) {
        boolean allDone;
        do {
            allDone = futures.stream().allMatch(Future::isDone);
            if (!allDone) {
                boolean pumped = false;
                for (ServerLevel lvl : server.getAllLevels()) {
                    pumped |= lvl.getChunkSource().pollTask();
                }
                if (!pumped) Thread.onSpinWait();
            }
        } while (!allDone);

        for (Future<Void> future : futures) {
            try {
                future.get();
            } catch (ExecutionException e) {
                LOGGER.error("Error in async entity tick", e.getCause());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    public static boolean shouldTickSynchronously(Entity entity) {
        if (isShuttingDown || entity.level().isClientSide() || entity.portalProcess != null) {
            return true;
        }

        UUID entityId = entity.getUUID();

        return AsyncConfig.disabled
                || entity instanceof Projectile
                || entity instanceof ServerPlayer
                || BLOCKED_ENTITIES.contains(entity.getClass())
                || BLACKLISTED_ENTITIES.contains(entityId)
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