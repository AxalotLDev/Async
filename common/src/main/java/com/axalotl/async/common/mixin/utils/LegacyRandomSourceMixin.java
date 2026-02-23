package com.axalotl.async.common.mixin.utils;

import com.axalotl.async.common.ParallelProcessor;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

@Mixin(LegacyRandomSource.class)
public abstract class LegacyRandomSourceMixin {

    @Unique
    private final AtomicLong async$instanceSeed = new AtomicLong(ThreadLocalRandom.current().nextLong());

    @Inject(method = "next", at = @At("HEAD"), cancellable = true)
    private void async$threadLocalNext(int bits, CallbackInfoReturnable<Integer> cir) {
        if (ParallelProcessor.isServerExecutionThread()) {
            long oldSeed, newSeed;
            do {
                oldSeed = async$instanceSeed.get();
                newSeed = oldSeed * 0x5DEECE66DL + 0xBL & 0xFFFFFFFFFFFFL;
            } while (!async$instanceSeed.compareAndSet(oldSeed, newSeed));

            cir.setReturnValue((int) (newSeed >>> (48 - bits)));
        }
    }
}