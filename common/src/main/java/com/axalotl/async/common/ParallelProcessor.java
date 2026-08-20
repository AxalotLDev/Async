package com.axalotl.async.common;

import com.axalotl.async.api.utils.AsyncCompatible;
import com.axalotl.async.common.compat.SableCompatibility;
import com.axalotl.async.common.config.AsyncConfig;
import com.axalotl.async.common.platform.PlatformUtils;
import com.axalotl.async.common.utils.SynchronizationReason;
import com.axalotl.async.common.utils.SynchronizationStats;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.ChunkPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;

import static com.axalotl.async.common.utils.TickStats.ASYNC_TICK_COUNT;
import static com.axalotl.async.common.utils.TickStats.ASYNC_TICK_TIME_NS;
import static com.axalotl.async.common.utils.TickStats.RECORDING_TICKS_LEFT;
import static com.axalotl.async.common.utils.TickStats.TICK_COUNT;
import static com.axalotl.async.common.utils.TickStats.TICK_TIME_NS;
import static com.axalotl.async.common.utils.TickStats.resetEntityTickStats;

public class ParallelProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(ParallelProcessor.class);
    private static final String ASYNC_TICK_POOL = "Async-Tick";
    private static final String SABLE_MOD_ID = "sable";
    private static final int SECTION_SHIFT = 2;
    private static final long POLL_INTERVAL_MICROSECONDS = 200L;
    private static final boolean SABLE_LOADED = PlatformUtils.isModLoaded(SABLE_MOD_ID);
    private static final AtomicBoolean SABLE_COMPATIBILITY_FAILURE_LOGGED = new AtomicBoolean();

    @Getter
    @Setter
    private static MinecraftServer server;

    public static ExecutorService executor;
    private static final AtomicInteger THREAD_POOL_ID = new AtomicInteger();
    private static volatile boolean isShuttingDown = false;

    private static final Queue<Runnable> FOREGROUND_TASKS = new ConcurrentLinkedQueue<>();
    private static final Queue<Runnable> BACKGROUND_TASKS = new ConcurrentLinkedQueue<>();

    public static final Executor BACKGROUND = ParallelProcessor::executeBackground;
    public static final Executor FOREGROUND = ParallelProcessor::executeForeground;

    private static final Set<UUID> BLACKLISTED_ENTITIES = ConcurrentHashMap.newKeySet();
    private static final Set<Class<?>> BLOCKED_ENTITIES = Set.of(
            FallingBlockEntity.class,
            Shulker.class,
            Boat.class
    );

    private static final Map<Class<?>, Boolean> ASYNC_API_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Set<WeakReference<Thread>>> MC_THREAD_TRACKER = new ConcurrentHashMap<>();

    private static final CostModel ENTITY_TICK_COST = new CostModel(25_000.0);
    private static final CostModel DESPAWN_COST = new CostModel(2_000.0);

    private static volatile boolean poolUnavailableLogged;

    /**
     * Queues work that must complete before background work.
     */
    public static void executeForeground(Runnable task) {
        FOREGROUND_TASKS.add(task);
        submitSlot();
    }

    /**
     * Queues work that may yield to tick-critical work.
     */
    public static void executeBackground(Runnable task) {
        BACKGROUND_TASKS.add(task);
        submitSlot();
    }

    private static void submitSlot() {
        ExecutorService threadPool = executor;
        if (threadPool != null && !threadPool.isShutdown()) {
            threadPool.execute(ParallelProcessor::runOnePrioritized);
            return;
        }
        if (!poolUnavailableLogged) {
            poolUnavailableLogged = true;
            LOGGER.error("Async pool unavailable (executor={}); running tasks inline on {}",
                    threadPool, Thread.currentThread().getName(),
                    new IllegalStateException("pool unavailable"));
        }
        runOnePrioritized();
    }

    private static void runOnePrioritized() {
        Runnable task = FOREGROUND_TASKS.poll();
        if (task == null) {
            task = BACKGROUND_TASKS.poll();
        }
        if (task == null) {
            return;
        }
        try {
            task.run();
        } catch (Throwable throwable) {
            LOGGER.error("Error in pool task", throwable);
        }
    }

    private static final class CostModel {
        private static final long TARGET_TASK_NANOSECONDS = 250_000L;
        private static final long MINIMUM_PARALLEL_NANOSECONDS = 2L * TARGET_TASK_NANOSECONDS;
        private static final double SMOOTHING_FACTOR = 0.25;
        private static final double MINIMUM_NANOSECONDS_PER_ITEM = 100.0;
        private static final double MAXIMUM_NANOSECONDS_PER_ITEM = 2_000_000.0;

        private final LongAdder batchNanoseconds = new LongAdder();
        private final LongAdder batchItems = new LongAdder();
        private volatile double nanosecondsPerItem;

        private CostModel(double seedNanosecondsPerItem) {
            this.nanosecondsPerItem = seedNanosecondsPerItem;
        }

        private Runnable wrap(int itemCount, Runnable task) {
            return () -> {
                long startTime = System.nanoTime();
                try {
                    task.run();
                } finally {
                    batchNanoseconds.add(System.nanoTime() - startTime);
                    batchItems.add(itemCount);
                }
            };
        }

        private synchronized void fold() {
            long itemCount = batchItems.sumThenReset();
            if (itemCount <= 0L) {
                return;
            }
            long elapsedNanoseconds = batchNanoseconds.sumThenReset();
            double sample = Math.clamp(
                    (double) elapsedNanoseconds / itemCount,
                    MINIMUM_NANOSECONDS_PER_ITEM,
                    MAXIMUM_NANOSECONDS_PER_ITEM
            );
            double currentEstimate = nanosecondsPerItem;
            nanosecondsPerItem = currentEstimate
                    + (sample - currentEstimate) * SMOOTHING_FACTOR;
        }

        private int chunkSize(int workItemCount) {
            fold();
            int costBasedSize = (int) Math.max(
                    1L,
                    (long) (TARGET_TASK_NANOSECONDS / nanosecondsPerItem)
            );
            int participantCount = Math.max(1, getPoolSize()) + 1;
            int fairShare = Math.max(
                    1,
                    (workItemCount + participantCount - 1) / participantCount
            );
            return Math.clamp(costBasedSize, 1, fairShare);
        }

        private boolean shouldRunSequentially(int workItemCount) {
            fold();
            return workItemCount * nanosecondsPerItem < MINIMUM_PARALLEL_NANOSECONDS;
        }
    }

    /**
     * Recreates the entity tick pool with the requested parallelism.
     */
    public static void setupThreadPool(int parallelism, Class<?> asyncClass) {
        isShuttingDown = false;

        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(
                    runnable,
                    "Async-Tick-Pool-Thread-" + THREAD_POOL_ID.getAndIncrement()
            );
            registerThread(ASYNC_TICK_POOL, thread);
            thread.setDaemon(true);
            thread.setPriority(Thread.NORM_PRIORITY - 1);
            thread.setContextClassLoader(asyncClass.getClassLoader());
            return thread;
        };

        ThreadPoolExecutor threadPool = new ThreadPoolExecutor(
                parallelism,
                parallelism,
                0L,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(),
                threadFactory
        );
        threadPool.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
        threadPool.allowCoreThreadTimeOut(false);
        threadPool.prestartAllCoreThreads();

        executor = threadPool;
        LOGGER.info("Initialized Pool with {} threads", parallelism);
    }

    /**
     * Records a thread as belonging to a named Minecraft worker pool.
     */
    public static void registerThread(String poolName, Thread thread) {
        MC_THREAD_TRACKER
                .computeIfAbsent(poolName, ignoredPoolName -> ConcurrentHashMap.newKeySet())
                .add(new WeakReference<>(thread));
    }

    private static boolean isThreadInPool(Thread thread) {
        Set<WeakReference<Thread>> threadReferences = MC_THREAD_TRACKER.getOrDefault(
                ASYNC_TICK_POOL,
                Set.of()
        );
        for (WeakReference<Thread> threadReference : threadReferences) {
            if (threadReference.get() == thread) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns whether the current thread belongs to the entity tick pool.
     */
    public static boolean isServerExecutionThread() {
        return isThreadInPool(Thread.currentThread());
    }

    /**
     * Returns the configured entity tick pool size.
     */
    public static int getPoolSize() {
        if (executor instanceof ThreadPoolExecutor threadPool) {
            return threadPool.getCorePoolSize();
        }
        return 0;
    }

    /**
     * Ticks a batch of entities and waits for every asynchronous tick to finish.
     */
    public static void callEntityTickBatch(ServerLevel world, List<Entity> entities) {
        callEntityTickBatch(world, entities, null);
    }

    /**
     * Ticks entities and performs despawn-only checks for entities outside the ticking range.
     */
    public static void callEntityTickBatch(
            ServerLevel world,
            List<Entity> entities,
            List<Entity> despawnOnlyEntities
    ) {
        boolean despawnChecksEnabled = despawnOnlyEntities != null;
        if (entities.isEmpty()
                && (!despawnChecksEnabled || despawnOnlyEntities.isEmpty())) {
            return;
        }

        if (AsyncConfig.disabled) {
            if (despawnChecksEnabled) {
                despawnOnlyEntities.forEach(ParallelProcessor::checkDespawn);
            }
            boolean recordSynchronizationStats = SynchronizationStats.isRecording();
            for (Entity entity : entities) {
                if (entity == null || entity.isRemoved()) {
                    continue;
                }
                if (recordSynchronizationStats) {
                    SynchronizationStats.record(entity, getSynchronizationReasons(entity));
                }
                tickOne(world, entity, false, despawnChecksEnabled);
            }
            return;
        }

        List<Entity> asynchronousEntities = new ArrayList<>();
        List<Entity> synchronousEntities = new ArrayList<>();
        boolean recordSynchronizationStats = SynchronizationStats.isRecording();
        for (Entity entity : entities) {
            if (entity == null || entity.isRemoved()) {
                continue;
            }

            Set<SynchronizationReason> synchronizationReasons = recordSynchronizationStats
                    ? getSynchronizationReasons(entity)
                    : Set.of();
            boolean tickSynchronously = recordSynchronizationStats
                    ? !synchronizationReasons.isEmpty()
                    : shouldTickSynchronously(entity);
            if (tickSynchronously) {
                synchronousEntities.add(entity);
                if (recordSynchronizationStats) {
                    SynchronizationStats.record(entity, synchronizationReasons);
                }
            } else {
                asynchronousEntities.add(entity);
            }
        }

        if (ENTITY_TICK_COST.shouldRunSequentially(asynchronousEntities.size())) {
            if (despawnChecksEnabled) {
                despawnOnlyEntities.forEach(ParallelProcessor::checkDespawn);
            }
            asynchronousEntities.forEach(
                    entity -> tickOne(world, entity, false, despawnChecksEnabled)
            );
            synchronousEntities.forEach(
                    entity -> tickOne(world, entity, false, despawnChecksEnabled)
            );
            return;
        }

        List<Runnable> work = buildSpatialWork(
                asynchronousEntities,
                entity -> tickOne(world, entity, true, despawnChecksEnabled)
        );
        if (despawnChecksEnabled && !despawnOnlyEntities.isEmpty()) {
            addSlices(
                    work,
                    despawnOnlyEntities,
                    DESPAWN_COST.chunkSize(despawnOnlyEntities.size()),
                    DESPAWN_COST,
                    ParallelProcessor::checkDespawn
            );
        }

        ParallelBatch batch = submitParallel(work);
        synchronousEntities.forEach(
                entity -> tickOne(world, entity, false, despawnChecksEnabled)
        );
        finishParallel(batch);
    }

    private static void tickOne(
            ServerLevel world,
            Entity entity,
            boolean asynchronous,
            boolean checkDespawn
    ) {
        if (entity.isRemoved()) {
            return;
        }
        if (checkDespawn) {
            checkDespawn(entity);
        }
        tickEntity(world, entity, asynchronous);
    }

    private static void checkDespawn(Entity entity) {
        if (entity.isRemoved()) {
            return;
        }
        try {
            entity.checkDespawn();
        } catch (Exception exception) {
            LOGGER.error(
                    "Error during despawn check. Entity: {}, UUID: {}",
                    entity.getType(),
                    entity.getUUID(),
                    exception
            );
        }
    }

    private static long sectionKey(Entity entity) {
        ChunkPos chunkPosition = entity.chunkPosition();
        return (((long) (chunkPosition.x >> SECTION_SHIFT)) << 32)
                | ((chunkPosition.z >> SECTION_SHIFT) & 0xFFFFFFFFL);
    }

    private static List<Runnable> buildSpatialWork(
            List<Entity> entities,
            Consumer<Entity> action
    ) {
        Long2ObjectMap<List<Entity>> entitiesBySection = new Long2ObjectOpenHashMap<>();
        for (Entity entity : entities) {
            entitiesBySection
                    .computeIfAbsent(sectionKey(entity), ignoredSection -> new ArrayList<>())
                    .add(entity);
        }

        int maximumEntitiesPerTask = ENTITY_TICK_COST.chunkSize(entities.size());
        long[] sectionKeys = entitiesBySection.keySet().toLongArray();
        Arrays.sort(sectionKeys);

        List<Runnable> work = new ArrayList<>();
        for (long sectionKey : sectionKeys) {
            List<Entity> sectionEntities = entitiesBySection.get(sectionKey);
            if (sectionEntities.size() >= maximumEntitiesPerTask) {
                addSlices(
                        work,
                        sectionEntities,
                        maximumEntitiesPerTask,
                        ENTITY_TICK_COST,
                        action
                );
            }
        }

        List<Entity> pendingEntities = new ArrayList<>();
        for (long sectionKey : sectionKeys) {
            List<Entity> sectionEntities = entitiesBySection.get(sectionKey);
            if (sectionEntities.size() >= maximumEntitiesPerTask) {
                continue;
            }
            if (!pendingEntities.isEmpty()
                    && pendingEntities.size() + sectionEntities.size() > maximumEntitiesPerTask) {
                emit(work, pendingEntities, ENTITY_TICK_COST, action);
                pendingEntities = new ArrayList<>();
            }
            pendingEntities.addAll(sectionEntities);
        }
        if (!pendingEntities.isEmpty()) {
            emit(work, pendingEntities, ENTITY_TICK_COST, action);
        }
        return work;
    }

    private static <ItemType> void emit(
            List<Runnable> work,
            List<ItemType> batch,
            CostModel costModel,
            Consumer<ItemType> action
    ) {
        work.add(costModel.wrap(batch.size(), () -> batch.forEach(action)));
    }

    private static <ItemType> void addSlices(
            List<Runnable> work,
            List<ItemType> items,
            int maximumItemsPerTask,
            CostModel costModel,
            Consumer<ItemType> action
    ) {
        for (int startIndex = 0; startIndex < items.size(); startIndex += maximumItemsPerTask) {
            int endIndex = Math.min(startIndex + maximumItemsPerTask, items.size());
            emit(work, items.subList(startIndex, endIndex), costModel, action);
        }
    }

    private record ParallelBatch(Queue<Runnable> queue, CountDownLatch done) {
    }

    private static ParallelBatch submitParallel(List<Runnable> work) {
        CountDownLatch done = new CountDownLatch(work.size());
        Queue<Runnable> queue = new ConcurrentLinkedQueue<>();
        for (Runnable task : work) {
            queue.add(() -> {
                try {
                    task.run();
                } catch (Throwable throwable) {
                    LOGGER.error("Error during parallel tick chunk", throwable);
                } finally {
                    done.countDown();
                }
            });
        }

        int workerCount = Math.clamp(getPoolSize(), 1, work.size());
        for (int workerIndex = 0; workerIndex < workerCount; workerIndex++) {
            executeForeground(() -> drain(queue));
        }
        return new ParallelBatch(queue, done);
    }

    private static void finishParallel(ParallelBatch batch) {
        CountDownLatch done = batch.done();

        Runnable chunk;
        while ((chunk = batch.queue().poll()) != null) {
            chunk.run();
            pumpMainThreadTasks(done);
        }

        boolean interrupted = false;
        while (done.getCount() > 0L) {
            try {
                if (done.await(POLL_INTERVAL_MICROSECONDS, TimeUnit.MICROSECONDS)) {
                    break;
                }
            } catch (InterruptedException exception) {
                interrupted = true;
            }
            pumpMainThreadTasks(done);
        }

        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static void drain(Queue<Runnable> queue) {
        Runnable chunk;
        while ((chunk = queue.poll()) != null) {
            chunk.run();
        }
    }

    private static void pumpMainThreadTasks(CountDownLatch done) {
        MinecraftServer minecraftServer = server;
        if (minecraftServer == null) {
            return;
        }
        for (ServerLevel level : minecraftServer.getAllLevels()) {
            if (done.getCount() == 0L) {
                return;
            }
            level.getChunkSource().pollTask();
        }
    }

    /**
     * Returns whether an entity must remain on the main thread.
     */
    public static boolean shouldTickSynchronously(Entity entity) {
        return getSynchronizationReasonMask(entity, true) != 0L;
    }

    /**
     * Returns every condition that currently keeps an entity on the main thread.
     */
    public static Set<SynchronizationReason> getSynchronizationReasons(Entity entity) {
        long reasonMask = getSynchronizationReasonMask(entity, false);
        if (reasonMask == 0L) {
            return Set.of();
        }

        Set<SynchronizationReason> reasons = EnumSet.noneOf(SynchronizationReason.class);
        for (SynchronizationReason reason : SynchronizationReason.values()) {
            if ((reasonMask & maskFor(reason)) != 0L) {
                reasons.add(reason);
            }
        }
        return Collections.unmodifiableSet(reasons);
    }

    private static long getSynchronizationReasonMask(Entity entity, boolean stopAfterFirstReason) {
        long reasonMask = 0L;

        if (isShuttingDown) {
            reasonMask |= maskFor(SynchronizationReason.PROCESSOR_SHUTTING_DOWN);
            if (stopAfterFirstReason) {
                return reasonMask;
            }
        }
        if (entity.level().isClientSide()) {
            reasonMask |= maskFor(SynchronizationReason.CLIENT_SIDE_LEVEL);
            if (stopAfterFirstReason) {
                return reasonMask;
            }
        }
        if (entity.portalProcess != null) {
            reasonMask |= maskFor(SynchronizationReason.ACTIVE_PORTAL_PROCESS);
            if (stopAfterFirstReason) {
                return reasonMask;
            }
        }
        if (AsyncConfig.disabled) {
            reasonMask |= maskFor(SynchronizationReason.ASYNC_DISABLED);
            if (stopAfterFirstReason) {
                return reasonMask;
            }
        }
        if (entitySupportsAsyncApi(entity)) {
            reasonMask |= maskFor(SynchronizationReason.MISSING_ASYNC_COMPATIBILITY);
            if (stopAfterFirstReason) {
                return reasonMask;
            }
        }
        if (entity instanceof Projectile) {
            reasonMask |= maskFor(SynchronizationReason.PROJECTILE);
            if (stopAfterFirstReason) {
                return reasonMask;
            }
        }
        if (entity instanceof Player) {
            reasonMask |= maskFor(SynchronizationReason.PLAYER);
            if (stopAfterFirstReason) {
                return reasonMask;
            }
        }
        if (BLOCKED_ENTITIES.contains(entity.getClass())) {
            reasonMask |= maskFor(SynchronizationReason.BLOCKED_ENTITY_CLASS);
            if (stopAfterFirstReason) {
                return reasonMask;
            }
        }
        if (BLACKLISTED_ENTITIES.contains(entity.getUUID())) {
            reasonMask |= maskFor(SynchronizationReason.RUNTIME_BLACKLIST);
            if (stopAfterFirstReason) {
                return reasonMask;
            }
        }
        if (requiresSableSynchronization(entity)) {
            reasonMask |= maskFor(SynchronizationReason.SABLE_COMPATIBILITY);
            if (stopAfterFirstReason) {
                return reasonMask;
            }
        }
        if (AsyncConfig.isEntitySynchronized(EntityType.getKey(entity.getType()))) {
            reasonMask |= maskFor(SynchronizationReason.SYNCHRONIZED_ENTITY_CONFIG);
        }

        return reasonMask;
    }

    private static long maskFor(SynchronizationReason reason) {
        return 1L << reason.ordinal();
    }

    private static boolean requiresSableSynchronization(Entity entity) {
        if (!SABLE_LOADED) {
            return false;
        }

        try {
            return SableCompatibility.shouldTickSynchronously(entity);
        } catch (LinkageError | RuntimeException exception) {
            if (SABLE_COMPATIBILITY_FAILURE_LOGGED.compareAndSet(false, true)) {
                LOGGER.error(
                        "Sable compatibility detection failed; keeping all entities on the server thread",
                        exception
                );
            }
            return true;
        }
    }

    /**
     * Returns whether a non-Minecraft entity lacks explicit Async API compatibility.
     */
    public static boolean entitySupportsAsyncApi(Entity entity) {
        Class<?> entityClass = entity.getClass();
        Boolean cachedResult = ASYNC_API_CACHE.get(entityClass);
        if (cachedResult != null) {
            return cachedResult;
        }
        boolean lacksCompatibility = !"minecraft".equals(
                EntityType.getKey(entity.getType()).getNamespace()
        ) && !entityClass.isAnnotationPresent(AsyncCompatible.class);
        ASYNC_API_CACHE.put(entityClass, lacksCompatibility);
        return lacksCompatibility;
    }

    private static void tickEntity(ServerLevel world, Entity entity, boolean asynchronous) {
        boolean recording = RECORDING_TICKS_LEFT.get() > 0;
        long startTime = recording ? System.nanoTime() : 0L;
        try {
            world.tickNonPassenger(entity);
        } catch (Exception exception) {
            LOGGER.error(
                    "Error during {} tick. Entity: {}, UUID: {}",
                    asynchronous ? "async" : "sync",
                    entity.getType(),
                    entity.getUUID(),
                    exception
            );
        } finally {
            if (recording) {
                EntityType<?> entityType = entity.getType();
                long elapsedNanoseconds = System.nanoTime() - startTime;

                if (asynchronous) {
                    ASYNC_TICK_TIME_NS
                            .computeIfAbsent(entityType, ignoredType -> new LongAdder())
                            .add(elapsedNanoseconds);
                    ASYNC_TICK_COUNT
                            .computeIfAbsent(entityType, ignoredType -> new LongAdder())
                            .increment();
                } else {
                    TICK_TIME_NS
                            .computeIfAbsent(entityType, ignoredType -> new LongAdder())
                            .add(elapsedNanoseconds);
                    TICK_COUNT
                            .computeIfAbsent(entityType, ignoredType -> new LongAdder())
                            .increment();
                }
            }
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public static void stop() {
        isShuttingDown = true;

        if (executor != null) {
            LOGGER.info("Waiting for Async poll to shutdown...");
            executor.shutdown();
            try {
                executor.awaitTermination(60L, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                LOGGER.warn("Interrupted while waiting for thread pool shutdown", exception);
            }
        }

        FOREGROUND_TASKS.clear();
        BACKGROUND_TASKS.clear();
        AsyncConfig.clearCaches();
        BLACKLISTED_ENTITIES.clear();
        SynchronizationStats.reset();
        resetEntityTickStats();
    }
}
