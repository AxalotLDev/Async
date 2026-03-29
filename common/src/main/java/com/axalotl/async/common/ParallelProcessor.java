package com.axalotl.async.common;

import com.axalotl.async.common.config.AsyncConfig;
import com.axalotl.async.common.parallelised.utils.PortalTeleportationManager;
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
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ParallelProcessor {
    public static final Logger LOGGER = LogManager.getLogger(ParallelProcessor.class);

    @Setter
    private static MinecraftServer server;

    public static MinecraftServer getServer() {
        return server;
    }

    public static final AtomicInteger currentEntities = new AtomicInteger();
    private static final AtomicInteger threadPoolID = new AtomicInteger();
    public static ExecutorService tickPool;
    private static final Set<UUID> blacklistedEntity = ConcurrentHashMap.newKeySet();
    private static final Map<String, Set<WeakReference<Thread>>> mcThreadTracker = new ConcurrentHashMap<>();
    private static final ThreadLocal<Boolean> IS_POOL_THREAD = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private static final Object ENTITY_ADD_LOCK = new Object();
    public static volatile it.unimi.dsi.fastutil.longs.LongOpenHashSet spawnableChunkPositions;
    public static final Set<Class<?>> BLOCKED_ENTITIES = Set.of(
            FallingBlockEntity.class,
            Shulker.class,
            AbstractBoat.class
    );
    private static volatile boolean isShuttingDown = false;

    public static void setupThreadPool(int parallelism, Class<?> asyncClass) {
        PortalTeleportationManager.init(server);
        isShuttingDown = false;
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(() -> {
                IS_POOL_THREAD.set(Boolean.TRUE);
                runnable.run();
            }, "Async-Tick-Pool-Thread-" + threadPoolID.getAndIncrement());
            registerThread("Async-Tick", thread);
            thread.setDaemon(false);
            thread.setPriority(Thread.NORM_PRIORITY - 1);
            thread.setContextClassLoader(asyncClass.getClassLoader());
            return thread;
        };
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                parallelism,
                parallelism,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(),
                threadFactory
        );
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
        executor.allowCoreThreadTimeOut(false);
        executor.prestartAllCoreThreads();
        tickPool = executor;
        LOGGER.info("Initialized Pool with {} threads", parallelism);
    }

    public static void registerThread(String poolName, Thread thread) {
        mcThreadTracker
                .computeIfAbsent(poolName, key -> ConcurrentHashMap.newKeySet())
                .add(new WeakReference<>(thread));
    }

    public static boolean isServerExecutionThread() {
        return IS_POOL_THREAD.get();
    }

    public static int getPoolSize() {
        return ((ThreadPoolExecutor) tickPool).getCorePoolSize();
    }

    public static Object getEntityAddLock() {
        return ENTITY_ADD_LOCK;
    }

    @SuppressWarnings("unchecked")
    public static void callEntityTickBatch(ServerLevel world, List<Entity> entities) {
        if (entities.isEmpty()) return;
        if (AsyncConfig.disabled) {
            entities.forEach(e -> tickSynchronously(world, e));
            return;
        }

        List<Entity> asyncEntities = new ArrayList<>();
        List<Entity> syncEntities = new ArrayList<>();
        for (Entity entity : entities) {
            if (shouldTickSynchronously(entity)) {
                syncEntities.add(entity);
            } else {
                asyncEntities.add(entity);
            }
        }

        int poolSize = getPoolSize();
        int chunkSize = (asyncEntities.size() + poolSize - 1) / poolSize;

        List<Future<Void>> futures = new ArrayList<>();
        for (int i = 0; i < asyncEntities.size(); i += chunkSize) {
            List<Entity> chunk = asyncEntities.subList(i, Math.min(i + chunkSize, asyncEntities.size()));
            Future<Void> future = (Future<Void>) tickPool.submit(() -> {
                for (Entity entity : chunk) {
                    performAsyncEntityTick(world, entity);
                }
            });
            futures.add(future);
        }

        for (Entity e : syncEntities) {
            tickSynchronously(world, e);
        }

        boolean allDone;
        do {
            allDone = true;
            for (int fi = 0; fi < futures.size(); fi++) {
                if (!futures.get(fi).isDone()) { allDone = false; break; }
            }
            if (!allDone) {
                boolean pumped = false;
                for (ServerLevel lvl : server.getAllLevels()) {
                    pumped |= lvl.getChunkSource().pollTask();
                }
                if (!pumped) {
                    Thread.onSpinWait();
                }
            }
        } while (!allDone);

        for (Future<Void> future : futures) {
            try {
                future.get();
            } catch (Exception e) {
                LOGGER.error("Error in async entity tick", e);
            }
        }
    }

    public static boolean shouldTickSynchronously(Entity entity) {
        if (isShuttingDown) {
            return true;
        }
        if (entity.level().isClientSide()) {
            return true;
        }

        UUID entityId = entity.getUUID();

        return AsyncConfig.disabled ||
                entity instanceof Projectile ||
                entity instanceof AbstractMinecart ||
                entity instanceof ServerPlayer ||
                BLOCKED_ENTITIES.contains(entity.getClass()) ||
                blacklistedEntity.contains(entityId) ||
                AsyncConfig.isEntitySynchronized(EntityType.getKey(entity.getType()));
    }

    private static void tickSynchronously(ServerLevel world, Entity entity) {
        try {
            world.tickNonPassenger(entity);
        } catch (Exception e) {
            logEntityError(entity, e);
        }
    }

    private static void performAsyncEntityTick(ServerLevel world, Entity entity) {
        currentEntities.incrementAndGet();
        try {
            world.tickNonPassenger(entity);
        } finally {
            currentEntities.decrementAndGet();
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public static void stop() {
        isShuttingDown = true;
        if (tickPool != null) {
            LOGGER.info("Waiting for Async tickPool to shutdown...");
            tickPool.shutdown();
            try {
                tickPool.awaitTermination(60L, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
            }
        }
        AsyncConfig.clearCaches();
        blacklistedEntity.clear();
        PortalTeleportationManager.shutdown();
    }

    private static void logEntityError(Entity entity, Throwable e) {
        LOGGER.error("{} Entity Type: {}, UUID: {}", "Error during synchronous tick", entity.getType().toString(), entity.getUUID(), e);
    }
}