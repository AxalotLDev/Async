package com.axalotl.async.mixin;

import com.axalotl.async.ParallelProcessor;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.atomic.AtomicInteger;

@Mixin(Util.class)
public abstract class UtilMixin {
    @Inject(method = "method_28123", at = @At(value = "INVOKE", target = "Ljava/util/concurrent/ForkJoinWorkerThread;setName(Ljava/lang/String;)V"))
    private static void registerThread(String string, AtomicInteger atomicInteger, ForkJoinPool pool, CallbackInfoReturnable<ForkJoinWorkerThread> cir, @Local ForkJoinWorkerThread forkJoinWorkerThread) {
        ParallelProcessor.regThread(string, forkJoinWorkerThread);
    }
}