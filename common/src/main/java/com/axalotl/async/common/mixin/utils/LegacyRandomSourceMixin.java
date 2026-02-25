package com.axalotl.async.common.mixin.utils;

import com.axalotl.async.common.parallelised.utils.AsyncRandomState;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.MarsagliaPolarGaussian;
import org.spongepowered.asm.mixin.*;

import java.util.concurrent.atomic.AtomicLong;

@Mixin(LegacyRandomSource.class)
public abstract class LegacyRandomSourceMixin {

    @Shadow
    @Final
    private AtomicLong seed;

    @Shadow
    @Final
    private MarsagliaPolarGaussian gaussianSource;

    /**
     * @author Async
     * @reason ThreadSafe — vanilla seed field is always initialized, no mixin fields touched
     */
    @Overwrite
    public void setSeed(long seed) {
        this.seed.set((seed ^ 25214903917L) & 281474976710655L);
        this.gaussianSource.reset();
        AsyncRandomState.bumpVersion();
    }

    /**
     * @author Async
     * @reason ThreadSafe — per-thread seed eliminates CAS contention
     */
    @Overwrite
    public int next(int bits) {
        return AsyncRandomState.next(this.seed, bits);
    }

    /**
     * @author Async
     * @reason ThreadSafe
     */
    @Overwrite
    public double nextGaussian() {
        return this.gaussianSource.nextGaussian();
    }
}