package com.axalotl.async.common.utils;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Captures entities actually selected for synchronous ticking.
 */
public final class SynchronizationStats {
    private static final Logger LOGGER = LoggerFactory.getLogger(SynchronizationStats.class);
    private static final Object CAPTURE_LOCK = new Object();
    private static final List<Consumer<Snapshot>> PENDING_CALLBACKS = new ArrayList<>();

    private static List<Consumer<Snapshot>> activeCallbacks = List.of();
    private static Map<ResourceLocation, MutableEntityTypeStats> activeEntityTypes = Map.of();
    private static int activeEntityCount;
    private static volatile boolean recording;

    private SynchronizationStats() {
    }

    /**
     * Requests a complete synchronization snapshot from the next server tick.
     */
    public static void captureNextTick(Consumer<Snapshot> callback) {
        synchronized (CAPTURE_LOCK) {
            PENDING_CALLBACKS.add(callback);
        }
    }

    /**
     * Starts a pending capture at the beginning of a server tick.
     */
    public static void onServerTickStart() {
        synchronized (CAPTURE_LOCK) {
            if (recording || PENDING_CALLBACKS.isEmpty()) {
                return;
            }

            activeCallbacks = new ArrayList<>(PENDING_CALLBACKS);
            PENDING_CALLBACKS.clear();
            activeEntityTypes = new HashMap<>();
            activeEntityCount = 0;
            recording = true;
        }
    }

    /**
     * Returns whether the current server tick is collecting synchronization statistics.
     */
    public static boolean isRecording() {
        return recording;
    }

    /**
     * Records one entity that the batch classifier selected for synchronous ticking.
     */
    public static void record(Entity entity, Set<SynchronizationReason> reasons) {
        if (!recording || reasons.isEmpty()) {
            return;
        }

        synchronized (CAPTURE_LOCK) {
            if (!recording) {
                return;
            }

            ResourceLocation entityTypeId = EntityType.getKey(entity.getType());
            MutableEntityTypeStats entityTypeStats = activeEntityTypes.computeIfAbsent(
                    entityTypeId,
                    ignoredEntityTypeId -> new MutableEntityTypeStats()
            );
            entityTypeStats.entityCount++;
            entityTypeStats.reasons.addAll(reasons);
            activeEntityCount++;
        }
    }

    /**
     * Publishes a completed capture at the end of a server tick.
     */
    public static void onServerTickEnd() {
        Snapshot snapshot;
        List<Consumer<Snapshot>> callbacks;

        synchronized (CAPTURE_LOCK) {
            if (!recording) {
                return;
            }

            snapshot = buildSnapshot();
            callbacks = activeCallbacks;
            activeCallbacks = List.of();
            activeEntityTypes = Map.of();
            activeEntityCount = 0;
            recording = false;
        }

        for (Consumer<Snapshot> callback : callbacks) {
            try {
                callback.accept(snapshot);
            } catch (RuntimeException exception) {
                LOGGER.error("Failed to publish synchronized entity statistics", exception);
            }
        }
    }

    /**
     * Clears pending and active captures during server shutdown.
     */
    public static void reset() {
        synchronized (CAPTURE_LOCK) {
            PENDING_CALLBACKS.clear();
            activeCallbacks = List.of();
            activeEntityTypes = Map.of();
            activeEntityCount = 0;
            recording = false;
        }
    }

    private static Snapshot buildSnapshot() {
        Map<ResourceLocation, EntityTypeStats> entityTypes = new HashMap<>();
        for (Map.Entry<ResourceLocation, MutableEntityTypeStats> entry
                : activeEntityTypes.entrySet()) {
            MutableEntityTypeStats mutableEntityTypeStats = entry.getValue();
            entityTypes.put(
                    entry.getKey(),
                    new EntityTypeStats(
                            mutableEntityTypeStats.entityCount,
                            mutableEntityTypeStats.reasons
                    )
            );
        }

        return new Snapshot(activeEntityCount, entityTypes);
    }

    /**
     * Statistics for the synchronized instances of one entity type.
     */
    public record EntityTypeStats(int entityCount, Set<SynchronizationReason> reasons) {
        public EntityTypeStats {
            reasons = Set.copyOf(reasons);
        }
    }

    /**
     * An immutable synchronization-statistics snapshot.
     */
    public record Snapshot(
            int synchronizedEntityCount,
            Map<ResourceLocation, EntityTypeStats> entityTypes
    ) {
        public Snapshot {
            entityTypes = Map.copyOf(entityTypes);
        }

        /**
         * Returns the unique set of synchronized entity types.
         */
        public Set<ResourceLocation> synchronizedEntityTypes() {
            return entityTypes.keySet();
        }
    }

    private static final class MutableEntityTypeStats {
        private int entityCount;
        private final Set<SynchronizationReason> reasons =
                EnumSet.noneOf(SynchronizationReason.class);
    }
}
