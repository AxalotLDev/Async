package com.axalotl.async.common.mixin.entity;

import com.axalotl.async.api.utils.SyncItemPickup;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.raid.Raider;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Raider.class)
public class RaiderMixin {

    @WrapMethod(method = "pickUpItem")
    private void pickUpItem(ItemEntity entity, Operation<Void> original) {
        SyncItemPickup.wrap(entity, () -> original.call(entity));
    }
}
