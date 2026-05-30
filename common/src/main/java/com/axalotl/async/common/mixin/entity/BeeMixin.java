package com.axalotl.async.common.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.animal.bee.Bee;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Bee.class)
public class BeeMixin {

    @WrapMethod(method = "wantsToEnterHive")
    private boolean wantsToEnterHive(Operation<Boolean> original) {
        synchronized (this) {
            return original.call();
        }
    }
}