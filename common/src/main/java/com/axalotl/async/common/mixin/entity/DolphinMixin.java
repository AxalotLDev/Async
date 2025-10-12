package com.axalotl.async.common.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.AgeableWaterCreature;
import net.minecraft.world.entity.animal.Dolphin;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Dolphin.class)
public abstract class DolphinMixin extends AgeableWaterCreature {

    @Unique
    private static final Object async$lock = new Object();

    protected DolphinMixin(EntityType<? extends AgeableWaterCreature> entityType, Level level) {
        super(entityType, level);
    }

    @WrapMethod(method = "pickUpItem")
    private void pickUpItem(ServerLevel level, ItemEntity entity, Operation<Void> original) {
        synchronized (async$lock) {
            if (!entity.isRemoved()) {
                original.call(level, entity);
            }
        }
    }
}