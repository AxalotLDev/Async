package com.axalotl.async.common.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Mob.class)
public abstract class MobMixin {

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
    private void setBodyArmor(ItemStack stack, Operation<Void> original) {
        synchronized (async$lock) {
            original.call(stack);
        }
    }

    @WrapMethod(method = "convertTo(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/world/entity/ConversionParams;Lnet/minecraft/world/entity/EntitySpawnReason;Lnet/minecraft/world/entity/ConversionParams$AfterConversion;)Lnet/minecraft/world/entity/Mob;")
    private <T extends Mob> @Nullable T convertTo(
            EntityType<T> entityType,
            ConversionParams conversionParams,
            EntitySpawnReason spawnReason,
            ConversionParams.AfterConversion<T> afterConversion,
            Operation<T> original
    ) {
        synchronized (async$lock) {
            if (((Mob)(Object)this).isRemoved()) {
                return null;
            }
            return original.call(entityType, conversionParams, spawnReason, afterConversion);
        }
    }
}