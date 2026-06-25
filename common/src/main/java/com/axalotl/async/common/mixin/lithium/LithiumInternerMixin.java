package com.axalotl.async.common.mixin.lithium;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.caffeinemc.mods.lithium.common.util.deduplication.LithiumInterner;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = LithiumInterner.class, remap = false)
public class LithiumInternerMixin<T> {

    @WrapMethod(method = "getCanonical")
    private synchronized <S extends T> S wrapGetCanonical(S value, Operation<S> original) {
        return original.call(value);
    }

    @WrapMethod(method = "deleteCanonical")
    private synchronized void wrapDeleteCanonical(Object value, Operation<Void> original) {
        original.call(value);
    }
}