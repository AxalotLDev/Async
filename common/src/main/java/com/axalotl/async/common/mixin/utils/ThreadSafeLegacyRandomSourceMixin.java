package com.axalotl.async.common.mixin.utils;

import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.MarsagliaPolarGaussian;
import org.spongepowered.asm.mixin.*;

import java.util.concurrent.atomic.AtomicLong;

@Mixin(LegacyRandomSource.class)
public abstract class ThreadSafeLegacyRandomSourceMixin {

    @Unique
    private final AtomicLong async$seed = new AtomicLong();

    @Shadow
    @Final
    private MarsagliaPolarGaussian gaussianSource;

    @Unique
    private static final long async$MULTIPLIER = 25214903917L;
    @Unique
    private static final long async$INCREMENT = 11L;
    @Unique
    private static final long MODULUS_MASK = 281474976710655L;

    /**
     * @author Minecraft
     * @reason ThreadSafeLegacyRandomSource
     */
    @Overwrite
    public void setSeed(long seed) {
        this.async$seed.set((seed ^ async$MULTIPLIER) & MODULUS_MASK);
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
            i = this.async$seed.get();
            j = (i * async$MULTIPLIER + async$INCREMENT) & MODULUS_MASK;
        } while (!this.async$seed.compareAndSet(i, j));

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