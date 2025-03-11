package com.axalotl.async;

import com.axalotl.async.config.AsyncConfig;
import com.axalotl.async.parallelised.ConcurrentCollections;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.entity.*;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.vehicle.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.SpawnDensityCapper;
import net.minecraft.world.SpawnHelper;
import net.minecraft.world.chunk.WorldChunk;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class ParallelProcessor {
    private static final Logger LOGGER = LogManager.getLogger(ParallelProcessor.class);

    @Getter
    @Setter
    private static MinecraftServer server;

    public static final AtomicInteger currentEntities = new AtomicInteger();
    private static final AtomicInteger threadPoolID = new AtomicInteger();
    private static ExecutorService tickPool;
    private static final Queue<CompletableFuture<?>> taskQueue = new ConcurrentLinkedQueue<>();
    private static final Set<UUID> blacklistedEntity = ConcurrentHashMap.newKeySet();
    private static final Map<String, Set<Thread>> mcThreadTracker = ConcurrentCollections.newHashMap();
    private static final Set<Class<?>> specialEntities = Set.of(
            FallingBlockEntity.class
    );

    public static void setupThreadPool(int parallelism) {
        ForkJoinPool.ForkJoinWorkerThreadFactory threadFactory = pool -> {
            ForkJoinWorkerThread worker = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
            worker.setName("Async-Tick-Pool-Thread-" + threadPoolID.getAndIncrement());
            registerThread("Async-Tick", worker);
            worker.setDaemon(true);
            worker.setPriority(Thread.NORM_PRIORITY);
            worker.setContextClassLoader(Async.class.getClassLoader());
            return worker;
        };

        tickPool = new ForkJoinPool(parallelism, threadFactory, (t, e) ->
                LOGGER.error("Uncaught exception in thread {}: {}", t.getName(), e), true);
        LOGGER.info("Initialized Pool with {} threads", parallelism);
    }

    public static void registerThread(String poolName, Thread thread) {
        mcThreadTracker.computeIfAbsent(poolName, key -> ConcurrentHashMap.newKeySet()).add(thread);
    }

    private static boolean isThreadInPool(Thread thread) {
        return mcThreadTracker.getOrDefault("Async-Tick", Set.of()).contains(thread);
    }

    public static boolean isServerExecutionThread() {
        return isThreadInPool(Thread.currentThread());
    }

    public static void callEntityTick(Consumer<Entity> tickConsumer, Entity entity) {
        if (shouldTickSynchronously(entity)) {
            tickSynchronously(tickConsumer, entity);
        } else {
            if (!tickPool.isShutdown() && !tickPool.isTerminated()) {
                CompletableFuture<Void> future = CompletableFuture.runAsync(() ->
                        performAsyncEntityTick(tickConsumer, entity), tickPool
                ).exceptionally(e -> {
                    logEntityError("Error in async tick, switching to synchronous", entity, e);
                    tickSynchronously(tickConsumer, entity);
                    blacklistedEntity.add(entity.getUuid());
                    return null;
                });
                taskQueue.add(future);
            } else {
                logEntityError("Rejected task due to ExecutorService shutdown", entity, null);
                tickSynchronously(tickConsumer, entity);
            }
        }
    }

    public static boolean shouldTickSynchronously(Entity entity) {
        return AsyncConfig.disabled ||
                entity instanceof ProjectileEntity ||
                entity instanceof AbstractMinecartEntity ||
                entity instanceof ServerPlayerEntity ||
                specialEntities.contains(entity.getClass()) ||
                blacklistedEntity.contains(entity.getUuid()) ||
                AsyncConfig.synchronizedEntities.contains(EntityType.getId(entity.getType())) ||
                isPortalTickRequired(entity) ||
                entity.hasPlayerRider();
    }

    private static boolean isPortalTickRequired(Entity entity) {
        return entity.portalManager != null && entity.portalManager.isInPortal();
    }

    private static void tickSynchronously(Consumer<Entity> tickConsumer, Entity entity) {
        try {
            tickConsumer.accept(entity);
        } catch (Exception e) {
            logEntityError("Error during synchronous tick", entity, e);
        }
    }

    private static void performAsyncEntityTick(Consumer<Entity> tickConsumer, Entity entity) {
        currentEntities.incrementAndGet();
        try {
            tickConsumer.accept(entity);
        } finally {
            currentEntities.decrementAndGet();
        }
    }

    public static void asyncSpawn(ServerWorld world, WorldChunk worldChunk, SpawnHelper.Info info, List<SpawnGroup> spawnableGroups) {
        if (AsyncConfig.enableAsyncSpawn) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() ->
                    SpawnHelper.spawn(world, worldChunk, info, spawnableGroups), tickPool
            ).exceptionally(e -> {
                LOGGER.error("Error in async spawn tick, switching to synchronous", e);
                SpawnHelper.spawn(world, worldChunk, info, spawnableGroups);
                return null;
            });
            taskQueue.add(future);
        } else {
            SpawnHelper.spawn(world, worldChunk, info, spawnableGroups);
        }
    }

    public static SpawnHelper.Info asyncSpawnSetup(int spawningChunkCount, Iterable<Entity> entities, SpawnHelper.ChunkSource chunkSource, SpawnDensityCapper densityCapper) {
        if (AsyncConfig.enableAsyncSpawn) {
            CompletableFuture<SpawnHelper.Info> future = CompletableFuture.supplyAsync(() ->
                    SpawnHelper.setupSpawn(spawningChunkCount, entities, chunkSource, densityCapper), tickPool
            ).exceptionally(e -> {
                LOGGER.error("Error in async setup spawn tick, switching to synchronous", e);
                return SpawnHelper.setupSpawn(spawningChunkCount, entities, chunkSource, densityCapper);
            });
            return future.join();
        } else {
            return SpawnHelper.setupSpawn(spawningChunkCount, entities, chunkSource, densityCapper);
        }
    }

    public static void postEntityTick() {
        if (!AsyncConfig.disabled) {
            try {
                List<CompletableFuture<?>> futuresList = new ArrayList<>(taskQueue);
                taskQueue.clear();

                if (futuresList.isEmpty()) {
                    return;
                }

                CompletableFuture<?> allTasks = CompletableFuture.allOf(
                        futuresList.toArray(new CompletableFuture[0])
                );

                allTasks.orTimeout(120, TimeUnit.SECONDS).exceptionally(ex -> {
                    LOGGER.error("Timeout during entity tick processing", ex);
                    server.shutdown();
                    return null;
                });

                server.getWorlds().forEach(world -> world.getChunkManager().mainThreadExecutor.runTasks(allTasks::isDone));
            } catch (CompletionException e) {
                LOGGER.error("Critical error during entity tick processing", e);
                server.shutdown();
            }
        }
    }

    public static void stop() {
        if (tickPool != null && !tickPool.isShutdown()) {
            tickPool.shutdown();
            try {
                if (!tickPool.awaitTermination(10, TimeUnit.SECONDS)) {
                    tickPool.shutdownNow();
                }
            } catch (InterruptedException e) {
                tickPool.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    private static void logEntityError(String message, Entity entity, Throwable e) {
        LOGGER.error("{} Entity Type: {}, UUID: {}", message, entity.getType().getName(), entity.getUuid(), e);
    }
}