package com.axalotl.async.common.mixin.entity.movement;

import com.axalotl.async.common.ParallelProcessor;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.Path;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.concurrent.locks.ReentrantLock;

@Mixin(value = PathNavigation.class)
public abstract class PathNavigationMixin {

    @Unique
    private final ReentrantLock navigationLock = new ReentrantLock();

    @WrapMethod(method = "tick()V")
    private void tick(Operation<Void> original) {
        ParallelProcessor.lockCooperatively(this.navigationLock);
        try {
            original.call();
        } finally {
            this.navigationLock.unlock();
        }
    }

    @WrapMethod(method = "moveTo(DDDD)Z")
    private boolean moveTo(double x, double y, double z, double speedModifier, Operation<Boolean> original) {
        ParallelProcessor.lockCooperatively(this.navigationLock);
        try {
            return original.call(x, y, z, speedModifier);
        } finally {
            this.navigationLock.unlock();
        }
    }

    @WrapMethod(method = "moveTo(DDDID)Z")
    private boolean moveToWithRange(double x, double y, double z, int reachRange, double speedModifier, Operation<Boolean> original) {
        ParallelProcessor.lockCooperatively(this.navigationLock);
        try {
            return original.call(x, y, z, reachRange, speedModifier);
        } finally {
            this.navigationLock.unlock();
        }
    }

    @WrapMethod(method = "moveTo(Lnet/minecraft/world/entity/Entity;D)Z")
    private boolean moveToEntity(Entity target, double speedModifier, Operation<Boolean> original) {
        ParallelProcessor.lockCooperatively(this.navigationLock);
        try {
            return original.call(target, speedModifier);
        } finally {
            this.navigationLock.unlock();
        }
    }

    @WrapMethod(method = "moveTo(Lnet/minecraft/world/level/pathfinder/Path;D)Z")
    private boolean moveToPath(Path newPath, double speedModifier, Operation<Boolean> original) {
        ParallelProcessor.lockCooperatively(this.navigationLock);
        try {
            return original.call(newPath, speedModifier);
        } finally {
            this.navigationLock.unlock();
        }
    }

    @WrapMethod(method = "recomputePath()V")
    private void recomputePath(Operation<Void> original) {
        ParallelProcessor.lockCooperatively(this.navigationLock);
        try {
            original.call();
        } finally {
            this.navigationLock.unlock();
        }
    }

    @WrapMethod(method = "stop()V")
    private void stop(Operation<Void> original) {
        ParallelProcessor.lockCooperatively(this.navigationLock);
        try {
            original.call();
        } finally {
            this.navigationLock.unlock();
        }
    }

    @WrapMethod(method = "shouldRecomputePath(Lnet/minecraft/core/BlockPos;)Z")
    private boolean shouldRecomputePath(BlockPos pos, Operation<Boolean> original) {
        ParallelProcessor.lockCooperatively(this.navigationLock);
        try {
            return original.call(pos);
        } finally {
            this.navigationLock.unlock();
        }
    }

    @WrapMethod(method = "setSpeedModifier(D)V")
    private void setSpeedModifier(double speedModifier, Operation<Void> original) {
        ParallelProcessor.lockCooperatively(this.navigationLock);
        try {
            original.call(speedModifier);
        } finally {
            this.navigationLock.unlock();
        }
    }

    @WrapMethod(method = "isDone()Z")
    private boolean isDone(Operation<Boolean> original) {
        ParallelProcessor.lockCooperatively(this.navigationLock);
        try {
            return original.call();
        } finally {
            this.navigationLock.unlock();
        }
    }

    @WrapMethod(method = "isInProgress()Z")
    private boolean isInProgress(Operation<Boolean> original) {
        ParallelProcessor.lockCooperatively(this.navigationLock);
        try {
            return original.call();
        } finally {
            this.navigationLock.unlock();
        }
    }

    @WrapMethod(method = "getPath()Lnet/minecraft/world/level/pathfinder/Path;")
    private Path getPath(Operation<Path> original) {
        ParallelProcessor.lockCooperatively(this.navigationLock);
        try {
            return original.call();
        } finally {
            this.navigationLock.unlock();
        }
    }
}
