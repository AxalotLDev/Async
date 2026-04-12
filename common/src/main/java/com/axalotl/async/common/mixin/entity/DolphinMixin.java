package com.axalotl.async.common.mixin.entity;

import com.axalotl.async.api.annotation.SyncItemPickup;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.AgeableWaterCreature;
import net.minecraft.world.entity.animal.dolphin.Dolphin;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Dolphin.class)
public abstract class DolphinMixin extends AgeableWaterCreature {

    protected DolphinMixin(EntityType<? extends AgeableWaterCreature> entityType, Level level) {
        super(entityType, level);
    }

    @WrapMethod(method = "pickUpItem")
    @SyncItemPickup
    private void pickUpItem(ServerLevel level, ItemEntity entity, Operation<Void> original) {
        original.call(level, entity);
    }
}
