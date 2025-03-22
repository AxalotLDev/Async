package com.axalotl.async;

import com.axalotl.async.config.AsyncConfig;
import com.axalotl.async.parallelised.ConcurrentCollections;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.chunk.LevelChunk;
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
    private static final Queue<CompletableFuture<Void>> taskQueue = new ConcurrentLinkedQueue<>();
    private static final Set<UUID> blacklistedEntity = ConcurrentHashMap.newKeySet();
    private static final ConcurrentHashMap<UUID, Integer> portalTickSyncMap = new ConcurrentHashMap<>();
    private static final Map<String, Set<Thread>> mcThreadTracker = ConcurrentCollections.newHashMap();
    public static final Set<Class<?>> specialEntities = Set.of(
            FallingBlockEntity.class,
            Player.class,
            ServerPlayer.class
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
                    blacklistedEntity.add(entity.getUUID());
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
        UUID entityId = entity.getUUID();
        boolean requiresSyncTick = AsyncConfig.disabled ||
                entity instanceof Projectile ||
                entity instanceof AbstractMinecart ||
                entity instanceof ServerPlayer ||
                specialEntities.contains(entity.getClass()) ||
                blacklistedEntity.contains(entityId) ||
                AsyncConfig.synchronizedEntities.contains(EntityType.getKey(entity.getType())) ||
                entity.hasExactlyOnePlayerPassenger();
        if (requiresSyncTick) {
            return true;
        }
        return isPortalTickRequired(entity);
    }

    private static boolean isPortalTickRequired(Entity entity) {
        if (entity.isInsidePortal) {
            return true;
        }
        return entity instanceof Projectile;
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
        acceptWithTimeout(tickConsumer, entity, 1, TimeUnit.SECONDS);
    }

    public static void acceptWithTimeout(Consumer<Entity> consumer, Entity argument, long timeout, TimeUnit timeUnit) {
        Future<?> future = tickPool.submit(() -> consumer.accept(argument));
        try {
            future.get(timeout, timeUnit);
        } catch (TimeoutException | ExecutionException | InterruptedException e) {
            currentEntities.decrementAndGet();
            System.out.println("Async mod exception: falling back to synchronous tick for " + argument.toString());
            tickSynchronously(consumer, argument);
            future.cancel(true); // Interrupt the task if it times out
        }
    }

    public static void asyncSpawn(ServerLevel world, LevelChunk worldChunk, NaturalSpawner.SpawnState info, boolean spawnFriendlies, boolean spawnMonsters, boolean forcedDespawn) {
        if (AsyncConfig.enableAsyncSpawn) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() ->
                    NaturalSpawner.spawnForChunk(world, worldChunk, info, spawnFriendlies, spawnMonsters, forcedDespawn), tickPool
            ).exceptionally(e -> {
                LOGGER.error("Error in async spawn tick, switching to synchronous", e);
                NaturalSpawner.spawnForChunk(world, worldChunk, info, spawnFriendlies, spawnMonsters, forcedDespawn);
                return null;
            });
            taskQueue.add(future);
        } else {
            NaturalSpawner.spawnForChunk(world, worldChunk, info, spawnFriendlies, spawnMonsters, forcedDespawn);
        }
    }

    public static void postEntityTick() {
        if (AsyncConfig.disabled) {
            return;
        }

        List<CompletableFuture<Void>> futuresList = new ArrayList<>(taskQueue);
        if (futuresList.isEmpty()) {
            return;
        }
        taskQueue.clear();

        try {
            CompletableFuture<Void> allTasks = CompletableFuture.allOf(
                    futuresList.toArray(new CompletableFuture[0])
            );
            allTasks.orTimeout(3, TimeUnit.SECONDS).exceptionally(ex -> {
                List<CompletableFuture<Void>> incompleteFutures = futuresList.stream()
                        .filter(future -> !future.isDone())
                        .toList();

                if (incompleteFutures.isEmpty()) {
                    LOGGER.error("Other exception when trying to tick entities. Clearing all of it...", ex);
                    allTasks.cancel(true);
                    return null;
                }

                for (CompletableFuture<Void> incompleteFuture : incompleteFutures) {
                    incompleteFuture.completeExceptionally(new RuntimeException("Future timed out and was abandoned."));
                }

                LOGGER.error("Timeout during entity tick processing", ex);
                return null;
            });

            server.getAllLevels().forEach(world -> {
                world.getChunkSource().pollTask();
                world.getChunkSource().mainThreadProcessor.managedBlock(allTasks::isDone);
            });

        } catch (CompletionException e) {
            LOGGER.error("Critical error during entity tick processing", e);

            for (CompletableFuture<Void> future : futuresList) {
                future.completeExceptionally(new RuntimeException("Async processing failed critically, executing synchronously."));
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
        LOGGER.error("{} Entity Type: {}, UUID: {}", message, entity.getType().getDescription(), entity.getUUID(), e);
    }
}