package com.axalotl.async.common;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.minecraft.world.level.chunk.LevelChunk;

public final class RandomTickBatch {

    private static final int RANDOM_TICK_GRAIN = 16;

    private RandomTickBatch() {}

    public static CompletableFuture<Void> submit(LevelChunk[] chunks, Consumer<LevelChunk> action) {
        int length = chunks.length;
        if (length == 0) return CompletableFuture.completedFuture(null);

        int poolSize = ParallelProcessor.getPoolSize();
        int chunkSize = Math.max(RANDOM_TICK_GRAIN, length / poolSize);

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (int i = 0; i < length; i += chunkSize) {
            int from = i;
            int to = Math.min(i + chunkSize, length);
            futures.add(CompletableFuture.runAsync(() -> {
                for (int j = from; j < to; j++) {
                    action.accept(chunks[j]);
                }
            }, ParallelProcessor.tickPool).exceptionally(e -> {
                ParallelProcessor.LOGGER.error("Error in async random tick", e);
                return null;
            }));
        }

        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
    }
}