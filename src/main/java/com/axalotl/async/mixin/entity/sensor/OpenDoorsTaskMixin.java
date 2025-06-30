package com.axalotl.async.mixin.entity.sensor;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.entity.ai.brain.Brain;
import net.minecraft.entity.ai.brain.task.OpenDoorsTask;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.concurrent.locks.ReentrantLock;

@Mixin(OpenDoorsTask.class)
public abstract class OpenDoorsTaskMixin {
    @Unique
    private static final ReentrantLock lock = new ReentrantLock();

    @WrapMethod(method = "hasReached")
    private static boolean hasReached(Brain<?> brain, BlockPos pos, Operation<Boolean> original) {
        synchronized (lock) {
            return original.call(brain, pos);
        }
    }
}