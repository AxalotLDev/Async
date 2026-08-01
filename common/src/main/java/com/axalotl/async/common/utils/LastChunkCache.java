package com.axalotl.async.common.utils;

import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.jetbrains.annotations.Nullable;

/**
 * Stores one thread-local chunk lookup result for the duration of a server tick.
 */
public final class LastChunkCache {
    private Object owner;
    private long positionKey;
    private ChunkStatus status;
    private ChunkAccess chunk;
    private int tick = Integer.MIN_VALUE;

    /**
     * Returns the cached chunk when every lookup component still matches.
     */
    public @Nullable ChunkAccess find(
            Object owner,
            long positionKey,
            ChunkStatus status,
            int tick
    ) {
        if (this.owner != owner
                || this.positionKey != positionKey
                || this.status != status
                || this.tick != tick) {
            return null;
        }
        return this.chunk;
    }

    /**
     * Replaces the cached lookup result.
     */
    public void store(
            Object owner,
            long positionKey,
            ChunkStatus status,
            ChunkAccess chunk,
            int tick
    ) {
        this.owner = owner;
        this.positionKey = positionKey;
        this.status = status;
        this.chunk = chunk;
        this.tick = tick;
    }
}
