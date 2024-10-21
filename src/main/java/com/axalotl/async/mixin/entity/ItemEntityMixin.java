package com.axalotl.async.mixin.entity;

import net.minecraft.entity.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.locks.ReentrantLock;

@Mixin(ItemEntity.class)
public class ItemEntityMixin {
    @Unique
    private static final ReentrantLock lock = new ReentrantLock();

    @Inject(method = "tryMerge()V", at = @At(value = "HEAD"))
    private void lock(CallbackInfo ci) {
        lock.lock();
    }

    @Inject(method = "tryMerge()V", at = @At(value = "RETURN"))
    private void unlock(CallbackInfo ci) {
        lock.unlock();
    }
}
