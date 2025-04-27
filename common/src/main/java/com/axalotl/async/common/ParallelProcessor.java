package com.axalotl.async.common;

import com.axalotl.async.common.config.AsyncConfig;
import com.google.common.collect.Streams;
import com.mojang.logging.LogUtils;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportType;
import net.minecraft.Util;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.chunk.LevelChunk;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class ParallelProcessor {
    public static final AtomicInteger currentEntities = new AtomicInteger();
    public static final Set<Class<?>> specialEntities = Set.of(
            FallingBlockEntity.class,
            Player.class,
            ServerPlayer.class
    );
    private static final Logger LOGGER = LogManager.getLogger();
    private static final AtomicInteger threadPoolID = new AtomicInteger();
    private static final Queue<CompletableFuture<?>> taskQueue = new ConcurrentLinkedQueue<>();
    private static final Set<UUID> blacklistedEntity = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, Integer> portalTickSyncMap = new ConcurrentHashMap<>();
    private static final Map<String, Set<Thread>> mcThreadTracker = new ConcurrentHashMap<>();
    public static MinecraftServer server;
    private static ExecutorService tickPool;

    public static MinecraftServer getServer() {
        return server;
    }

    public static void setServer(MinecraftServer server) {
        ParallelProcessor.server = server;
    }

    public static void setupThreadPool(int parallelism, Class asyncClass) {
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
        boolean requiresSyncTick = AsyncConfig.disabled.getValue() ||
                entity instanceof Projectile ||
                entity instanceof AbstractMinecart ||
                entity instanceof ServerPlayer ||
                specialEntities.contains(entity.getClass()) ||
                blacklistedEntity.contains(entityId) ||
                AsyncConfig.synchronizedEntities.getValue().contains(EntityType.getKey(entity.getType())) ||
                entity.hasExactlyOnePlayerPassenger();
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

    public static void asyncSpawn(ServerLevel world, LevelChunk chunk, NaturalSpawner.SpawnState spawnState, boolean spawnAnimals,
                                  boolean spawnMonsters, boolean rareSpawn) {
        if (AsyncConfig.enableAsyncSpawn.getValue()) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() ->
                    NaturalSpawner.spawnForChunk(world, chunk, spawnState, spawnAnimals, spawnMonsters, rareSpawn), tickPool
            ).exceptionally(e -> {
                LOGGER.error("Error in async spawn tick, switching to synchronous", e);
                NaturalSpawner.spawnForChunk(world, chunk, spawnState, spawnAnimals, spawnMonsters, rareSpawn);
                return null;
            });
            taskQueue.add(future);
        } else {
            NaturalSpawner.spawnForChunk(world, chunk, spawnState, spawnAnimals, spawnMonsters, rareSpawn);
        }
    }

    public static void postEntityTick() {

        if (AsyncConfig.disabled.getValue()) {
            return;
        }

        List<CompletableFuture<?>> futuresList = new ArrayList<>();
        try {
            CompletableFuture<?> future;
            while ((future = taskQueue.poll()) != null) {
                futuresList.add(future);
            }

            CompletableFuture<?> allTasks = CompletableFuture.allOf(
                    futuresList.toArray(new CompletableFuture[0])
            );

            if (AsyncConfig.recoverFromErrors.getValue()) {
                allTasks.orTimeout(3, TimeUnit.SECONDS).exceptionally(ex -> {
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
            }
            else {
                allTasks.orTimeout(server.getNextTickTime(), TimeUnit.MILLISECONDS).exceptionally(ex -> {
                    crash("Timeout during entity tick processing: ", ex);
                    return null;
                });
            }

            server.getAllLevels().forEach(world -> {
                world.getChunkSource().pollTask();
                world.getChunkSource().mainThreadProcessor.managedBlock(allTasks::isDone);
            });
        } catch (CompletionException e) {
            if (AsyncConfig.recoverFromErrors.getValue()) {
                LOGGER.error("Critical error during entity tick processing", e);

                for (CompletableFuture<?> future : futuresList) {
                    future.completeExceptionally(new RuntimeException("Async processing failed critically."));
                }
            } else {
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
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        ThreadInfo[] threadInfos = threadMXBean.dumpAllThreads(true, true);
        StringBuilder stringBuilder = new StringBuilder();
        Error error = new Error("Watchdog");

        for (ThreadInfo threadInfo : threadInfos) {
            if (threadInfo.getThreadId() == server.getRunningThread().getId()) {
                error.setStackTrace(threadInfo.getStackTrace());
            }

            stringBuilder.append(threadInfo);
            stringBuilder.append("\n");
        }

        CrashReport crashReport = new CrashReport("Watching Server", error);
        server.fillSystemReport(crashReport.getSystemReport());
        CrashReportCategory crashReportSection = crashReport.addCategory("Thread Dump");
        crashReportSection.setDetail("Threads", stringBuilder);

        CrashReportCategory threadDumpSection = crashReport.addCategory("Async thread dump");
        threadDumpSection.setDetail("All Threads", () -> {
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

        CrashReportCategory crashReportSection2 = crashReport.addCategory("Performance stats");
        crashReportSection2.setDetail(
                "Random tick rate", () -> server.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).toString()
        );
        crashReportSection2.setDetail(
                "Level stats",
                () -> Streams.stream(server.getAllLevels())
                        .map(world -> world.dimension() + ": " + world.getWatchdogStats())
                        .collect(Collectors.joining(",\n"))
        );
        System.out.println("Crash report:\n" + crashReport);
        Path path = server.getServerDirectory().resolve("crash-reports").resolve("crash-" + Util.getFilenameFormattedDateTime() + "-server.txt");
        if (crashReport.saveToFile(path, ReportType.CRASH)) {
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
        LOGGER.error("{} Entity Type: {}, UUID: {}", message, entity.getType().getCategory().getName(), entity.getUUID(), e);
    }
}