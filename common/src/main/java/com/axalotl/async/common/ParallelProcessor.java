package com.axalotl.async.common;

import com.axalotl.async.common.config.AsyncConfig;
import com.axalotl.async.common.parallelised.utils.VanishCompat;
import io.netty.util.internal.shaded.org.jctools.queues.MpscUnboundedArrayQueue;
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

    public static final Logger LOGGER = LogManager.getLogger(ParallelProcessor.class);

    @Getter
    @Setter
    private static MinecraftServer server;

    public static ForkJoinPool tickPool;
    public static final AtomicInteger currentEntities = new AtomicInteger();
    private static final Object ENTITY_ADD_LOCK = new Object();
    private static final AtomicInteger threadPoolID = new AtomicInteger();
    private static final Set<UUID> blacklistedEntity = ConcurrentHashMap.newKeySet();
    private static volatile boolean isShuttingDown = false;

    private static final ThreadLocal<Boolean> IS_ASYNC_TICK_THREAD = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private static int pendingCount = 0;
    private static int despawnCount = 0;
    private static final int ENTITY_GRAIN = 64;
    private static final int DESPAWN_GRAIN = 128;
    private static final int INITIAL_CAPACITY = 16384;
    private static final long POST_TICK_TIMEOUT_SECS = 10;
    private static Entity[] pendingDespawns = new Entity[4096];
    private static ArrayList<SpawnEntry> spawnSubmit = new ArrayList<>();
    private static ArrayList<SpawnEntry> spawnCollect = new ArrayList<>();
    private static Entity[] pendingEntities = new Entity[INITIAL_CAPACITY];
    private static ServerLevel[] pendingWorlds = new ServerLevel[INITIAL_CAPACITY];
    private static final Queue<CompletableFuture<?>> externalTaskQueue = new MpscUnboundedArrayQueue<>(256);

    record SpawnEntry(ServerLevel level, LevelChunk chunk, NaturalSpawner.SpawnState spawnState,
                      List<MobCategory> categories) {
    }

    public static final Set<Class<?>> BLOCKED_ENTITIES = Set.of(
            FallingBlockEntity.class,
            Shulker.class,
            AbstractBoat.class
    );

    private static final class AsyncTickWorkerThread
            extends ForkJoinWorkerThread {

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
                parallelism, pool -> {
            AsyncTickWorkerThread worker = new AsyncTickWorkerThread(pool);
            worker.setName("Async-Tick-Pool-Thread-" + threadPoolID.getAndIncrement());
            worker.setDaemon(true);
            worker.setPriority(Thread.NORM_PRIORITY);
            worker.setContextClassLoader(asyncClass.getClassLoader());
            return worker;
        },
                (t, e) -> LOGGER.error("Uncaught exception in thread {}: {}", t.getName(), e), true);

        LOGGER.info("Initialized Pool with {} threads", parallelism);
        VanishCompat.apply();
    }

    public static boolean isServerExecutionThread() {
        return IS_ASYNC_TICK_THREAD.get();
    }

    public static void callEntityTick(ServerLevel world, Entity entity) {
        if (isShuttingDown || tickPool == null || tickPool.isShutdown()) {
            safeTickSync(world, entity);
            return;
        }

        if (shouldTickSynchronously(entity)) {
            safeTickSync(world, entity);
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

    private static void safeTickSync(ServerLevel world, Entity entity) {
        try {
            world.tickNonPassenger(entity);
        } catch (Throwable t) {
            LOGGER.error("Error during synchronous entity tick. Type: {}, UUID: {}", entity.getType(), entity.getUUID(), t);
        }
    }

    public static boolean shouldTickSynchronously(Entity entity) {
        if (AsyncConfig.disabled) return true;
        if (entity instanceof ServerPlayer) return true;
        if (entity instanceof Projectile) return true;
        if (entity instanceof AbstractMinecart) return true;
        if (entity.level().isClientSide()) return true;
        if (BLOCKED_ENTITIES.contains(entity.getClass())) return true;
        if (entity.portalProcess != null) return true;
        if (blacklistedEntity.contains(entity.getUUID())) return true;
        return AsyncConfig.isEntitySynchronized(EntityType.getKey(entity.getType()));
    }

    public static Object getEntityAddLock() {
        return ENTITY_ADD_LOCK;
    }

    static final class EntityTickBatch extends RecursiveAction {

        private final ServerLevel[] worlds;
        private final Entity[] entities;
        private final boolean[] completed;
        private final int from;
        private final int to;

        EntityTickBatch(ServerLevel[] worlds, Entity[] entities, boolean[] completed, int from, int to) {
            this.worlds = worlds;
            this.entities = entities;
            this.completed = completed;
            this.from = from;
            this.to = to;
        }

        @Override
        protected void compute() {
            int size = to - from;
            if (size <= ENTITY_GRAIN) {
                for (int i = from; i < to; i++) {
                    try {
                        worlds[i].tickNonPassenger(entities[i]);
                        completed[i] = true;
                    } catch (Throwable t) {
                        LOGGER.error("Async entity tick error, blacklisting entity. Type: {}, UUID: {}", entities[i].getType(), entities[i].getUUID(), t);
                        blacklistedEntity.add(entities[i].getUUID());
                    }
                }
            } else {
                int mid = (from + to) >>> 1;
                invokeAll(new EntityTickBatch(worlds, entities, completed, from, mid), new EntityTickBatch(worlds, entities, completed, mid, to));
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
                    try {
                        entities[i].checkDespawn();
                    } catch (Throwable t) {
                        LOGGER.error("Async despawn error for entity. Type: {}, UUID: {}", entities[i].getType(), entities[i].getUUID(), t);
                    }
                }
            } else {
                int mid = (from + to) >>> 1;
                invokeAll(new DespawnBatch(entities, from, mid), new DespawnBatch(entities, mid, to));
            }
        }
    }

    public static void asyncSpawnForChunk(ServerLevel level, LevelChunk chunk, NaturalSpawner.SpawnState spawnState, List<MobCategory> categories) {
        if (!chunk.loaded) return;

        if (isShuttingDown || tickPool == null || tickPool.isShutdown() || AsyncConfig.disabled || !AsyncConfig.enableAsyncSpawn) {
            NaturalSpawner.spawnForChunk(level, chunk, spawnState, categories);
            return;
        }

        if (categories.isEmpty()) return;

        spawnCollect.add(new SpawnEntry(level, chunk, spawnState, List.copyOf(categories)));
    }

    private static final IdentityHashMap<ServerLevel, ForkJoinTask<?>> worldSpawnTasks = new IdentityHashMap<>();

    private static void submitSpawnCycle() {
        if (spawnCollect.isEmpty()) return;

        ServerLevel world = spawnCollect.getFirst().level();

        ForkJoinTask<?> prev = worldSpawnTasks.get(world);
        if (prev != null && !prev.isDone()) {
            prev.quietlyJoin();
        }

        ArrayList<SpawnEntry> ready = spawnCollect;
        spawnCollect = spawnSubmit;
        spawnCollect.clear();
        spawnSubmit = ready;

        worldSpawnTasks.put(world, tickPool.submit(() -> {
            for (int i = 0, n = ready.size(); i < n; i++) {
                SpawnEntry s = ready.get(i);
                NaturalSpawner.spawnForChunk(s.level(), s.chunk(), s.spawnState(), s.categories());
            }
        }));
    }

    public static void asyncDespawn(Entity entity) {
        if (isShuttingDown || tickPool == null || tickPool.isShutdown() || AsyncConfig.disabled || !AsyncConfig.enableAsyncSpawn) {
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
        int entityCount = pendingCount;
        boolean[] entityCompleted = null;
        pendingCount = 0;

        if (entityCount > 0) {
            entityCompleted = new boolean[entityCount];
            currentEntities.set(entityCount);
            entityTask = new EntityTickBatch(pendingWorlds, pendingEntities, entityCompleted, 0, entityCount);
            tickPool.execute(entityTask);
        }

        ForkJoinTask<?> despawnTask = null;
        int dCount = despawnCount;
        despawnCount = 0;

        if (dCount > 0) {
            despawnTask = new DespawnBatch(pendingDespawns, 0, dCount);
            tickPool.execute(despawnTask);
        }

        CompletableFuture<Void> externalFuture = null;
        {
            CompletableFuture<?> f;
            ArrayList<CompletableFuture<?>> externalTasks = null;
            while ((f = externalTaskQueue.poll()) != null) {
                if (externalTasks == null) externalTasks = new ArrayList<>();
                externalTasks.add(f);
            }
            if (externalTasks != null) {
                externalFuture = CompletableFuture.allOf(externalTasks.toArray(CompletableFuture[]::new));
            }
        }

        long deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(POST_TICK_TIMEOUT_SECS);

        while (true) {
            boolean allDone = (entityTask == null || entityTask.isDone())
                    && (despawnTask == null || despawnTask.isDone())
                    && (externalFuture == null || externalFuture.isDone());

            if (allDone) break;

            if (System.nanoTime() > deadlineNanos) {
                LOGGER.error("postEntityTick timed out after {}s. Entity: {}, Despawn: {}, External: {}",
                        POST_TICK_TIMEOUT_SECS,
                        entityTask == null || entityTask.isDone(),
                        despawnTask == null || despawnTask.isDone(),
                        externalFuture == null || externalFuture.isDone());

                if (entityTask != null && !entityTask.isDone()) entityTask.cancel(true);
                if (despawnTask != null && !despawnTask.isDone()) despawnTask.cancel(true);
                if (externalFuture != null && !externalFuture.isDone()) externalFuture.cancel(true);

                if (entityCompleted != null) {
                    int rescued = 0;
                    for (int i = 0; i < entityCount; i++) {
                        if (!entityCompleted[i]) {
                            safeTickSync(pendingWorlds[i], pendingEntities[i]);
                            rescued++;
                        }
                    }
                    if (rescued > 0) {
                        LOGGER.warn("Synchronously rescued {} entities from timed-out async batch", rescued);
                    }
                }

                break;
            }

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

        for (ForkJoinTask<?> task : worldSpawnTasks.values()) {
            if (task != null && !task.isDone()) task.quietlyJoin();
        }
        worldSpawnTasks.clear();

        ArrayList<CompletableFuture<?>> remaining = new ArrayList<>();
        CompletableFuture<?> f;
        while ((f = externalTaskQueue.poll()) != null) {
            remaining.add(f);
        }
        if (!remaining.isEmpty()) {
            CompletableFuture.allOf(remaining.toArray(CompletableFuture[]::new)).join();
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
        spawnCollect.clear();
        spawnSubmit.clear();
        pendingCount = 0;
        despawnCount = 0;
    }
}
