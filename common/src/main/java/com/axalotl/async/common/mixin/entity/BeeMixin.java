package com.axalotl.async.common.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.animal.Bee;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Bee.class)
public class BeeMixin {
    @Unique
    private static final Object async$lock = new Object();

    @WrapMethod(method = "wantsToEnterHive")
    private boolean loot(Operation<Boolean> original) {
        synchronized (async$lock) {
            return original.call();
        }
    }
}