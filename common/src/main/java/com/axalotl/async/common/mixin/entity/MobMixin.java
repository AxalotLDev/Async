package com.axalotl.async.common.mixin.entity;

import com.axalotl.async.api.utils.SyncItemPickup;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Mob.class)
public class MobMixin {

    @WrapMethod(method = "equipItemIfPossible")
    private ItemStack tryEquip(ItemStack itemStack, Operation<ItemStack> original) {
        synchronized (this) {
            return original.call(itemStack);
        }
    }

    @WrapMethod(method = "pickUpItem")
    private void pickUpItem(ItemEntity entity, Operation<Void> original) {
        SyncItemPickup.wrap(entity, () -> original.call(entity));
    }

    @WrapMethod(method = "setItemSlot")
    private void equipStack(EquipmentSlot slot, ItemStack itemStack, Operation<Void> original) {
        synchronized (this) {
            original.call(slot, itemStack);
        }
    }

    @WrapMethod(method = "setItemSlotAndDropWhenKilled")
    private void equipLootStack(EquipmentSlot slot, ItemStack itemStack, Operation<Void> original) {
        synchronized (this) {
            original.call(slot, itemStack);
        }
    }

    @WrapMethod(method = "setBodyArmorItem")
    private void equipLootStack(ItemStack item, Operation<Void> original) {
        synchronized (this) {
            original.call(item);
        }
    }
}
