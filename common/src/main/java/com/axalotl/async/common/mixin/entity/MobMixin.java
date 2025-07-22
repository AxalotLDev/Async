package com.axalotl.async.common.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Mob.class)
public class MobMixin {

    @Unique
    private static final Object async$lock = new Object();

    @WrapMethod(method = "equipItemIfPossible")
    private ItemStack tryEquip(ServerLevel level, ItemStack stack, Operation<ItemStack> original) {
        synchronized (async$lock) {
            return original.call(level, stack);
        }
    }

    @WrapMethod(method = "pickUpItem")
    private void pickUpItem(ServerLevel level, ItemEntity entity, Operation<Void> original) {
        synchronized (async$lock) {
            original.call(level, entity);
        }
    }

    @WrapMethod(method = "setItemSlotAndDropWhenKilled")
    private void equipLootStack(EquipmentSlot slot, ItemStack stack, Operation<Void> original) {
        synchronized (async$lock) {
            original.call(slot, stack);
        }
    }

    @WrapMethod(method = "setBodyArmorItem")
    private void equipLootStack(ItemStack stack, Operation<Void> original) {
        synchronized (async$lock) {
            original.call(stack);
        }
    }
}