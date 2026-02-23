package com.axalotl.async.common.mixin.utils;

import com.axalotl.async.common.ParallelProcessor;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;


import java.util.concurrent.ThreadLocalRandom;

@Mixin(LegacyRandomSource.class)
public abstract class LegacyRandomSourceMixin {

    @Unique
    private static final ThreadLocal<long[]> async$localSeed = ThreadLocal.withInitial(
            () -> new long[]{ ThreadLocalRandom.current().nextLong() }
    );

    @Unique
    @Inject(method = "next", at = @At("HEAD"), cancellable = true)
    private void async$threadLocalNext(int bits, CallbackInfoReturnable<Integer> cir) {
        if (ParallelProcessor.isServerExecutionThread()) {
            long[] s = async$localSeed.get();
            long oldSeed = s[0];
            long newSeed = oldSeed * 0x5DEECE66DL + 0xBL & 0xFFFFFFFFFFFFL;
            s[0] = newSeed;
            cir.setReturnValue((int)(newSeed >>> (48 - bits)));
        }
    }
}