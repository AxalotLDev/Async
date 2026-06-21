package com.axalotl.async.common.mixin.entity;

import com.axalotl.async.api.utils.SyncItemPickup;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Mob.class)
public class MobMixin {

    @WrapMethod(method = "equipItemIfPossible")
    private ItemStack tryEquip(ServerLevel level, ItemStack itemStack, Operation<ItemStack> original) {
        synchronized (this) {
            return original.call(level, itemStack);
        }
    }

    @WrapMethod(method = "pickUpItem")
    private void pickUpItem(ServerLevel level, ItemEntity entity, Operation<Void> original) {
        SyncItemPickup.wrap(level, entity, () -> original.call(level, entity));
    }

    @WrapMethod(method = "setItemSlotAndDropWhenKilled")
    private void equipLootStack(EquipmentSlot slot, ItemStack itemStack, Operation<Void> original) {
        synchronized (this) {
            original.call(slot, itemStack);
        }
    }
}