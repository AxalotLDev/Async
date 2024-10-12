package net.himeki.mcmtfabric;

import net.himeki.mcmtfabric.config.BlockEntityLists;
import net.himeki.mcmtfabric.config.GeneralConfig;
import net.himeki.mcmtfabric.serdes.SerDesHookTypes;
import net.himeki.mcmtfabric.serdes.SerDesRegistry;
import net.himeki.mcmtfabric.serdes.filter.ISerDesFilter;
import net.himeki.mcmtfabric.serdes.pools.PostExecutePool;
import net.minecraft.block.entity.PistonBlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.entity.passive.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.ChestMinecartEntity;
import net.minecraft.entity.vehicle.HopperMinecartEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.chunk.BlockEntityTickInvoker;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public class ParallelProcessor {

    private static final Logger LOGGER = LogManager.getLogger();

    static Phaser worldPhaser;

    static ConcurrentHashMap<ServerWorld, Phaser> sharedPhasers = new ConcurrentHashMap<>();
    static ExecutorService worldPool;
    static ExecutorService tickPool;
    static MinecraftServer mcs;
    static AtomicBoolean isTicking = new AtomicBoolean();

    public static void setupThreadPool(int parallelism) {
        AtomicInteger worldPoolThreadID = new AtomicInteger();
        AtomicInteger tickPoolThreadID = new AtomicInteger();
        final ClassLoader cl = MCMT.class.getClassLoader();
        ForkJoinPool.ForkJoinWorkerThreadFactory worldThreadFactory = p -> {
            ForkJoinWorkerThread fjwt = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(p);
            fjwt.setName("MCMT-World-Pool-Thread-" + worldPoolThreadID.getAndIncrement());
            regThread("MCMT-World", fjwt);
            fjwt.setContextClassLoader(cl);
            return fjwt;
        };
        ForkJoinPool.ForkJoinWorkerThreadFactory tickThreadFactory = p -> {
            ForkJoinWorkerThread fjwt = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(p);
            fjwt.setName("MCMT-Tick-Pool-Thread-" + tickPoolThreadID.getAndIncrement());
            regThread("MCMT-Tick", fjwt);
            fjwt.setContextClassLoader(cl);
            return fjwt;
        };
        worldPool = new ForkJoinPool(Math.min(3, Math.max(parallelism / 2, 1)), worldThreadFactory, null, true);
        tickPool = new ForkJoinPool(parallelism, tickThreadFactory, null, true);
    }

    static Map<String, Set<Thread>> mcThreadTracker = new ConcurrentHashMap<>();

    // Statistics
    public static AtomicInteger currentWorlds = new AtomicInteger();
    public static AtomicInteger currentEnts = new AtomicInteger();
    public static AtomicInteger currentTEs = new AtomicInteger();
    public static AtomicInteger currentEnvs = new AtomicInteger();

    //Operation logging
    public static Set<String> currentTasks = ConcurrentHashMap.newKeySet();

    public static void regThread(String poolName, Thread thread) {
        mcThreadTracker.computeIfAbsent(poolName, s -> ConcurrentHashMap.newKeySet()).add(thread);
    }

    public static boolean isThreadPooled(String poolName, Thread t) {
        return mcThreadTracker.containsKey(poolName) && mcThreadTracker.get(poolName).contains(t);
    }

    public static boolean serverExecutionThreadPatch() {
        return isThreadPooled("MCMT-World", Thread.currentThread()) || isThreadPooled("MCMT-Tick", Thread.currentThread());
    }

    static long tickStart = 0;
    static GeneralConfig config;

    public static void preTick(int size, MinecraftServer server) {
        config = MCMT.config; // Load when config are loaded. Static loads before config update.
        if (!config.disabled && !config.disableWorld) {
            if (worldPhaser != null) {
                LOGGER.warn("Multiple servers?");
            } else {
                tickStart = System.nanoTime();
                isTicking.set(true);
                worldPhaser = new Phaser(size + 1);
                mcs = server;
            }
        }
    }

    public static void callTick(ServerWorld serverworld, BooleanSupplier hasTimeLeft, MinecraftServer server) {
        try {
            if (config.disabled || config.disableWorld) {
                serverworld.tick(hasTimeLeft);
                return;
            }
            if (mcs != server) {
                LOGGER.warn("Multiple servers?");
                config.disabled = true;
                serverworld.tick(hasTimeLeft);
            } else {
                String taskName = null;
                if (config.opsTracing) {
                    taskName = "WorldTick: " + serverworld.toString() + "@" + serverworld.hashCode();
                    currentTasks.add(taskName);
                }
                String finalTaskName = taskName;
                worldPool.execute(() -> {
                    try {
                        currentWorlds.incrementAndGet();
                        serverworld.tick(hasTimeLeft);
                    } finally {
                        worldPhaser.arriveAndDeregister();
                        currentWorlds.decrementAndGet();
                        if (config.opsTracing) currentTasks.remove(finalTaskName);
                    }
                });
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static long[] lastTickTime = new long[32];
    public static int lastTickTimePos = 0;
    public static int lastTickTimeFill = 0;

    public static void postTick(MinecraftServer server) {
        if (!config.disabled && !config.disableWorld) {
            if (mcs != server) {
                LOGGER.warn("Multiple servers?");
            } else {
                worldPhaser.arriveAndAwaitAdvance();
                isTicking.set(false);
                worldPhaser = null;
                //PostExecute logic
                Deque<Runnable> queue = PostExecutePool.POOL.getQueue();
                Iterator<Runnable> qi = queue.iterator();
                while (qi.hasNext()) {
                    Runnable r = qi.next();
                    r.run();
                    qi.remove();
                }
                lastTickTime[lastTickTimePos] = System.nanoTime() - tickStart;
                lastTickTimePos = (lastTickTimePos + 1) % lastTickTime.length;
                lastTickTimeFill = Math.min(lastTickTimeFill + 1, lastTickTime.length - 1);
            }
        }
    }

    public static void preChunkTick(ServerWorld world) {
        Phaser phaser; // Keep a party throughout 3 ticking phases
        if (!config.disabled && !config.disableEnvironment) {
            phaser = new Phaser(2);
        } else {
            phaser = new Phaser(1);
        }
        sharedPhasers.put(world, phaser);
    }

    public static void preEntityTick(ServerWorld world) {
        if (!config.disabled && !config.disableEntity) sharedPhasers.get(world).register();
    }

    public static void callEntityTick(Consumer<Entity> tickConsumer, Entity entityIn, ServerWorld serverworld) {
        if (config.disabled || config.disableEntity) {
            tickConsumer.accept(entityIn);
            return;
        }
        if (sharedPhasers.get(serverworld).getRegisteredParties() >= 65535) {
            tickConsumer.accept(entityIn);
            return;
        }
        if (entityIn == null || entityIn.isRemoved() || !entityIn.isAlive() || (entityIn.portalManager != null && entityIn.portalManager.isInPortal())) {
            tickConsumer.accept(entityIn);
            return;
        }
        if (entityIn instanceof PlayerEntity || entityIn instanceof ServerPlayerEntity ||
                entityIn instanceof FallingBlockEntity ||
                entityIn instanceof AllayEntity ||
                entityIn instanceof DolphinEntity ||
                entityIn instanceof HopperMinecartEntity || entityIn instanceof ChestMinecartEntity ||
                entityIn instanceof FoxEntity
        ) {
            tickConsumer.accept(entityIn);
            return;
        }
        String taskName = null;
        if (config.opsTracing) {
            taskName = "EntityTick: " + entityIn/* + KG: Wayyy too slow. Maybe for debug but needs to be done via flag in that circumstance "@" + entityIn.hashCode()*/;
            currentTasks.add(taskName);
        }
        String finalTaskName = taskName;
        sharedPhasers.get(serverworld).register();
        tickPool.execute(() -> {
            try {
                final ISerDesFilter filter = SerDesRegistry.getFilter(SerDesHookTypes.EntityTick, entityIn.getClass());
                currentEnts.incrementAndGet();
                if (filter != null) {
                    filter.serialise(() -> tickConsumer.accept(entityIn), entityIn, entityIn.getBlockPos(), serverworld, SerDesHookTypes.EntityTick);
                } else {
                    tickConsumer.accept(entityIn);
                }
            } catch (Exception e) {
                System.err.println("Exception ticking Entity " + entityIn.getType().getName() + " at " + entityIn.getPos());
                e.printStackTrace();
            } finally {
                if (config.opsTracing) currentTasks.remove(finalTaskName);
                sharedPhasers.get(serverworld).arriveAndDeregister();
                currentEnts.decrementAndGet();
            }
        });
    }

    public static void postEntityTick(ServerWorld world) {
        if (!config.disabled && !config.disableEntity) {
            var phaser = sharedPhasers.get(world);
            phaser.arriveAndDeregister();
            phaser.arriveAndAwaitAdvance();
        }
    }

    public static boolean filterTE(BlockEntityTickInvoker tte) {
        boolean isLocking = BlockEntityLists.teBlackList.contains(tte.getClass());
        // Apparently a string starts with check is faster than Class.getPackage; who knew (I didn't)
        if (!isLocking && config.chunkLockModded && !tte.getClass().getName().startsWith("net.minecraft.block.entity.")) {
            isLocking = true;
        }
        if (isLocking && BlockEntityLists.teWhiteList.contains(tte.getClass())) {
            isLocking = false;
        }
        if (tte instanceof PistonBlockEntity) {
            isLocking = true;
        }
        return isLocking;
    }

    public static boolean shouldThreadChunks() {
        return !MCMT.config.disableMultiChunk;
    }
}