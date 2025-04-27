package com.axalotl.async.common.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.InteractWithDoor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.concurrent.locks.ReentrantLock;

@Mixin(InteractWithDoor.class)
public abstract class OpenDoorsTaskMixin {

    @Unique
    private static final ReentrantLock async$lock = new ReentrantLock();

    @WrapMethod(method = "isMobComingThroughDoor")
    private static boolean hasReached(Brain<?> brain, BlockPos pos, Operation<Boolean> original) {
        synchronized (async$lock) {
            return original.call(brain, pos);
        }
    }
}