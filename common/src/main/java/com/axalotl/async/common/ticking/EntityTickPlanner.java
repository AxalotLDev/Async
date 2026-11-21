package com.axalotl.async.common.ticking;

import com.axalotl.async.common.ParallelProcessor;
import com.axalotl.async.common.ParallelProcessor.CostModel;
import com.axalotl.async.common.ParallelProcessor.ParallelBatch;
import com.axalotl.async.common.config.AsyncConfig;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;

import static com.axalotl.async.common.utils.TickStats.*;

public class EntityTickPlanner {

    private static final Logger LOGGER = LoggerFactory.getLogger(EntityTickPlanner.class);

    public static final CostModel ENTITY_TICK_COST = new CostModel(25_000);
    public static final CostModel DESPAWN_COST = new CostModel(2_000);

    private static final int SECTION_SHIFT = 2;

    public static void tickEntityList(ServerLevel level, boolean asyncDespawn) {
        ProfilerFiller profiler = Profiler.get();

        List<Entity> toTick = new ArrayList<>();
        List<Entity> despawn = new ArrayList<>();

        level.entityTickList.forEach(entity -> {
            if (entity.isRemoved()) return;
            if (level.tickRateManager().isEntityFrozen(entity)) return;

            if (!asyncDespawn) {
                profiler.push("checkDespawn");
                entity.checkDespawn();
                profiler.pop();
            }

            if (!(entity instanceof ServerPlayer)
                    && !level.getChunkSource().chunkMap.getDistanceManager()
                    .inEntityTickingRange(entity.chunkPosition().pack())) {
                if (asyncDespawn) despawn.add(entity);
                return;
            }

            Entity vehicle = entity.getVehicle();
            if (vehicle != null) {
                if (!vehicle.isRemoved() && vehicle.hasPassenger(entity)) {
                    if (asyncDespawn) despawn.add(entity);
                    return;
                }
                entity.stopRiding();
            }

            toTick.add(entity);
        });

        profiler.push("tick");
        tickBatch(level, toTick, asyncDespawn ? despawn : null);
        profiler.pop();
    }

    private static void tickBatch(ServerLevel world, List<Entity> entities, @Nullable List<Entity> despawnOnly) {
        final boolean despawn = despawnOnly != null;
        if (entities.isEmpty() && (!despawn || despawnOnly.isEmpty())) return;

        if (AsyncConfig.disabled) {
            if (despawn) despawnOnly.forEach(EntityTickPlanner::checkDespawn);
            entities.forEach(e -> tickOne(world, e, false, despawn));
            return;
        }

        List<Entity> asyncEntities = new ArrayList<>();
        List<Entity> syncEntities = new ArrayList<>();
        for (Entity entity : entities) {
            if (entity.isRemoved()) {
                continue;
            }
            if (TickingPolicy.shouldTickSynchronously(entity)) {
                syncEntities.add(entity);
            } else {
                asyncEntities.add(entity);
            }
        }
        if (ENTITY_TICK_COST.shouldRunSequentially(asyncEntities.size())) {
            if (despawn) despawnOnly.forEach(EntityTickPlanner::checkDespawn);
            asyncEntities.forEach(e -> tickOne(world, e, false, despawn));
            syncEntities.forEach(e -> tickOne(world, e, false, despawn));
            return;
        }

        final List<Runnable> work = buildSpatialWork(asyncEntities,
                e -> tickOne(world, e, true, despawn));
        if (despawn && !despawnOnly.isEmpty()) {
            ParallelProcessor.addSlices(work, despawnOnly, DESPAWN_COST.chunkSize(despawnOnly.size()), DESPAWN_COST,
                    EntityTickPlanner::checkDespawn);
        }

        ParallelBatch batch = ParallelProcessor.submitParallel(work);
        syncEntities.forEach(e -> tickOne(world, e, false, despawn));
        ParallelProcessor.finishBatch(batch);
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
                ParallelProcessor.addSlices(work, section, maxPerTask, cost, action);
            }
        }
        List<Entity> pending = new ArrayList<>();
        for (long key : keys) {
            List<Entity> section = bySection.get(key);
            if (section.size() >= maxPerTask) continue;
            if (!pending.isEmpty() && pending.size() + section.size() > maxPerTask) {
                ParallelProcessor.emit(work, pending, cost, action);
                pending = new ArrayList<>();
            }
            pending.addAll(section);
        }
        if (!pending.isEmpty()) {
            ParallelProcessor.emit(work, pending, cost, action);
        }
        return work;
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
}
