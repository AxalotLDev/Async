package com.axalotl.async;

import com.axalotl.async.serdes.SerDesHookTypes;
import com.axalotl.async.serdes.SerDesRegistry;
import com.axalotl.async.serdes.filter.ISerDesFilter;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.entity.Entity;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.entity.TntEntity;
import net.minecraft.entity.passive.AllayEntity;
import net.minecraft.entity.passive.DolphinEntity;
import net.minecraft.entity.passive.FoxEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.HopperMinecartEntity;
import net.minecraft.entity.vehicle.TntMinecartEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import net.minecraft.server.world.ServerWorld;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class ParallelProcessor {
    private static final Logger LOGGER = LogManager.getLogger();
    @Getter
    @Setter
    protected static MinecraftServer server;
    protected static ExecutorService tickPool;
    protected static AtomicInteger ThreadPoolID = new AtomicInteger();
    @Getter
    public static AtomicInteger currentEnts = new AtomicInteger();

    private static final Map<Class<? extends Entity>, Boolean> modEntityCache = new ConcurrentHashMap<>();
    private static final Set<Class<?>> specialEntities = Set.of(
            PlayerEntity.class,
            ServerPlayerEntity.class,
            TntMinecartEntity.class,
            FallingBlockEntity.class,
            AllayEntity.class,
            DolphinEntity.class,
            FoxEntity.class,
            HopperMinecartEntity.class
    );

    static Map<String, Set<Thread>> mcThreadTracker = new ConcurrentHashMap<>();

    public static void setupThreadPool(int parallelism) {
        final ClassLoader cl = Async.class.getClassLoader();
        ForkJoinPool.ForkJoinWorkerThreadFactory tickThreadFactory = p -> {
            ForkJoinWorkerThread fjwt = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(p);
            fjwt.setName("Async-Tick-Pool-Thread-" + ThreadPoolID.getAndIncrement());
            regThread("Async-Tick", fjwt);
            fjwt.setContextClassLoader(cl);
            return fjwt;
        };
        tickPool = new ForkJoinPool(parallelism, tickThreadFactory, (t, e) -> LOGGER.error("Error on create Async tickPool", e), true);
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


    public static void callEntityTick(Consumer<Entity> tickConsumer, Entity entityIn, ServerWorld serverworld) {
        if (Async.config.disabled || Async.config.disableEntity || entityIn.isRemoved() || !entityIn.isAlive() ||
                (entityIn.portalManager != null && entityIn.portalManager.isInPortal()) || isModEntity(entityIn) ||
                specialEntities.contains(entityIn.getClass()) || (Async.config.disableTNT && entityIn instanceof TntEntity)) {
            tickConsumer.accept(entityIn);
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                final ISerDesFilter filter = SerDesRegistry.getFilter(SerDesHookTypes.EntityTick, entityIn.getClass());
                currentEnts.incrementAndGet();
                if (filter != null) {
                    filter.serialise(() -> tickConsumer.accept(entityIn), entityIn, entityIn.getBlockPos(), serverworld, SerDesHookTypes.EntityTick);
                } else {
                    tickConsumer.accept(entityIn);
                }
            } catch (Exception e) {
                LOGGER.error("Exception ticking Entity {} at {}", entityIn.getType().getName(), entityIn.getPos(), e);
            } finally {
                currentEnts.decrementAndGet();
            }
        }, tickPool);
    }

    private static boolean isModEntity(Entity entityIn) {
        return modEntityCache.computeIfAbsent(entityIn.getClass(), clazz ->
                !clazz.getPackageName().startsWith("net.minecraft")
        );
    }

    public static void stop() {
        tickPool.shutdown();
        try {
            if (!tickPool.awaitTermination(60, TimeUnit.SECONDS)) {
                tickPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            tickPool.shutdownNow();
        }
    }
}