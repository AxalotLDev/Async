package com.axalotl.async.common.mixin.sodium;

import com.axalotl.async.common.ParallelProcessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer")
public class SodiumWorldRendererMixin {
    @Inject(method = "setupTerrain", at = @At("HEAD"))
    private void onSetupTerrain(CallbackInfo ci) {
        if (ParallelProcessor.getServer() != null)
            ParallelProcessor.postEntityTick();
    }
}
