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
    private static final Object lock = new Object();

    @WrapMethod(method = "equipItemIfPossible")
    private ItemStack tryEquip(ServerLevel level, ItemStack itemStack, Operation<ItemStack> original) {
        synchronized (lock) {
            return original.call(level, itemStack);
        }
    }

    @WrapMethod(method = "pickUpItem")
    private void pickUpItem(ServerLevel level, ItemEntity entity, Operation<Void> original) {
        synchronized (lock) {
            original.call(level, entity);
        }
    }

    @WrapMethod(method = "setItemSlotAndDropWhenKilled")
    private void equipLootStack(EquipmentSlot slot, ItemStack itemStack, Operation<Void> original) {
        synchronized (lock) {
            original.call(slot, itemStack);
        }
    }

    @WrapMethod(method = "setBodyArmorItem")
    private void equipLootStack(ItemStack item, Operation<Void> original) {
        synchronized (lock) {
            original.call(item);
        }
    }
}