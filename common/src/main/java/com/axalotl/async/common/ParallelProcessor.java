package com.axalotl.async.common;

import com.axalotl.async.common.config.AsyncConfig;
import com.google.common.collect.Streams;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportType;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.level.*;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.chunk.LevelChunk;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Unique;

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
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

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
    public static final Set<Class<?>> specialEntities = Set.of(
            FallingBlockEntity.class,
            Player.class,
            ServerPlayer.class
    );

    public static void setupThreadPool(int parallelism, Class<?> asyncClass) {
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

    public static void callEntityTick(ServerLevel world, Entity entity) {
        if (shouldTickSynchronously(entity)) {
            tickSynchronously(world, entity);
        } else {
            if (!tickPool.isShutdown() && !tickPool.isTerminated()) {
                CompletableFuture<Void> future = CompletableFuture.runAsync(() ->
                        performAsyncEntityTick(world, entity), tickPool
                ).exceptionally(e -> {
                    logEntityError("Error in async tick, switching to synchronous", entity, e);
                    tickSynchronously(world, entity);
                    blacklistedEntity.add(entity.getUUID());
                    return null;
                });
                taskQueue.add(future);
            } else {
                logEntityError("Rejected task due to ExecutorService shutdown", entity, null);
                tickSynchronously(world, entity);
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
                AsyncConfig.synchronizedEntities.contains(EntityType.getKey(entity.getType()));
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

    public static void asyncSpawn(ServerLevel world, LevelChunk chunk, NaturalSpawner.SpawnState spawnState, boolean spawnAnimals,
                                  boolean spawnMonsters, boolean rareSpawn) {
        if (AsyncConfig.enableAsyncSpawn) {
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

    public static void asyncDespawn(Entity entity) {
        if (AsyncConfig.enableAsyncSpawn) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(entity::checkDespawn, tickPool
            ).exceptionally(e -> {
                LOGGER.error("Error in async spawn tick, switching to synchronous", e);
                entity.checkDespawn();
                return null;
            });
            taskQueue.add(future);
        } else {
            entity.checkDespawn();
        }
    }

    public static NaturalSpawner.SpawnState asyncCreateState(int spawnableChunkCount, Iterable<Entity> entities, NaturalSpawner.ChunkGetter chunkGetter, LocalMobCapCalculator calculator) {
        if (AsyncConfig.enableAsyncSpawn) {
            return CompletableFuture.supplyAsync(() ->
                    async$createState(spawnableChunkCount, entities, chunkGetter, calculator), tickPool
            ).exceptionally(e -> {
                LOGGER.error("Error in async spawn tick, switching to synchronous", e);
                return async$createState(spawnableChunkCount, entities, chunkGetter, calculator);
            }).join();
        } else {
            return CompletableFuture.completedFuture(
                    async$createState(spawnableChunkCount, entities, chunkGetter, calculator)
            ).join();
        }
    }

    @Unique
    private static NaturalSpawner.SpawnState async$createState(
            int spawnableChunkCount,
            Iterable<Entity> entities,
            NaturalSpawner.ChunkGetter chunkGetter,
            LocalMobCapCalculator calculator
    ) {
        PotentialCalculator potentialcalculator = new PotentialCalculator();
        Object2IntOpenHashMap<MobCategory> mobCountMap = new Object2IntOpenHashMap<>();
        Map<Long, Biome> biomeCache = new Object2ObjectOpenHashMap<>();
        for (Entity entity : entities) {
            if (entity instanceof Mob mob && (mob.isPersistenceRequired() || mob.requiresCustomPersistence())) {
                continue;
            }
            MobCategory mobcategory = entity.getType().getCategory();
            if (mobcategory == MobCategory.MISC) {
                continue;
            }
            BlockPos pos = entity.blockPosition();
            long chunkPosLong = ChunkPos.asLong(pos);
            chunkGetter.query(chunkPosLong, chunk -> {Biome biome = biomeCache.computeIfAbsent(chunkPosLong, key -> NaturalSpawner.getRoughBiome(pos, chunk));

                MobSpawnSettings.MobSpawnCost spawnCost = biome.getMobSettings().getMobSpawnCost(entity.getType());
                if (spawnCost != null) {
                    potentialcalculator.addCharge(pos, spawnCost.charge());
                }
                if (entity instanceof Mob) {
                    calculator.addMob(chunk.getPos(), mobcategory);
                }
                mobCountMap.addTo(mobcategory, 1);
            });
        }
        return new NaturalSpawner.SpawnState(spawnableChunkCount, mobCountMap, potentialcalculator, calculator);
    }

    public static void postEntityTick() {
        if (!AsyncConfig.disabled) {
            List<CompletableFuture<?>> futuresList = new ArrayList<>();
            CompletableFuture<?> future;
            while ((future = taskQueue.poll()) != null) {
                futuresList.add(future);
            }

            CompletableFuture<?> allTasks = CompletableFuture.allOf(
                    futuresList.toArray(new CompletableFuture[0])
            );

            long maxTickTime;

            if (server instanceof DedicatedServer dedicatedServer) {
                maxTickTime = dedicatedServer.getMaxTickLength();
            } else {
                maxTickTime = 60000;
            }

            if (maxTickTime > 0) {
                allTasks
                        .orTimeout(maxTickTime, TimeUnit.MILLISECONDS)
                        .exceptionally(ex -> {
                            Throwable cause = ex instanceof java.util.concurrent.CompletionException
                                    ? ex.getCause() : ex;
                            if (cause instanceof TimeoutException) {
                                crash("Timeout during entity tick processing: ", cause);
                            } else {
                                LOGGER.error("Error during entity tick processing: ", cause);
                            }
                            return null;
                        });
            } else {
                allTasks.exceptionally(ex -> {
                    Throwable cause = ex instanceof java.util.concurrent.CompletionException
                            ? ex.getCause() : ex;
                    LOGGER.error("Error during entity tick processing: ", cause);
                    return null;
                });
            }

            server.getAllLevels().forEach(world -> {
                world.getChunkSource().pollTask();
                world.getChunkSource().mainThreadProcessor.managedBlock(allTasks::isDone);
            });
        }
    }

    public static void stop() {
        if (tickPool != null && !tickPool.isShutdown()) {
            tickPool.shutdown();
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
            if (threadInfo.getThreadId() == server.getRunningThread().threadId()) {
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
        LOGGER.error("{} Entity Type: {}, UUID: {}", message, entity.getType().toString(), entity.getUUID(), e);
    }
}