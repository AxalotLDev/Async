package com.axalotl.async.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.block.entity.Hopper;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(HopperBlockEntity.class)
public abstract class HopperBlockEntityMixin {

    @WrapMethod(method = "extract(Lnet/minecraft/world/World;Lnet/minecraft/block/entity/Hopper;)Z")
    private static synchronized boolean onExtract(World world, Hopper hopper, Operation<Boolean> original) {
        return original.call(world, hopper);
    }

    @WrapMethod(method = "extract(Lnet/minecraft/inventory/Inventory;Lnet/minecraft/entity/ItemEntity;)Z")
    private static synchronized boolean onExtract(Inventory inventory, ItemEntity itemEntity, Operation<Boolean> original) {
        return original.call(inventory, itemEntity);
    }

    @WrapMethod(method = "extract(Lnet/minecraft/block/entity/Hopper;Lnet/minecraft/inventory/Inventory;ILnet/minecraft/util/math/Direction;)Z")
    private static synchronized boolean onExtract(Hopper hopper, Inventory inventory, int slot, Direction side, Operation<Boolean> original) {
        return original.call(hopper, inventory, slot, side);
    }

    @WrapMethod(method = "canExtract")
    private static synchronized boolean canExtract(Inventory hopperInventory, Inventory fromInventory, ItemStack stack, int slot, Direction facing, Operation<Boolean> original) {
        return original.call(hopperInventory, fromInventory, stack, slot, facing);
    }
}
