package com.axalotl.async.common;

import com.axalotl.async.common.config.AsyncConfig;
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
import net.minecraft.world.entity.vehicle.boat.Boat;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.chunk.LevelChunk;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ParallelProcessor {
    public static final Logger LOGGER = LogManager.getLogger(ParallelProcessor.class);

    @Getter
    @Setter
    private static MinecraftServer server;

    public static ForkJoinPool tickPool;
    public static final AtomicInteger currentEntities = new AtomicInteger();
    private static final Object ENTITY_ADD_LOCK = new Object();
    private static final AtomicInteger threadPoolID = new AtomicInteger();
    private static final Set<UUID> blacklistedEntity = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, Integer> portalTickSyncMap = new ConcurrentHashMap<>();
    private static final Map<String, Set<WeakReference<Thread>>> mcThreadTracker = new ConcurrentHashMap<>();
    private static final ConcurrentLinkedQueue<CompletableFuture<?>> taskQueue = new ConcurrentLinkedQueue<>();
    private static final ConcurrentLinkedQueue<CompletableFuture<Void>> spawnQueue = new ConcurrentLinkedQueue<>();
    private static volatile boolean isShuttingDown = false;


    public static final Set<Class<?>> BLOCKED_ENTITIES = Set.of(
            FallingBlockEntity.class,
            Shulker.class,
            Boat.class
    );

    public static void setupThreadPool(int parallelism, Class<?> asyncClass) {
        isShuttingDown = false;

        ForkJoinPool.ForkJoinWorkerThreadFactory threadFactory = pool -> {
            ForkJoinWorkerThread worker = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
            worker.setName("Async-Tick-Pool-Thread-" + threadPoolID.getAndIncrement());
            registerThread("Async-Tick", worker);
            worker.setDaemon(true);
            worker.setPriority(Thread.NORM_PRIORITY);
            worker.setContextClassLoader(asyncClass.getClassLoader());
            return worker;
        };

        tickPool = new ForkJoinPool(parallelism, threadFactory, (t, e) ->
                LOGGER.error("Uncaught exception in thread {}: {}", t.getName(), e), true);
        LOGGER.info("Initialized Pool with {} threads", parallelism);
    }


    public static void registerThread(String poolName, Thread thread) {
        mcThreadTracker
                .computeIfAbsent(poolName, key -> ConcurrentHashMap.newKeySet())
                .add(new WeakReference<>(thread));
    }

    private static boolean isThreadInPool(Thread thread) {
        return mcThreadTracker.getOrDefault("Async-Tick", Set.of()).stream()
                .map(WeakReference::get)
                .anyMatch(thread::equals);
    }

    public static boolean isServerExecutionThread() {
        return isThreadInPool(Thread.currentThread());
    }

    public static void callEntityTick(ServerLevel world, Entity entity) {
        if (isShuttingDown) {
            tickSynchronously(world, entity);
            return;
        }

        if (shouldTickSynchronously(entity)) {
            tickSynchronously(world, entity);
        } else {
            if (!tickPool.isShutdown() && !tickPool.isTerminated()) {
                CompletableFuture<Void> future = CompletableFuture.runAsync(() ->
                        performAsyncEntityTick(world, entity), tickPool
                ).exceptionally(e -> {
                    logEntityError("Error in async tick, switching to synchronous", entity, e);
                    blacklistedEntity.add(entity.getUUID());
                    return null;
                });
                taskQueue.add(future);
            } else {
                tickSynchronously(world, entity);
            }
        }
    }

    public static boolean shouldTickSynchronously(Entity entity) {
        if (entity.level().isClientSide()) {
            return true;
        }

        UUID entityId = entity.getUUID();
        boolean requiresSyncTick = AsyncConfig.disabled ||
                entity instanceof Projectile ||
                entity instanceof AbstractMinecart ||
                entity instanceof ServerPlayer ||
                BLOCKED_ENTITIES.contains(entity.getClass()) ||
                blacklistedEntity.contains(entityId) ||
                AsyncConfig.isEntitySynchronized(EntityType.getKey(entity.getType()));

        if (requiresSyncTick) {
            return true;
        }

        if (portalTickSyncMap.containsKey(entityId)) {
            int ticksLeft = portalTickSyncMap.get(entityId);
            if (ticksLeft > 0) {
                portalTickSyncMap.put(entityId, ticksLeft - 1);
                return true;
            } else {
                portalTickSyncMap.remove(entityId);
            }
        }

        if (isPortalTickRequired(entity)) {
            portalTickSyncMap.put(entityId, 39);
            return true;
        }
        return false;
    }

    private static boolean isPortalTickRequired(Entity entity) {
        return entity.portalProcess != null && entity.portalProcess.isInsidePortalThisTick();
    }

    private static void tickSynchronously(ServerLevel world, Entity entity) {
        try {
            world.tickNonPassenger(entity);
        } catch (Exception e) {
            logEntityError("Error during synchronous tick", entity, e);
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

    public static Object getEntityAddLock() {
        return ENTITY_ADD_LOCK;
    }

    public static void asyncSpawnForChunk(
            ServerLevel level,
            LevelChunk chunk,
            NaturalSpawner.SpawnState spawnState,
            List<MobCategory> categories
    ) {
        if (!chunk.loaded) {
            return;
        }

        if (isShuttingDown || AsyncConfig.disabled || !AsyncConfig.enableAsyncSpawn) {
            NaturalSpawner.spawnForChunk(level, chunk, spawnState, categories);
            return;
        }

        if (categories.isEmpty()) {
            return;
        }

        List<MobCategory> categoriesCopy = List.copyOf(categories);

        CompletableFuture<Void> future = CompletableFuture.runAsync(() ->
                NaturalSpawner.spawnForChunk(level, chunk, spawnState, categoriesCopy), tickPool
        ).exceptionally(e -> {
            LOGGER.error("Error in async spawn for chunk {}: {}", chunk.getPos(), e.getMessage());
            return null;
        });

        spawnQueue.add(future);
    }

    public static void asyncDespawn(Entity entity) {
        if (isShuttingDown || AsyncConfig.disabled || !AsyncConfig.enableAsyncSpawn) {
            entity.checkDespawn();
            return;
        }

        CompletableFuture<Void> future = CompletableFuture.runAsync(
                entity::checkDespawn, tickPool
        ).exceptionally(e -> {
            LOGGER.error("Error in async despawn for {}: {}", entity.getType(), e.getMessage());
            return null;
        });

        taskQueue.add(future);
    }

    public static void addTask(CompletableFuture<?> future) {
        taskQueue.add(future);
    }

    public static void postEntityTick() {
        if (AsyncConfig.disabled) return;

        List<CompletableFuture<?>> entityTasks = new ArrayList<>();
        CompletableFuture<?> future;
        while ((future = taskQueue.poll()) != null) {
            entityTasks.add(future);
        }

        List<CompletableFuture<?>> spawnTasks = new ArrayList<>();
        CompletableFuture<Void> spawnFuture;
        while ((spawnFuture = spawnQueue.poll()) != null) {
            spawnTasks.add(spawnFuture);
        }

        List<CompletableFuture<?>> allTasks = new ArrayList<>(entityTasks.size() + spawnTasks.size());
        allTasks.addAll(entityTasks);
        allTasks.addAll(spawnTasks);

        if (allTasks.isEmpty()) {
            return;
        }

        CompletableFuture<Void> allTasksFuture = CompletableFuture.allOf(
                allTasks.toArray(new CompletableFuture[0])
        );

        while (!allTasksFuture.isDone()) {
            boolean didWork = false;
            for (ServerLevel world : server.getAllLevels()) {
                didWork |= world.getChunkSource().pollTask();
            }

            if (!didWork) {
                Thread.onSpinWait();
            }
        }

        for (ServerLevel world : server.getAllLevels()) {
            world.getChunkSource().pollTask();
        }


    }

    public static void stop() {
        isShuttingDown = true;

        List<CompletableFuture<?>> remaining = new ArrayList<>();
        CompletableFuture<?> f;
        while ((f = taskQueue.poll()) != null) {
            remaining.add(f);
        }
        CompletableFuture<Void> sf;
        while ((sf = spawnQueue.poll()) != null) {
            remaining.add(sf);
        }

        if (!remaining.isEmpty()) {
            CompletableFuture.allOf(remaining.toArray(new CompletableFuture[0])).join();
        }

        if (tickPool != null) {
            tickPool.shutdown();
            boolean quiesced = tickPool.awaitQuiescence(10, TimeUnit.SECONDS);
            if (!quiesced) {
                LOGGER.warn("The pool did not stop in time! Forcing shutdown...");
                tickPool.shutdownNow();
            }
        }

        AsyncConfig.clearCaches();
        blacklistedEntity.clear();
        portalTickSyncMap.clear();
    }

    private static void logEntityError(String message, Entity entity, Throwable e) {
        LOGGER.error("{} Entity Type: {}, UUID: {}", message, entity.getType().toString(), entity.getUUID(), e);
    }
}