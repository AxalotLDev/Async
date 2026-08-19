package com.axalotl.async.common;

import com.axalotl.async.api.utils.AsyncCompatible;
import com.axalotl.async.common.config.AsyncConfig;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.entity.vehicle.boat.Boat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import static com.axalotl.async.common.utils.TickStats.*;

public class ParallelProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(ParallelProcessor.class);

    @Getter
    @Setter
    private static MinecraftServer server;

    //Thread pool
    public static ExecutorService executor;
    private static final AtomicInteger THREAD_POOL_ID = new AtomicInteger();
    private static volatile boolean isShuttingDown = false;

    private static final Queue<Runnable> FOREGROUND_TASKS = new ConcurrentLinkedQueue<>();
    private static final Queue<Runnable> BACKGROUND_TASKS = new ConcurrentLinkedQueue<>();

    public static final Executor BACKGROUND = ParallelProcessor::executeBackground;
    public static final Executor FOREGROUND = ParallelProcessor::executeForeground;

    //Blacklist
    private static final Set<UUID> BLACKLISTED_ENTITIES = ConcurrentHashMap.newKeySet();
    private static final Set<Class<?>> BLOCKED_ENTITIES = Set.of(
            FallingBlockEntity.class,
            Shulker.class,
            AbstractBoat.class,
            Boat.class,
            EnderDragon.class
    );

    //Cache
    private static final Map<Class<?>, Boolean> ASYNC_API_CACHE = new ConcurrentHashMap<>();

    private static final Map<Class<?>, Boolean> SYNC_BY_CLASS = new ConcurrentHashMap<>();

    public static void onSyncRulesChanged() {
        SYNC_BY_CLASS.clear();
    }

    //Threads
    private static final Map<String, Set<WeakReference<Thread>>> MC_THREAD_TRACKER = new ConcurrentHashMap<>();

    //Batch
    private static final int SECTION_SHIFT = 2;

    public static void executeForeground(Runnable task) {
        FOREGROUND_TASKS.add(task);
        submitSlot();
    }

    public static void executeBackground(Runnable task) {
        BACKGROUND_TASKS.add(task);
        submitSlot();
    }

    private static volatile boolean poolUnavailableLogged;

    private static void submitSlot() {
        ExecutorService pool = executor;
        if (pool != null && !pool.isShutdown()) {
            pool.execute(ParallelProcessor::runOnePrioritized);
            return;
        }
        if (!poolUnavailableLogged) {
            poolUnavailableLogged = true;
            LOGGER.error("Async pool unavailable (executor={}); running tasks inline on {}",
                    pool, Thread.currentThread().getName(), new IllegalStateException("pool unavailable"));
        }
        runOnePrioritized();
    }

    private static void runOnePrioritized() {
        Runnable task = FOREGROUND_TASKS.poll();
        if (task == null) task = BACKGROUND_TASKS.poll();
        if (task == null) return;
        try {
            task.run();
        } catch (Throwable e) {
            LOGGER.error("Error in pool task", e);
        }
    }

    public static final CostModel ENTITY_TICK_COST = new CostModel(25_000);
    public static final CostModel DESPAWN_COST = new CostModel(2_000);
    public static final CostModel GENERIC_COST = new CostModel(100_000);
    public static final CostModel SPAWN_COST = new CostModel(100_000);

    public static final class CostModel {
        private static final long TARGET_TASK_NANOS = 250_000;
        private static final long MIN_PARALLEL_NANOS = 2 * TARGET_TASK_NANOS;
        private static final double SMOOTHING = 0.25;

        private final LongAdder batchNanos = new LongAdder();
        private final LongAdder batchItems = new LongAdder();
        private volatile double nanosPerItem;

        CostModel(double seedNanosPerItem) {
            this.nanosPerItem = seedNanosPerItem;
        }

        public Runnable wrap(int items, Runnable task) {
            return () -> {
                long start = System.nanoTime();
                try {
                    task.run();
                } finally {
                    batchNanos.add(System.nanoTime() - start);
                    batchItems.add(items);
                }
            };
        }

        private synchronized void fold() {
            long items = batchItems.sumThenReset();
            if (items <= 0) return;
            long nanos = batchNanos.sumThenReset();
            double sample = Math.clamp((double) nanos / items, 100.0, 2_000_000.0);
            double current = nanosPerItem;
            nanosPerItem = current + (sample - current) * SMOOTHING;
        }

        public int chunkSize(int workItems) {
            fold();
            int byCost = (int) Math.max(1, (long) (TARGET_TASK_NANOS / nanosPerItem));
            int participants = Math.max(1, getPoolSize()) + 1;
            int fairShare = Math.max(1, (workItems + participants - 1) / participants);
            return Math.clamp(byCost, 1, fairShare);
        }

        public boolean shouldRunSequentially(int workItems) {
            fold();
            return workItems * nanosPerItem < MIN_PARALLEL_NANOS;
        }
    }

    public static void setupThreadPool(int parallelism, Class<?> asyncClass) {
        isShuttingDown = false;

        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable, "Async-Tick-Pool-Thread-" + THREAD_POOL_ID.getAndIncrement());
            registerThread("Async-Tick", thread);
            thread.setDaemon(true);
            thread.setPriority(Thread.NORM_PRIORITY - 1);
            thread.setContextClassLoader(asyncClass.getClassLoader());
            return thread;
        };

        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                parallelism,
                parallelism,
                0L,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(),
                threadFactory
        );
        pool.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
        pool.allowCoreThreadTimeOut(false);
        pool.prestartAllCoreThreads();

        executor = pool;
        LOGGER.info("Initialized Pool with {} threads", parallelism);
    }

    public static void registerThread(String poolName, Thread thread) {
        MC_THREAD_TRACKER
                .computeIfAbsent(poolName, _ -> ConcurrentHashMap.newKeySet())
                .add(new WeakReference<>(thread));
    }

    private static boolean isThreadInPool(Thread thread) {
        for (WeakReference<Thread> ref : MC_THREAD_TRACKER.getOrDefault("Async-Tick", Set.of())) {
            if (ref.get() == thread) return true;
        }
        return false;
    }

    public static boolean isServerExecutionThread() {
        return isThreadInPool(Thread.currentThread());
    }

    public static int getPoolSize() {
        if (executor instanceof ThreadPoolExecutor pool) {
            return pool.getCorePoolSize();
        }
        return 0;
    }

    public static void callEntityTickBatch(ServerLevel world, List<Entity> entities) {
        callEntityTickBatch(world, entities, null);
    }

    public static void callEntityTickBatch(ServerLevel world, List<Entity> entities, List<Entity> despawnOnly) {
        final boolean despawn = despawnOnly != null;
        if (entities.isEmpty() && (!despawn || despawnOnly.isEmpty())) return;

        if (AsyncConfig.disabled) {
            if (despawn) despawnOnly.forEach(ParallelProcessor::checkDespawn);
            entities.forEach(e -> tickOne(world, e, false, despawn));
            return;
        }

        List<Entity> asyncEntities = new ArrayList<>();
        List<Entity> syncEntities = new ArrayList<>();
        for (Entity entity : entities) {
            if (entity == null || entity.isRemoved()) {
                continue;
            }
            if (shouldTickSynchronously(entity)) {
                syncEntities.add(entity);
            } else {
                asyncEntities.add(entity);
            }
        }
        if (ENTITY_TICK_COST.shouldRunSequentially(asyncEntities.size())) {
            if (despawn) despawnOnly.forEach(ParallelProcessor::checkDespawn);
            asyncEntities.forEach(e -> tickOne(world, e, false, despawn));
            syncEntities.forEach(e -> tickOne(world, e, false, despawn));
            return;
        }

        final List<Runnable> work = buildSpatialWork(asyncEntities,
                e -> tickOne(world, e, true, despawn));
        if (despawn && !despawnOnly.isEmpty()) {
            addSlices(work, despawnOnly, DESPAWN_COST.chunkSize(despawnOnly.size()), DESPAWN_COST,
                    ParallelProcessor::checkDespawn);
        }

        ParallelBatch batch = submitParallel(work);
        syncEntities.forEach(e -> tickOne(world, e, false, despawn));
        finishParallel(batch);
    }

    private static void tickOne(ServerLevel world, Entity entity, boolean async, boolean despawn) {
        if (entity.isRemoved()) return;
        if (despawn) checkDespawn(entity);
        tickEntity(world, entity, async);
    }

    private static void checkDespawn(Entity entity) {
        if (entity.isRemoved()) return;
        try {
            entity.checkDespawn();
        } catch (Exception e) {
            LOGGER.error("Error during despawn check. Entity: {}, UUID: {}", entity.getType(), entity.getUUID(), e);
        }
    }

    private static long sectionKey(Entity entity) {
        ChunkPos pos = entity.chunkPosition();
        return (((long) (pos.x() >> SECTION_SHIFT)) << 32) | ((pos.z() >> SECTION_SHIFT) & 0xFFFFFFFFL);
    }

    private static List<Runnable> buildSpatialWork(List<Entity> entities, Consumer<Entity> action) {
        CostModel cost = ENTITY_TICK_COST;
        Long2ObjectMap<List<Entity>> bySection = new Long2ObjectOpenHashMap<>();
        for (Entity entity : entities) {
            bySection.computeIfAbsent(sectionKey(entity), _ -> new ArrayList<>()).add(entity);
        }
        int maxPerTask = cost.chunkSize(entities.size());
        long[] keys = bySection.keySet().toLongArray();
        Arrays.sort(keys);

        List<Runnable> work = new ArrayList<>();
        for (long key : keys) {
            List<Entity> section = bySection.get(key);
            if (section.size() >= maxPerTask) {
                addSlices(work, section, maxPerTask, cost, action);
            }
        }
        List<Entity> pending = new ArrayList<>();
        for (long key : keys) {
            List<Entity> section = bySection.get(key);
            if (section.size() >= maxPerTask) continue;
            if (!pending.isEmpty() && pending.size() + section.size() > maxPerTask) {
                emit(work, pending, cost, action);
                pending = new ArrayList<>();
            }
            pending.addAll(section);
        }
        if (!pending.isEmpty()) {
            emit(work, pending, cost, action);
        }
        return work;
    }

    private static <T> void emit(List<Runnable> work, List<T> batch, CostModel cost, Consumer<T> action) {
        work.add(cost.wrap(batch.size(), () -> batch.forEach(action)));
    }

    private static <T> void addSlices(List<Runnable> work, List<T> items, int maxPerTask, CostModel cost, Consumer<T> action) {
        for (int i = 0; i < items.size(); i += maxPerTask) {
            emit(work, items.subList(i, Math.min(i + maxPerTask, items.size())), cost, action);
        }
    }

    public static <T> void forEachParallel(List<T> items, Consumer<T> action) {
        if (items.isEmpty()) return;
        if (GENERIC_COST.shouldRunSequentially(items.size())) {
            items.forEach(action);
            return;
        }

        List<Runnable> work = new ArrayList<>();
        addSlices(work, items, GENERIC_COST.chunkSize(items.size()), GENERIC_COST, action);
        runParallel(work);
    }

    private record ParallelBatch(Queue<Runnable> queue, CountDownLatch done) {
    }

    private static ParallelBatch submitParallel(List<Runnable> work) {
        final CountDownLatch done = new CountDownLatch(work.size());
        final Queue<Runnable> queue = new ConcurrentLinkedQueue<>();
        for (Runnable task : work) {
            queue.add(() -> {
                try {
                    task.run();
                } catch (Throwable e) {
                    LOGGER.error("Error during parallel tick chunk", e);
                } finally {
                    done.countDown();
                }
            });
        }

        final int workers = Math.clamp(getPoolSize(), 1, work.size());
        for (int i = 0; i < workers; i++) {
            executeForeground(() -> drain(queue));
        }
        return new ParallelBatch(queue, done);
    }

    private static final long POLL_INTERVAL_MICROS = 200;

    private static void finishParallel(ParallelBatch batch) {
        final CountDownLatch done = batch.done();

        Runnable chunk;
        while ((chunk = batch.queue().poll()) != null) {
            chunk.run();
            pumpMainThreadTasks();
        }

        boolean interrupted = false;
        while (done.getCount() > 0) {
            try {
                if (done.await(POLL_INTERVAL_MICROS, TimeUnit.MICROSECONDS)) {
                    break;
                }
            } catch (InterruptedException e) {
                interrupted = true;
            }
            pumpMainThreadTasks();
        }

        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static void runParallel(List<Runnable> work) {
        if (work.isEmpty()) return;
        finishParallel(submitParallel(work));
    }

    private static void drain(Queue<Runnable> queue) {
        Runnable chunk;
        while ((chunk = queue.poll()) != null) {
            chunk.run();
        }
    }

    private static void pumpMainThreadTasks() {
        for (ServerLevel lvl : server.getAllLevels()) {
            lvl.getChunkSource().pollTask();
        }
    }

    private static final long LOCK_POLL_MICROS = 200;

    public static boolean isMainServerThread() {
        return server != null && server.isSameThread();
    }

    public static void lockCooperatively(ReentrantLock lock) {
        if (lock.tryLock()) {
            return;
        }
        if (!isMainServerThread()) {
            lock.lock();
            return;
        }
        try {
            while (!lock.tryLock(LOCK_POLL_MICROS, TimeUnit.MICROSECONDS)) {
                pumpMainThreadTasks();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            lock.lock();
        }
    }


    public static boolean shouldTickSynchronously(Entity entity) {
        if (isShuttingDown || AsyncConfig.disabled || entity.portalProcess != null || entity.level().isClientSide()) {
            return true;
        }

        if (!BLACKLISTED_ENTITIES.isEmpty() && BLACKLISTED_ENTITIES.contains(entity.getUUID())) {
            return true;
        }

        Boolean cached = SYNC_BY_CLASS.get(entity.getClass());
        if (cached != null) {
            return cached;
        }

        boolean sync = entitySupportsAsyncApi(entity)
                || entity instanceof Projectile
                || entity instanceof Player
                || BLOCKED_ENTITIES.contains(entity.getClass())
                || AsyncConfig.isEntitySynchronized(EntityType.getKey(entity.getType()));
        SYNC_BY_CLASS.put(entity.getClass(), sync);
        return sync;
    }

    public static boolean entitySupportsAsyncApi(Entity entity) {
        Class<?> cls = entity.getClass();
        Boolean cached = ASYNC_API_CACHE.get(cls);
        if (cached != null) return cached;
        boolean result = !"minecraft".equals(EntityType.getKey(entity.getType()).getNamespace()) && !cls.isAnnotationPresent(AsyncCompatible.class);
        ASYNC_API_CACHE.put(cls, result);
        return result;
    }

    private static void tickEntity(ServerLevel world, Entity entity, boolean async) {
        final boolean recording = RECORDING_TICKS_LEFT.get() > 0;
        final long start = recording ? System.nanoTime() : 0L;
        try {
            world.tickNonPassenger(entity);
        } catch (Exception e) {
            LOGGER.error("Error during {} tick. Entity: {}, UUID: {}",
                    async ? "async" : "sync", entity.getType(), entity.getUUID(), e);
        } finally {
            if (recording) {
                EntityType<?> type = entity.getType();
                long elapsed = System.nanoTime() - start;

                if (async) {
                    ASYNC_TICK_TIME_NS.computeIfAbsent(type, _ -> new LongAdder()).add(elapsed);
                    ASYNC_TICK_COUNT.computeIfAbsent(type, _ -> new LongAdder()).increment();
                } else {
                    TICK_TIME_NS.computeIfAbsent(type, _ -> new LongAdder()).add(elapsed);
                    TICK_COUNT.computeIfAbsent(type, _ -> new LongAdder()).increment();
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
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOGGER.warn("Interrupted while waiting for thread pool shutdown", e);
            }
        }

        FOREGROUND_TASKS.clear();
        BACKGROUND_TASKS.clear();
        AsyncConfig.clearCaches();
        BLACKLISTED_ENTITIES.clear();
        resetEntityTickStats();
    }
}