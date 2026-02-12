package com.axalotl.async.common;

import com.axalotl.async.common.config.AsyncConfig;
import com.axalotl.async.common.parallelised.utils.VanishCompat;
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

import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

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
    private static final Map<String, Set<WeakReference<Thread>>> mcThreadTracker = new ConcurrentHashMap<>();
    private static volatile boolean isShuttingDown = false;

    private static int despawnCount = 0;
    private static int pendingCount = 0;
    private static final int ENTITY_GRAIN = 64;
    private static final int DESPAWN_GRAIN = 128;
    private static final int INITIAL_CAPACITY = 16384;
    private static volatile ForkJoinTask<?> currentSpawnTask;
    private static Entity[] pendingDespawns = new Entity[4096];
    private static Entity[] pendingEntities = new Entity[INITIAL_CAPACITY];
    private static final ArrayList<Runnable> pendingSpawnWork = new ArrayList<>();
    private static ServerLevel[] pendingWorlds = new ServerLevel[INITIAL_CAPACITY];
    private static final ConcurrentLinkedQueue<CompletableFuture<?>> externalTaskQueue = new ConcurrentLinkedQueue<>();

    public static final Set<Class<?>> BLOCKED_ENTITIES = Set.of(
            FallingBlockEntity.class,
            Shulker.class,
            AbstractBoat.class
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
        VanishCompat.apply();
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
            world.tickNonPassenger(entity);
            return;
        }

        if (shouldTickSynchronously(entity)) {
            world.tickNonPassenger(entity);
            return;
        }

        int idx = pendingCount;
        if (idx >= pendingEntities.length) {
            int newCap = pendingEntities.length << 1;
            pendingWorlds = Arrays.copyOf(pendingWorlds, newCap);
            pendingEntities = Arrays.copyOf(pendingEntities, newCap);
        }
        pendingWorlds[idx] = world;
        pendingEntities[idx] = entity;
        pendingCount = idx + 1;
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

        return entity.portalProcess != null;
    }

    public static Object getEntityAddLock() {
        return ENTITY_ADD_LOCK;
    }

    static final class EntityTickBatch extends RecursiveAction {
        private final ServerLevel[] worlds;
        private final Entity[] entities;
        private final int from;
        private final int to;

        EntityTickBatch(ServerLevel[] worlds, Entity[] entities, int from, int to) {
            this.worlds = worlds;
            this.entities = entities;
            this.from = from;
            this.to = to;
        }

        @Override
        protected void compute() {
            int size = to - from;
            if (size <= ENTITY_GRAIN) {
                for (int i = from; i < to; i++) {
                    worlds[i].tickNonPassenger(entities[i]);
                }
            } else {
                int mid = (from + to) >>> 1;
                invokeAll(
                        new EntityTickBatch(worlds, entities, from, mid),
                        new EntityTickBatch(worlds, entities, mid, to)
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
        pendingSpawnWork.add(() -> NaturalSpawner.spawnForChunk(level, chunk, spawnState, categoriesCopy));
    }

    private static void submitSpawnCycle() {
        if (pendingSpawnWork.isEmpty()) {
            return;
        }

        ForkJoinTask<?> prev = currentSpawnTask;
        if (prev != null && !prev.isDone()) {
            pendingSpawnWork.clear();
            return;
        }

        Runnable[] work = pendingSpawnWork.toArray(new Runnable[0]);
        pendingSpawnWork.clear();

        currentSpawnTask = tickPool.submit(() -> {
            for (Runnable task : work) {
                task.run();
            }
        });
    }

    public static void asyncDespawn(Entity entity) {
        if (isShuttingDown || AsyncConfig.disabled || !AsyncConfig.enableAsyncSpawn) {
            entity.checkDespawn();
            return;
        }

        int idx = despawnCount;
        if (idx >= pendingDespawns.length) {
            pendingDespawns = Arrays.copyOf(pendingDespawns, pendingDespawns.length << 1);
        }
        pendingDespawns[idx] = entity;
        despawnCount = idx + 1;
    }

    public static void addTask(CompletableFuture<?> future) {
        externalTaskQueue.add(future);
    }

    public static void postEntityTick() {
        if (AsyncConfig.disabled) return;

        submitSpawnCycle();

        ForkJoinTask<?> entityTask = null;
        ForkJoinTask<?> despawnTask = null;

        int entityCount = pendingCount;
        pendingCount = 0;

        if (entityCount > 0) {
            currentEntities.set(entityCount);
            entityTask = new EntityTickBatch(pendingWorlds, pendingEntities, 0, entityCount);
            tickPool.execute(entityTask);
        }

        int dCount = despawnCount;
        despawnCount = 0;

        if (dCount > 0) {
            despawnTask = new DespawnBatch(pendingDespawns, 0, dCount);
            tickPool.execute(despawnTask);
        }

        CompletableFuture<Void> externalFuture = null;
        List<CompletableFuture<?>> externalTasks = null;
        CompletableFuture<?> f;
        while ((f = externalTaskQueue.poll()) != null) {
            if (externalTasks == null) {
                externalTasks = new ArrayList<>();
            }
            externalTasks.add(f);
        }
        if (externalTasks != null) {
            externalFuture = CompletableFuture.allOf(externalTasks.toArray(new CompletableFuture[0]));
        }

        while (true) {
            boolean allDone = entityTask == null || entityTask.isDone();
            if (despawnTask != null && !despawnTask.isDone()) {
                allDone = false;
            }
            if (externalFuture != null && !externalFuture.isDone()) {
                allDone = false;
            }

            if (allDone) break;

            boolean didWork = false;
            for (ServerLevel world : server.getAllLevels()) {
                didWork |= world.getChunkSource().pollTask();
            }

            if (!didWork) {
                LockSupport.parkNanos(1_000L);
            }
        }

        if (entityTask != null) {
            entityTask.quietlyJoin();
            if (entityTask.isCompletedAbnormally()) {
                LOGGER.error("Entity tick batch error", entityTask.getException());
            }
            Arrays.fill(pendingWorlds, 0, entityCount, null);
            Arrays.fill(pendingEntities, 0, entityCount, null);
            currentEntities.set(0);
        }

        if (despawnTask != null) {
            despawnTask.quietlyJoin();
            if (despawnTask.isCompletedAbnormally()) {
                LOGGER.error("Despawn batch error", despawnTask.getException());
            }
            Arrays.fill(pendingDespawns, 0, dCount, null);
        }
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

        List<CompletableFuture<?>> remaining = new ArrayList<>();
        CompletableFuture<?> f;
        while ((f = externalTaskQueue.poll()) != null) {
            remaining.add(f);
        }
        if (!remaining.isEmpty()) {
            CompletableFuture.allOf(remaining.toArray(new CompletableFuture[0])).join();
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
        pendingSpawnWork.clear();
        pendingCount = 0;
        despawnCount = 0;
    }
}