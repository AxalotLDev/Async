package com.axalotl.async.common.mixin.utils;

import net.minecraft.util.ThreadingDetector;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;

import java.util.concurrent.Semaphore;

@Mixin(ThreadingDetector.class)
public abstract class LockHelperMixin {

    @Shadow
    @Final
    @Mutable
    private Semaphore lock = new Semaphore(255);
}