package com.axalotl.async.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.concurrent.locks.ReentrantLock;

@Mixin(MobEntity.class)
public class MobEntityMixin {
    @Unique
    private static final ReentrantLock lock = new ReentrantLock();

    @WrapMethod(method = "tryEquip")
    private ItemStack tryEquip(ServerWorld world, ItemStack stack, Operation<ItemStack> original) {
        synchronized (lock) {
            return original.call(world, stack);
        }
    }

    @WrapMethod(method = "equipStack")
    private void equipStack(EquipmentSlot slot, ItemStack stack, Operation<Void> original) {
        synchronized (lock) {
            original.call(slot, stack);
        }
    }

    @WrapMethod(method = "equipLootStack")
    private void equipLootStack(EquipmentSlot slot, ItemStack stack, Operation<Void> original) {
        synchronized (lock) {
            original.call(slot, stack);
        }
    }

    @WrapMethod(method = "equipBodyArmor")
    private void equipLootStack(ItemStack stack, Operation<Void> original) {
        synchronized (lock) {
            original.call(stack);
        }
    }
}
