package com.axalotl.async.common.spawn;

import com.axalotl.async.common.ParallelProcessor;
import com.axalotl.async.common.ParallelProcessor.ParallelBatch;
import com.axalotl.async.common.utils.AsyncLevelRandom;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class SpawnBatch {

    private final ServerLevel level;

    private List<Runnable> batch = new ArrayList<>();

    private @Nullable ParallelBatch inFlight;

    public SpawnBatch(ServerLevel level) {
        this.level = level;
    }

    public void runChunk(long chunkKey, Runnable chunkAction) {
        AsyncLevelRandom.beginChunk(this.level, chunkKey);
        chunkAction.run();
    }

    public void submit(long chunkKey, Runnable chunkAction) {
        this.batch.add(() -> runChunk(chunkKey, chunkAction));
    }

    public void joinAndSubmit() {
        ParallelProcessor.finishBatch(this.inFlight);
        this.inFlight = null;

        if (this.batch.isEmpty()) {
            return;
        }

        List<Runnable> ready = this.batch;
        this.batch = new ArrayList<>();
        this.inFlight = ParallelProcessor.submitBatch(() -> ready.forEach(Runnable::run));
    }
}
