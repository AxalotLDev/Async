package com.axalotl.async.common.compat;

import com.axalotl.async.common.ParallelProcessor;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.locks.ReentrantLock;

/**
 * Keeps entity ticks that can interact with Sable sub-levels on the server thread and serializes
 * Sable state that Sable only expects to be touched by one thread.
 */
public final class SableCompatibility {
    private static final double COLLISION_QUERY_MARGIN = 2.0;
    private static final String CONTRAPTION_ENTITY_CLASS =
            "com.simibubi.create.content.contraptions.AbstractContraptionEntity";

    /**
     * Sable adds every Create contraption to its native physics pipeline from the contraption's
     * tick and removes it again in setRemoved.
     */
    private static final ClassValue<Boolean> CONTRAPTION_ENTITY = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            for (Class<?> current = type; current != null; current = current.getSuperclass()) {
                if (CONTRAPTION_ENTITY_CLASS.equals(current.getName())) {
                    return true;
                }
            }
            return false;
        }
    };

    /**
     * Guards per-level Sable state that Sable mutates without synchronization: block-change
     * bookkeeping and the native physics scene, explosion impulses, and static scratch values.
     */
    private static final ReentrantLock SHARED_STATE_LOCK = new ReentrantLock();

    private SableCompatibility() {
    }

    /**
     * Returns whether Sable can access sub-level state while ticking the entity.
     */
    public static boolean shouldTickSynchronously(Entity entity) {
        if (CONTRAPTION_ENTITY.get(entity.getClass())) {
            return true;
        }

        SableCompanion companion = SableCompanion.INSTANCE;
        if (companion.getContaining(entity) != null
                || companion.getTrackingOrVehicleSubLevel(entity) != null) {
            return true;
        }

        AABB entityBounds = entity.getBoundingBox();
        AABB movementBounds = entityBounds
                .minmax(entityBounds.move(entity.getDeltaMovement()))
                .inflate(COLLISION_QUERY_MARGIN);
        BoundingBox3d queryBounds = new BoundingBox3d(movementBounds);
        return companion.getAllIntersecting(entity.level(), queryBounds).iterator().hasNext();
    }

    /**
     * Returns whether an explosion's block rays can enter a sub-level. Rays travel at most about
     * 1.73 times the radius (strength up to 1.3 times the radius, spent at 0.225 per 0.3-block
     * step), so twice the radius plus a block covers them.
     */
    public static boolean canReachSubLevel(Level level, Vec3 center, float radius) {
        double reach = radius * 2.0 + 1.0;
        AABB explosionBounds = new AABB(
                center.x - reach,
                center.y - reach,
                center.z - reach,
                center.x + reach,
                center.y + reach,
                center.z + reach
        );
        return SableCompanion.INSTANCE
                .getAllIntersecting(level, new BoundingBox3d(explosionBounds))
                .iterator()
                .hasNext();
    }

    /**
     * Mirrors Sable's entity-ticking override: plot chunks carry no vanilla tickets, but Sable ticks
     * their entities while a plot chunk holder exists.
     */
    public static boolean isTickingPlotChunk(ServerLevel level, long chunkPosition) {
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        return container != null && container.getChunkHolder(new ChunkPos(chunkPosition)) != null;
    }

    /**
     * Returns whether the chunk lies in Sable's plot grid, where Sable serves chunks itself.
     */
    public static boolean isInPlotGrid(ServerLevel level, int chunkX, int chunkZ) {
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        return container != null && container.inBounds(chunkX, chunkZ);
    }

    /**
     * Returns the occupied plot chunk at the position, or null for an empty plot-grid chunk.
     */
    public static @Nullable LevelChunk getPlotChunk(ServerLevel level, int chunkX, int chunkZ) {
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        return container != null ? container.getChunk(new ChunkPos(chunkX, chunkZ)) : null;
    }

    /**
     * Acquires the shared-state lock. The server thread keeps servicing chunk tasks while it waits,
     * so a worker that holds the lock while waiting for a chunk can still finish.
     */
    public static void lockSharedState() {
        ParallelProcessor.lockServicingChunkTasks(SHARED_STATE_LOCK);
    }

    /**
     * Releases the lock acquired by {@link #lockSharedState()}.
     */
    public static void unlockSharedState() {
        SHARED_STATE_LOCK.unlock();
    }
}
