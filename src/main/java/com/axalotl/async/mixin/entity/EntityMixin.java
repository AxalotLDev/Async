package com.axalotl.async.mixin.entity;

import com.axalotl.async.config.AsyncConfig;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.concurrent.locks.ReentrantLock;

@Mixin(Entity.class)
public abstract class EntityMixin {
    @Unique
    private static final ReentrantLock lock = new ReentrantLock();

    @WrapMethod(method = "move")
    private synchronized void move(MoverType type, Vec3 movement, Operation<Void> original) {
        if (AsyncConfig.enableEntityMoveSync) {
            synchronized (lock) {
                original.call(type, movement);
            }
        } else {
            original.call(type, movement);
        }
    }

    /**
     * tickBlockCollision is simpler on 1.20.1
     * @param original
     */
    @WrapMethod(method = "checkInsideBlocks()V")
    private void checkInsideBlocks(Operation<Void> original) {
        if (AsyncConfig.enableEntityMoveSync) {
            synchronized (lock) {
                original.call();
            }
        } else {
            original.call();
        }
    }

//    @WrapMethod(method = "tickBlockCollision(Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/util/math/Vec3d;)V")
//    private void tickBlockCollision(Vec3d lastRenderPos, Vec3d pos, Operation<Void> original) {
//        if (AsyncConfig.enableEntityMoveSync) {
//            synchronized (lock) {
//                original.call(lastRenderPos, pos);
//            }
//        } else {
//            original.call(lastRenderPos, pos);
//        }
//    }

    @WrapMethod(method = "setRemoved")
    private void setRemoved(Entity.RemovalReason reason, Operation<Void> original) {
        synchronized (lock) {
            original.call(reason);
        }
    }

    @WrapMethod(method = "getFeetBlockState")
    private BlockState getBlockStateAtPos(Operation<BlockState> original) {
        BlockState blockState = original.call();
        if (blockState != null) {
            return blockState;
        } else {
            return Blocks.AIR.defaultBlockState();
        }
    }

    @WrapMethod(method = "addPassenger")
    private void addPassenger(Entity passenger, Operation<Void> original) {
        synchronized (lock) {
            original.call(passenger);
        }
    }

    @WrapMethod(method = "removePassenger")
    private void removePassenger(Entity passenger, Operation<Void> original) {
        synchronized (lock) {
            original.call(passenger);
        }
    }
}