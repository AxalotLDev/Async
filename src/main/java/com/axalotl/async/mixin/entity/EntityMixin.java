package com.axalotl.async.mixin.entity;

import com.google.common.collect.ImmutableList;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemConvertible;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

@Mixin(Entity.class)
public abstract class EntityMixin {

    @Shadow
    volatile private ImmutableList<Entity> passengerList = ImmutableList.of();
    @Unique
    private static final ReentrantLock lock = new ReentrantLock();

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

    @WrapMethod(method = "setRemoved")
    private void setRemoved(Entity.RemovalReason reason, Operation<Void> original) {
        synchronized (lock) {
            original.call(reason);
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

    @WrapMethod(method = "streamIntoPassengers")
    private Stream<Entity> streamIntoPassengers(Operation<Stream<Entity>> original) {
        synchronized (lock) {
            return original.call();
        }
    }

    @WrapMethod(method = "removePassenger")
    private void removePassenger(Entity passenger, Operation<Void> original) {
        synchronized (lock) {
            original.call(passenger);
        }
    }
}