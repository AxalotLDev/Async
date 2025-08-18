package com.axalotl.async.fabric.mixin.sodium;

import com.axalotl.async.common.config.AsyncConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;

@Mixin(value = SodiumWorldRenderer.class, remap = false)
public abstract class AsyncSodiumWorldRendererMixin {

    @Shadow
    @Mutable
    private boolean useEntityCulling;

    @Inject(method = "setupTerrain", at = @At("TAIL"))
    private void onSetupTerrainTail(CallbackInfo ci) {
        this.useEntityCulling = AsyncConfig.enableEntityCulling;
    }
}
