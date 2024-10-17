package com.axalotl.async;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.entity.Entity;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.entity.TntEntity;
import net.minecraft.entity.passive.AllayEntity;
import net.minecraft.entity.passive.DolphinEntity;
import net.minecraft.entity.passive.FoxEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.TntMinecartEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class ParallelProcessor {
    private static final Logger LOGGER = LogManager.getLogger();

    @Getter
    protected static Phaser phaser;

    protected static ExecutorService tickPool;

    @Getter
    @Setter
    protected static MinecraftServer server;

    protected static AtomicInteger ThreadPoolID = null;

    @Getter
    protected static AtomicBoolean isTicking = new AtomicBoolean();
    public static AtomicInteger currentEnts = new AtomicInteger();

    static Map<String, Set<Thread>> mcThreadTracker = new ConcurrentHashMap<>();

    public static void setupThreadPool(int parallelism) {
        if (Async.config.useVirtualThreads) {
            tickPool = Executors.newVirtualThreadPerTaskExecutor();
        } else {
            ThreadPoolID = new AtomicInteger();
            final ClassLoader cl = Async.class.getClassLoader();
            ForkJoinPool.ForkJoinWorkerThreadFactory tickThreadFactory = p -> {
                ForkJoinWorkerThread fjwt = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(p);
                fjwt.setName("Async-Tick-Pool-Thread-" + ThreadPoolID.getAndIncrement());
                regThread("Async-Tick", fjwt);
                fjwt.setContextClassLoader(cl);
                return fjwt;
            };
            tickPool = new ForkJoinPool(parallelism, tickThreadFactory, (t, e) -> LOGGER.error("Error on create tickPool", e), true);
        }
    }


    public static void regThread(String poolName, Thread thread) {
        mcThreadTracker.computeIfAbsent(poolName, s -> ConcurrentHashMap.newKeySet()).add(thread);
    }

    public static boolean isThreadPooled(String poolName, Thread t) {
        return mcThreadTracker.containsKey(poolName) && mcThreadTracker.get(poolName).contains(t);
    }

    public static boolean serverExecutionThreadPatch() {
        return isThreadPooled("Async-Tick", Thread.currentThread());
    }

    public static void startTick(MinecraftServer server) {
        if (phaser != null) {
            return;
        }
        ParallelProcessor.server = server;
        isTicking.set(true);
        phaser = new Phaser();
        phaser.register();
    }

    public static void endTick(MinecraftServer server) {
        if (ParallelProcessor.server == server) {
            phaser.arriveAndAwaitAdvance();
            isTicking.set(false);
            phaser = null;
        }
    }


    public static void callEntityTick(Entity entityIn) {
        if (Async.config.disabled || Async.config.disableEntity) {
            entityIn.tick();
            return;
        }
        if (phaser.getRegisteredParties() >= 65535) {
            entityIn.tick();
            return;
        }
        if (!isTicking.get()) {
            entityIn.tick();
            return;
        }
        if (isModEntity(entityIn)) {
            entityIn.tick();
            return;
        }
        if (entityIn.isRemoved() || !entityIn.isAlive() || (entityIn.portalManager != null && entityIn.portalManager.isInPortal())) {
            entityIn.tick();
            return;
        }
        if (Async.config.disableTNT && entityIn instanceof TntEntity) {
            entityIn.tick();
            return;
        }
        if (entityIn instanceof PlayerEntity || entityIn instanceof ServerPlayerEntity ||
                entityIn instanceof TntMinecartEntity ||
                entityIn instanceof FallingBlockEntity ||
                entityIn instanceof AllayEntity ||
                entityIn instanceof DolphinEntity ||
                entityIn instanceof FoxEntity
        ) {
            entityIn.tick();
            return;
        }
        phaser.register();
        tickPool.execute(() -> {
            try {
                currentEnts.incrementAndGet();
                entityIn.tick();
            } catch (Exception e) {
                LOGGER.error("Exception ticking Entity {} at {}", entityIn.getType().getName(), entityIn.getPos(), e);
            } finally {
                phaser.arriveAndDeregister();
                currentEnts.decrementAndGet();
            }
        });
    }

    private static boolean isModEntity(Entity entityIn) {
        return !entityIn.getClass().getPackageName().startsWith("net.minecraft");
    }
}