package com.axalotl.async.common.utils;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import org.jspecify.annotations.NonNull;

public final class AsyncLevelRandom implements RandomSource {

    private static final long TICK_MIX = 0x9E3779B97F4A7C15L;

    private final Level level;
    private final RandomSource ownerStream;
    private final Thread owner;
    private final long levelMix;
    private final ThreadLocal<RandomSource> workerStream = new ThreadLocal<>();

    public AsyncLevelRandom(Level level, RandomSource ownerStream) {
        this.level = level;
        this.ownerStream = ownerStream;
        this.owner = Thread.currentThread();
        this.levelMix = System.identityHashCode(ownerStream);
    }

    public static void beginChunk(Level level, long chunkKey) {
        if (level.getRandom() instanceof AsyncLevelRandom random) {
            random.beginChunk(chunkKey);
        }
    }

    public void beginChunk(long chunkKey) {
        if (Thread.currentThread() == this.owner) {
            return;
        }
        RandomSource worker = this.workerStream.get();
        if (worker == null) {
            worker = RandomSource.create(this.levelMix);
            this.workerStream.set(worker);
        }
        worker.setSeed(chunkKey ^ (this.levelMix + this.level.getGameTime() * TICK_MIX));
    }

    private RandomSource current() {
        if (Thread.currentThread() == this.owner) {
            return this.ownerStream;
        }
        RandomSource worker = this.workerStream.get();
        if (worker == null) {
            worker = RandomSource.create(this.levelMix);
            this.workerStream.set(worker);
        }
        return worker;
    }

    @Override
    public @NonNull RandomSource fork() {
        return this.current().fork();
    }

    @Override
    public @NonNull PositionalRandomFactory forkPositional() {
        return this.current().forkPositional();
    }

    @Override
    public void setSeed(long seed) {
        this.current().setSeed(seed);
    }

    @Override
    public int nextInt() {
        return this.current().nextInt();
    }

    @Override
    public int nextInt(int bound) {
        return this.current().nextInt(bound);
    }

    @Override
    public long nextLong() {
        return this.current().nextLong();
    }

    @Override
    public boolean nextBoolean() {
        return this.current().nextBoolean();
    }

    @Override
    public float nextFloat() {
        return this.current().nextFloat();
    }

    @Override
    public double nextDouble() {
        return this.current().nextDouble();
    }

    @Override
    public double nextGaussian() {
        return this.current().nextGaussian();
    }
}
