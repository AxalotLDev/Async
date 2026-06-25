package com.axalotl.async.common.mixin.utils;

import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.MarsagliaPolarGaussian;
import org.spongepowered.asm.mixin.*;

import java.util.concurrent.atomic.AtomicLong;

@Mixin(LegacyRandomSource.class)
public abstract class ThreadSafeLegacyRandomSourceMixin {

    @Unique
    private final AtomicLong seed = new AtomicLong();

    @Shadow
    @Final
    private MarsagliaPolarGaussian gaussianSource;

    @Unique
    private static final long MULTIPLIER = 25214903917L;
    @Unique
    private static final long INCREMENT = 11L;
    @Unique
    private static final long MODULUS_MASK = 281474976710655L;

    /**
     * @author Minecraft
     * @reason ThreadSafeLegacyRandomSource
     */
    @Overwrite
    public void setSeed(long seed) {
        this.seed.set((seed ^ MULTIPLIER) & MODULUS_MASK);
    }

    /**
     * @author Minecraft
     * @reason ThreadSafeLegacyRandomSource
     */
    @Overwrite
    public int next(int bits) {
        long i;
        long j;
        do {
            i = this.seed.get();
            j = (i * MULTIPLIER + INCREMENT) & MODULUS_MASK;
        } while (!this.seed.compareAndSet(i, j));

        return (int)(j >>> (48 - bits));
    }

    /**
     * @author Minecraft
     * @reason ThreadSafeLegacyRandomSource
     */
    @Overwrite
    public double nextGaussian() {
        return this.gaussianSource.nextGaussian();
    }
}