package com.axalotl.async.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Entity.class)
public class EntityMixin {
    @WrapMethod(method = "isInsideBubbleColumn")
    private synchronized boolean isInsideBubbleColumn(Operation<Boolean> original) {
        try {
            return original.call();
        } catch (Exception e) {
            return false;
        }
    }
}
