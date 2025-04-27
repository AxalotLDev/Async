package com.axalotl.async.mixin.entity;

import com.axalotl.async.config.AsyncConfig;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.item.ItemConvertible;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.concurrent.locks.ReentrantLock;

@Mixin(Entity.class)
public abstract class EntityMixin {
    @Unique
    private static final ReentrantLock lock = new ReentrantLock();

    @WrapMethod(method = "move")
    private void move(MovementType type, Vec3d movement, Operation<Void> original) {
        if (AsyncConfig.enableEntityMoveSync) {
            synchronized (lock) {
                original.call(type, movement);
            }
        } else {
            original.call(type, movement);
        }
    }

    @WrapMethod(method = "tickBlockCollision()V")
    private void tickBlockCollision(Operation<Void> original) {
        if (AsyncConfig.enableEntityMoveSync) {
            synchronized (lock) {
                original.call();
            }
        } else {
            original.call();
        }
    }

    @WrapMethod(method = "tickBlockCollision(Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/util/math/Vec3d;)V")
    private void tickBlockCollision(Vec3d lastRenderPos, Vec3d pos, Operation<Void> original) {
        if (AsyncConfig.enableEntityMoveSync) {
            synchronized (lock) {
                original.call(lastRenderPos, pos);
            }
        } else {
            original.call(lastRenderPos, pos);
        }
    }

    @WrapMethod(method = "setRemoved")
    private void setRemoved(Entity.RemovalReason reason, Operation<Void> original) {
        synchronized (lock) {
            original.call(reason);
        }
    }

    @WrapMethod(method = "dropStack(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/item/ItemStack;F)Lnet/minecraft/entity/ItemEntity;")
    private ItemEntity dropStack(ServerWorld world, ItemStack stack, float yOffset, Operation<ItemEntity> original) {
        synchronized (lock) {
            return original.call(world, stack, yOffset);
        }
    }

    @WrapMethod(method = "dropItem(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/item/ItemConvertible;I)Lnet/minecraft/entity/ItemEntity;")
    private ItemEntity dropStack(ServerWorld world, ItemConvertible item, int offsetY, Operation<ItemEntity> original) {
        synchronized (lock) {
            return original.call(world, item, offsetY);
        }
    }

    @WrapMethod(method = "getBlockStateAtPos")
    private BlockState getBlockStateAtPos(Operation<BlockState> original) {
        BlockState blockState = original.call();
        if (blockState != null) {
            return blockState;
        } else {
            return Blocks.AIR.getDefaultState();
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