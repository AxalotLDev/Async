package com.axalotl.async.common;

import com.axalotl.async.common.config.AsyncConfig;
import com.axalotl.async.common.parallelised.utils.VanishCompat;
import io.netty.util.concurrent.FastThreadLocalThread;
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

public class ParallelProcessor {

    public static final Logger LOGGER = LogManager.getLogger(ParallelProcessor.class);

    @Getter
    @Setter
    private static MinecraftServer server;

    public static final AtomicInteger currentEntities = new AtomicInteger();
    private static final AtomicInteger threadPoolID = new AtomicInteger();
    public static ExecutorService tickPool;
    private static final Object ENTITY_ADD_LOCK = new Object();
    private static final Set<UUID> blacklistedEntity = ConcurrentHashMap.newKeySet();
    private static final Map<String, Set<WeakReference<Thread>>> mcThreadTracker = new ConcurrentHashMap<>();

    public static final Set<Class<?>> BLOCKED_ENTITIES = Set.of(
            FallingBlockEntity.class,
            Shulker.class,
            AbstractBoat.class
    );

    private static volatile boolean isShuttingDown = false;

    // --- Spawn system (from furry, adapted for ExecutorService, reworked per-world grouping) ---

    private static ArrayList<SpawnEntry> spawnSubmit = new ArrayList<>();
    private static ArrayList<SpawnEntry> spawnCollect = new ArrayList<>();
    private static final IdentityHashMap<ServerLevel, CompletableFuture<?>> worldSpawnTasks = new IdentityHashMap<>();

    record SpawnEntry(ServerLevel level, LevelChunk chunk, NaturalSpawner.SpawnState spawnState,
                      List<MobCategory> categories) {
    }

    // --- Thread pool (from non-furry) ---

    public static void setupThreadPool(int parallelism, Class<?> asyncClass) {
        isShuttingDown = false;
        ThreadFactory threadFactory = runnable -> {
            FastThreadLocalThread thread = new FastThreadLocalThread(runnable,
                    "Async-Tick-Pool-Thread-" + threadPoolID.getAndIncrement()) {
                @Override
                public void run() {
                    super.run();
                }
            };
            registerThread("Async-Tick", thread);
            thread.setDaemon(false);
            thread.setPriority(Thread.NORM_PRIORITY - 1);
            thread.setContextClassLoader(asyncClass.getClassLoader());
            thread.setUncaughtExceptionHandler((t, e) ->
                    LOGGER.error("Uncaught exception in thread {}", t.getName(), e));
            return thread;
        };
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                parallelism,
                parallelism,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(),
                threadFactory
        );
        executor.allowCoreThreadTimeOut(false);
        executor.prestartAllCoreThreads();
        tickPool = executor;
        LOGGER.info("Initialized Pool with {} threads", parallelism);
        VanishCompat.apply();
    }

    // --- Thread tracking (from non-furry) ---

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

    public static int getPoolSize() {
        return ((ThreadPoolExecutor) tickPool).getCorePoolSize();
    }

    public static Object getEntityAddLock() {
        return ENTITY_ADD_LOCK;
    }

    // --- Batch entity ticking (returns future, caller decides when to wait) ---

    public static CompletableFuture<Void> callEntityTickBatch(ServerLevel world, List<Entity> entities) {
        if (entities.isEmpty()) return CompletableFuture.completedFuture(null);
        if (AsyncConfig.disabled) {
            entities.forEach(world::tickNonPassenger);
            return CompletableFuture.completedFuture(null);
        }

        // Tick sync entities on main thread immediately
        for (Entity e : entities) {
            if (shouldTickSynchronously(e)) {
                world.tickNonPassenger(e);
            }
        }

        // Submit async entities to pool
        int poolSize = getPoolSize();
        int chunkSize = Math.max(1, entities.size() / poolSize);

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (int i = 0; i < entities.size(); i += chunkSize) {
            List<Entity> chunk = entities.subList(i, Math.min(i + chunkSize, entities.size()));
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                for (Entity entity : chunk) {
                    if (shouldTickSynchronously(entity)) continue;
                    currentEntities.incrementAndGet();
                    world.tickNonPassenger(entity);
                    currentEntities.decrementAndGet();
                }
            }, tickPool).exceptionally(throwable -> {
                LOGGER.error("Error in async entity tick batch", throwable);
                return null;
            });
            futures.add(future);
        }

        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
    }

    // --- Batch despawn (returns future, caller decides when to wait) ---

    public static CompletableFuture<Void> callDespawnBatch(List<Entity> entities) {
        if (entities.isEmpty()) return CompletableFuture.completedFuture(null);
        if (AsyncConfig.disabled) {
            entities.forEach(Entity::checkDespawn);
            return CompletableFuture.completedFuture(null);
        }

        int poolSize = getPoolSize();
        int chunkSize = Math.max(1, entities.size() / poolSize);

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (int i = 0; i < entities.size(); i += chunkSize) {
            List<Entity> chunk = entities.subList(i, Math.min(i + chunkSize, entities.size()));
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                for (Entity e : chunk) {
                    e.checkDespawn();
                }
            }, tickPool).exceptionally(throwable -> {
                LOGGER.error("Error in async despawn batch", throwable);
                return null;
            });
            futures.add(future);
        }

        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
    }

    // --- Wait helper (pumps chunk tasks while waiting) ---

    public static void waitWithPumping(CompletableFuture<?>... tasks) {
        boolean allDone;
        do {
            allDone = true;
            for (CompletableFuture<?> task : tasks) {
                if (!task.isDone()) {
                    allDone = false;
                    break;
                }
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
    }

    // --- Spawn system (from furry, reworked: per-world grouping) ---

    public static void asyncSpawnForChunk(ServerLevel level, LevelChunk chunk,
                                          NaturalSpawner.SpawnState spawnState, List<MobCategory> categories) {
        if (!chunk.loaded) return;

        if (isShuttingDown || tickPool == null || tickPool.isShutdown()
                || AsyncConfig.disabled || !AsyncConfig.enableAsyncSpawn) {
            NaturalSpawner.spawnForChunk(level, chunk, spawnState, categories);
            return;
        }

        if (categories.isEmpty()) return;

        spawnCollect.add(new SpawnEntry(level, chunk, spawnState, List.copyOf(categories)));
    }

    public static void submitSpawnCycle() {
        if (spawnCollect.isEmpty()) return;

        IdentityHashMap<ServerLevel, ArrayList<SpawnEntry>> byWorld = new IdentityHashMap<>();
        for (int i = 0, n = spawnCollect.size(); i < n; i++) {
            SpawnEntry s = spawnCollect.get(i);
            byWorld.computeIfAbsent(s.level(), k -> new ArrayList<>()).add(s);
        }

        for (Map.Entry<ServerLevel, ArrayList<SpawnEntry>> entry : byWorld.entrySet()) {
            ServerLevel world = entry.getKey();
            ArrayList<SpawnEntry> entries = entry.getValue();

            CompletableFuture<?> prev = worldSpawnTasks.get(world);
            if (prev != null && !prev.isDone()) {
                prev.join();
            }

            worldSpawnTasks.put(world, CompletableFuture.runAsync(() -> {
                for (int i = 0, n = entries.size(); i < n; i++) {
                    SpawnEntry s = entries.get(i);
                    NaturalSpawner.spawnForChunk(s.level(), s.chunk(), s.spawnState(), s.categories());
                }
            }, tickPool));
        }

        ArrayList<SpawnEntry> tmp = spawnCollect;
        spawnCollect = spawnSubmit;
        spawnCollect.clear();
        spawnSubmit = tmp;
    }

    // --- Entity sync check (furry: simple portalProcess != null) ---

    public static boolean shouldTickSynchronously(Entity entity) {
        if (isShuttingDown) return true;
        if (entity.level().isClientSide()) return true;
        if (AsyncConfig.disabled) return true;
        if (entity instanceof ServerPlayer) return true;
        if (entity instanceof Projectile) return true;
        if (entity instanceof AbstractMinecart) return true;
        if (BLOCKED_ENTITIES.contains(entity.getClass())) return true;
        if (entity.portalProcess != null) return true;
        if (blacklistedEntity.contains(entity.getUUID())) return true;
        return AsyncConfig.isEntitySynchronized(EntityType.getKey(entity.getType()));
    }

    // --- Shutdown (merged) ---

    public static void stop() {
        isShuttingDown = true;

        for (CompletableFuture<?> task : worldSpawnTasks.values()) {
            if (task != null && !task.isDone()) task.join();
        }
        worldSpawnTasks.clear();

        if (tickPool != null) {
            LOGGER.info("Waiting for Async tickPool to shutdown...");
            tickPool.shutdown();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
            while (!tickPool.isTerminated() && System.nanoTime() < deadline) {
                Thread.onSpinWait();
            }
            if (!tickPool.isTerminated()) {
                LOGGER.warn("Pool did not terminate in time, forcing shutdown");
                tickPool.shutdownNow();
            }
        }

        AsyncConfig.clearCaches();
        blacklistedEntity.clear();
        spawnCollect.clear();
        spawnSubmit.clear();
    }
}