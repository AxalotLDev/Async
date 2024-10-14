package com.axalotl.async;

import com.axalotl.async.config.BlockEntityLists;
import com.axalotl.async.config.GeneralConfig;
import com.axalotl.async.serdes.SerDesHookTypes;
import com.axalotl.async.serdes.SerDesRegistry;
import com.axalotl.async.serdes.filter.ISerDesFilter;
import net.minecraft.block.entity.PistonBlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.entity.TntEntity;
import net.minecraft.entity.passive.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.ChestMinecartEntity;
import net.minecraft.entity.vehicle.HopperMinecartEntity;
import net.minecraft.server.network.ServerPlayerEntity;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.chunk.BlockEntityTickInvoker;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class ParallelProcessor {

    static ConcurrentHashMap<ServerWorld, Phaser> sharedPhasers = new ConcurrentHashMap<>();
    static ExecutorService tickPool;

    public static void setupThreadPool(int parallelism) {
        if (Async.config.useVirtualThreads) {
            tickPool = Executors.newVirtualThreadPerTaskExecutor();
        } else {
            AtomicInteger tickPoolThreadID = new AtomicInteger();
            final ClassLoader cl = Async.class.getClassLoader();
            ForkJoinPool.ForkJoinWorkerThreadFactory tickThreadFactory = p -> {
                ForkJoinWorkerThread fjwt = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(p);
                fjwt.setName("Async-Tick-Pool-Thread-" + tickPoolThreadID.getAndIncrement());
                regThread("Async-Tick", fjwt);
                fjwt.setContextClassLoader(cl);
                return fjwt;
            };
            tickPool = new ForkJoinPool(parallelism, tickThreadFactory, null, true);
        }
    }

    static Map<String, Set<Thread>> mcThreadTracker = new ConcurrentHashMap<>();

    // Statistics
    public static AtomicInteger currentEnts = new AtomicInteger();

    //Operation logging
    public static Set<String> currentTasks = ConcurrentHashMap.newKeySet();

    public static void regThread(String poolName, Thread thread) {
        mcThreadTracker.computeIfAbsent(poolName, s -> ConcurrentHashMap.newKeySet()).add(thread);
    }

    public static boolean isThreadPooled(String poolName, Thread t) {
        return mcThreadTracker.containsKey(poolName) && mcThreadTracker.get(poolName).contains(t);
    }

    public static boolean serverExecutionThreadPatch() {
        return isThreadPooled("Async-Tick", Thread.currentThread());
    }

    static GeneralConfig config;

    public static void preTick() {
        config = Async.config; // Load when config are loaded. Static loads before config update.
    }

    public static void preChunkTick(ServerWorld world) {
        Phaser phaser;
        phaser = new Phaser(1);
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
        if (config.disableTNT && entityIn instanceof TntEntity) {
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
        if (!isLocking && !tte.getClass().getName().startsWith("net.minecraft.block.entity.")) {
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
}