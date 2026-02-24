package com.axalotl.async.common.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.vehicle.MinecartHopper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(MinecartHopper.class)
public class MinecartHopperMixin {
    @Unique
    private static final Object async$lock = new Object();

    @WrapMethod(method = "suckInItems")
    private boolean suckInItems(Operation<Boolean> original) {
        synchronized (async$lock) {
            return original.call();
        }
    }
}