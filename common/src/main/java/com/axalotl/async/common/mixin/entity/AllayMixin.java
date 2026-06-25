package com.axalotl.async.common.mixin.entity;

import com.axalotl.async.api.utils.SyncItemPickup;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Allay.class)
public abstract class AllayMixin {

    @WrapMethod(method = "pickUpItem")
    private void pickUpItem(ItemEntity entity, Operation<Void> original) {
        SyncItemPickup.wrap(entity, () -> original.call(entity));
    }
}
