package com.axalotl.async.fabric.mixin.sodium;

import com.axalotl.async.common.ParallelProcessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;

@Mixin(value = SodiumWorldRenderer.class)
public class SodiumWorldRendererMixin {
    @Inject(method = "setupTerrain", at = @At("HEAD"))
    private void onSetupTerrain(CallbackInfo ci) {
        if (ParallelProcessor.getServer() != null)
            ParallelProcessor.postEntityTick();
    }
}
