package com.axalotl.async.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.concurrent.locks.ReentrantLock;

@Mixin(Mob.class)
public class MobEntityMixin {
    @Unique
    private static final ReentrantLock lock = new ReentrantLock();

    @WrapMethod(method = "equipItemIfPossible")
    private ItemStack tryEquip(ItemStack stack, Operation<ItemStack> original) {
        synchronized (lock) {
            return original.call(stack);
        }
    }

    @WrapMethod(method = "setItemSlot")
    private void equipStack(EquipmentSlot slot, ItemStack stack, Operation<Void> original) {
        synchronized (lock) {
            original.call(slot, stack);
        }
    }

    @WrapMethod(method = "setItemSlotAndDropWhenKilled")
    private void equipLootStack(EquipmentSlot slot, ItemStack stack, Operation<Void> original) {
        synchronized (lock) {
            original.call(slot, stack);
        }
    }
}