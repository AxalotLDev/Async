package com.axalotl.async.common.mixin.entity;

import com.axalotl.async.common.config.AsyncConfig;
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
    private static final ReentrantLock async$lock = new ReentrantLock();

    @WrapMethod(method = "move")
    private void move(MoverType type, Vec3 movement, Operation<Void> original) {
        if (AsyncConfig.enableEntityMoveSync.getValue()) {
            synchronized (async$lock) {
                original.call(type, movement);
            }
        } else {
            original.call(type, movement);
        }
    }

    @WrapMethod(method = "setRemoved")
    private void setRemoved(Entity.RemovalReason reason, Operation<Void> original) {
        synchronized (async$lock) {
            original.call(reason);
        }
    }

    @WrapMethod(method = "getInBlockState")
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
        synchronized (async$lock) {
            original.call(passenger);
        }
    }

    @WrapMethod(method = "removePassenger")
    private void removePassenger(Entity passenger, Operation<Void> original) {
        synchronized (async$lock) {
            original.call(passenger);
        }
    }
}