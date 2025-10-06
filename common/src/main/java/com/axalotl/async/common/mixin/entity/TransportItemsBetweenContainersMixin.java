package com.axalotl.async.common.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.Container;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.TransportItemsBetweenContainers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(TransportItemsBetweenContainers.class)
public class TransportItemsBetweenContainersMixin {

    @Unique
    private static final Object async$lock = new Object();

    @WrapMethod(method = "pickUpItems")
    private void pickUpItems(PathfinderMob mob, Container counter, Operation<Void> original) {
        synchronized (async$lock) {
            original.call(mob, counter);
        }
    }

    @WrapMethod(method = "putDownItem")
    private void putDownItem(PathfinderMob mob, Container counter, Operation<Void> original) {
        synchronized (async$lock) {
            original.call(mob, counter);
        }
    }
}