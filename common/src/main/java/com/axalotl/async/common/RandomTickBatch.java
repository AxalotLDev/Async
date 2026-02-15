package com.axalotl.async.common;

import java.util.concurrent.RecursiveAction;
import java.util.function.Consumer;
import net.minecraft.world.level.chunk.LevelChunk;

public final class RandomTickBatch extends RecursiveAction {

    private static final int RANDOM_TICK_GRAIN = 16;

    private final LevelChunk[] chunks;
    private final int from;
    private final int to;
    private final Consumer<LevelChunk> action;

    public RandomTickBatch(LevelChunk[] chunks, int from, int to, Consumer<LevelChunk> action) {
        this.chunks = chunks;
        this.from = from;
        this.to = to;
        this.action = action;
    }

    @Override
    protected void compute() {
        int size = to - from;
        if (size <= RANDOM_TICK_GRAIN) {
            for (int i = from; i < to; i++) {
                try {
                    action.accept(chunks[i]);
                } catch (Throwable e) {
                    ParallelProcessor.LOGGER.error("Error in async random tick", e);
                }
            }
        } else {
            int mid = (from + to) >>> 1;
            invokeAll(new RandomTickBatch(chunks, from, mid, action), new RandomTickBatch(chunks, mid, to, action));
        }
    }
}
