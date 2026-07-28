package com.axalotl.async.common.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.vehicle.minecart.MinecartHopper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.Hopper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(MinecartHopper.class)
public class MinecartHopperMixin {
    @Unique
    private static final Object[] POSITION_LOCKS = new Object[256];

    static {
        for (int i = 0; i < POSITION_LOCKS.length; i++) {
            POSITION_LOCKS[i] = new Object();
        }
    }

    @Unique
    private static Object lockForPos(BlockPos pos) {
        return POSITION_LOCKS[pos.hashCode() & 0xFF];
    }

    @WrapOperation(
            method = "suckInItems",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/entity/HopperBlockEntity;suckInItems(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/block/entity/Hopper;)Z"
            )
    )
    private boolean wrapSuckInItems(Level level, Hopper hopper, Operation<Boolean> original) {
        BlockPos blockPos = BlockPos.containing(hopper.getLevelX(), hopper.getLevelY() + (double) 1.0F, hopper.getLevelZ());
        synchronized (lockForPos(blockPos)) {
            return original.call(level, hopper);
        }
    }

    @WrapOperation(
            method = "suckInItems",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/entity/HopperBlockEntity;addItem(Lnet/minecraft/world/Container;Lnet/minecraft/world/entity/item/ItemEntity;)Z"
            )
    )
    private boolean wrapItemPickup(Container container, ItemEntity entity, Operation<Boolean> original) {
        synchronized (entity) {
            if (!entity.isAlive()) return false;
            synchronized (container) {
                return original.call(container, entity);
            }
        }
    }
}