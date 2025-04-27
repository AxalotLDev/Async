package com.axalotl.async;

import com.axalotl.async.config.AsyncConfig;
import com.google.common.collect.Streams;
import com.mojang.logging.LogUtils;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.Bootstrap;
import net.minecraft.entity.*;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.vehicle.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.MinecraftDedicatedServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Util;
import net.minecraft.util.crash.CrashReport;
import net.minecraft.util.crash.CrashReportSection;
import net.minecraft.util.crash.ReportType;
import net.minecraft.world.GameRules;
import net.minecraft.world.SpawnHelper;
import net.minecraft.world.chunk.WorldChunk;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static net.minecraft.server.dedicated.DedicatedServerWatchdog.createCrashReport;

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
    private static final Map<UUID, Integer> portalTickSyncMap = new ConcurrentHashMap<>();
    private static final Map<String, Set<Thread>> mcThreadTracker = new ConcurrentHashMap<>();
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
        UUID entityId = entity.getUuid();
        boolean requiresSyncTick = AsyncConfig.disabled ||
                entity instanceof ProjectileEntity ||
                entity instanceof AbstractMinecartEntity ||
                entity instanceof ServerPlayerEntity ||
                specialEntities.contains(entity.getClass()) ||
                blacklistedEntity.contains(entityId) ||
                AsyncConfig.synchronizedEntities.contains(EntityType.getId(entity.getType())) ||
                entity.hasPlayerRider();
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

    public static void postEntityTick() {
        if (!AsyncConfig.disabled) {
            try {
                List<CompletableFuture<?>> futuresList = new ArrayList<>();
                CompletableFuture<?> future;
                while ((future = taskQueue.poll()) != null) {
                    futuresList.add(future);
                }

                CompletableFuture<?> allTasks = CompletableFuture.allOf(
                        futuresList.toArray(new CompletableFuture[0])
                );

                allTasks.orTimeout(((MinecraftDedicatedServer) server).getMaxTickTime(), TimeUnit.MILLISECONDS).exceptionally(ex -> {
                    List<CompletableFuture<?>> incompleteFutures = futuresList.stream()
                            .filter(doneFuture -> !doneFuture.isDone())
                            .toList();

                    if (incompleteFutures.isEmpty()) {
                        LOGGER.error("Other exception when trying to tick entities. Clearing all of it...", ex);
                        allTasks.cancel(true);
                        return null;
                    }

                    for (CompletableFuture<?> incompleteFuture : incompleteFutures) {
                        incompleteFuture.completeExceptionally(new RuntimeException("Future timed out and was abandoned."));
                    }

                    LOGGER.error("Timeout during entity tick processing", ex);
                    return null;
                });

                server.getWorlds().forEach(world -> {
                    world.getChunkManager().executeQueuedTasks();
                    world.getChunkManager().mainThreadExecutor.runTasks(allTasks::isDone);
                });
            } catch (CompletionException e) {
                crash("Critical error during entity tick processing: ", e);
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

    public static void crash(String message, Throwable throwable) {
        String errorMessage = message + throwable.getMessage();
        LOGGER.error(errorMessage, LogUtils.FATAL_MARKER);
        CrashReport crashReport = createCrashReport("Watching Server", server.getThread().threadId());
        server.addSystemDetails(crashReport.getSystemDetailsSection());
        CrashReportSection crashReportSection = crashReport.addElement("Performance stats");
        crashReportSection.add(
                "Random tick rate", () -> server.getSaveProperties().getGameRules().get(GameRules.RANDOM_TICK_SPEED).toString()
        );

        CrashReportSection threadDumpSection = crashReport.addElement("Async thread dump");
        threadDumpSection.add("All Threads", () -> {
            StringBuilder sb = new StringBuilder();
            Map<Thread, StackTraceElement[]> allThreads = Thread.getAllStackTraces();
            for (Map.Entry<Thread, StackTraceElement[]> entry : allThreads.entrySet()) {
                Thread t = entry.getKey();
                sb.append(String.format("\"%s\" [%s]%n", t.getName(), t.getState()));
                for (StackTraceElement ste : entry.getValue()) {
                    sb.append("\tat ").append(ste).append("\n");
                }
                sb.append("\n");
            }
            return sb.toString();
        });

        crashReportSection.add(
                "Level stats",
                () -> Streams.stream(server.getWorlds())
                        .map(world -> world.getRegistryKey().getValue() + ": " + world.getDebugString())
                        .collect(Collectors.joining(",\n"))
        );
        Bootstrap.println("Crash report:\n" + crashReport.asString(ReportType.MINECRAFT_CRASH_REPORT));
        Path path = server.getRunDirectory().resolve("crash-reports").resolve("crash-" + Util.getFormattedCurrentTime() + "-server.txt");
        if (crashReport.writeToFile(path, ReportType.MINECRAFT_CRASH_REPORT)) {
            LOGGER.error("This crash report has been saved to: {}", path.toAbsolutePath());
        } else {
            LOGGER.error("We were unable to save this crash report to disk.");
        }

        shutdown();
    }

    private static void shutdown() {
        try {
            Timer timer = new Timer();
            timer.schedule(new TimerTask() {
                public void run() {
                    Runtime.getRuntime().halt(1);
                }
            }, 10000L);
            System.exit(1);
        } catch (Throwable var2) {
            Runtime.getRuntime().halt(1);
        }
    }

    private static void logEntityError(String message, Entity entity, Throwable e) {
        LOGGER.error("{} Entity Type: {}, UUID: {}", message, entity.getType().getName(), entity.getUuid(), e);
    }
}