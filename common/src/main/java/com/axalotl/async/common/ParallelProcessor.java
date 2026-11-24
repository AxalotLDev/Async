package com.axalotl.async.common;

import com.axalotl.async.common.config.AsyncConfig;
import com.axalotl.async.common.ticking.TickingPolicy;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import static com.axalotl.async.common.utils.TickStats.resetEntityTickStats;

@Slf4j
public class ParallelProcessor {

    @Getter
    @Setter
    private static MinecraftServer server;

    //Thread pool
    public static ExecutorService executor;
    private static Class<?> contextClass;
    private static final AtomicInteger THREAD_POOL_ID = new AtomicInteger();
    @Getter
    private static volatile boolean isShuttingDown = false;

    private static final Queue<Runnable> FOREGROUND_TASKS = new ConcurrentLinkedQueue<>();
    private static final Queue<Runnable> BACKGROUND_TASKS = new ConcurrentLinkedQueue<>();

    public static final Executor BACKGROUND = ParallelProcessor::executeBackground;

    //Threads
    private static final Map<String, Set<WeakReference<Thread>>> MC_THREAD_TRACKER = new ConcurrentHashMap<>();

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
            log.error("Async pool unavailable (executor={}); running tasks inline on {}",
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
            log.error("Error in pool task", e);
        }
    }

    public static final CostModel GENERIC_COST = new CostModel(100_000);

    public static final class CostModel {
        private static final long TARGET_TASK_NANOS = 250_000;
        private static final long MIN_PARALLEL_NANOS = 2 * TARGET_TASK_NANOS;
        private static final double SMOOTHING = 0.25;

        private final LongAdder batchNanos = new LongAdder();
        private final LongAdder batchItems = new LongAdder();
        private volatile double nanosPerItem;

        public CostModel(double seedNanosPerItem) {
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
        contextClass = asyncClass;
        if (executor != null) executor.shutdown();
        executor = newPool(parallelism, asyncClass);
        log.info("Initialized Pool with {} threads", parallelism);
    }

    private static ThreadPoolExecutor newPool(int parallelism, Class<?> asyncClass) {
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
        return pool;
    }

    public static void rebuildThreadPool() {
        if (contextClass == null) return;
        int parallelism = AsyncConfig.getParallelism();
        if (parallelism == getPoolSize()) return;
        setupThreadPool(parallelism, contextClass);
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

    public static <T> void emit(List<Runnable> work, List<T> batch, CostModel cost, Consumer<T> action) {
        work.add(cost.wrap(batch.size(), () -> batch.forEach(action)));
    }

    public static <T> void addSlices(List<Runnable> work, List<T> items, int maxPerTask, CostModel cost, Consumer<T> action) {
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

    public record ParallelBatch(Queue<Runnable> queue, CountDownLatch done) {
    }

    public static ParallelBatch submitParallel(List<Runnable> work) {
        final CountDownLatch done = new CountDownLatch(work.size());
        final Queue<Runnable> queue = new ConcurrentLinkedQueue<>();
        for (Runnable task : work) {
            queue.add(() -> {
                try {
                    task.run();
                } catch (Throwable e) {
                    log.error("Error during parallel tick chunk", e);
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

    public static ParallelBatch submitBatch(Runnable task) {
        if (AsyncConfig.disabled || getPoolSize() == 0) {
            task.run();
            return null;
        }
        return submitParallel(List.of(task));
    }

    public static void finishBatch(ParallelBatch batch) {
        if (batch != null) {
            finishParallel(batch);
        }
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

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public static void stop() {
        isShuttingDown = true;

        if (executor != null) {
            log.info("Waiting for Async poll to shutdown...");
            executor.shutdown();
            try {
                executor.awaitTermination(60L, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Interrupted while waiting for thread pool shutdown", e);
            }
        }

        FOREGROUND_TASKS.clear();
        BACKGROUND_TASKS.clear();
        AsyncConfig.clearCaches();
        TickingPolicy.reset();
        resetEntityTickStats();
    }
}