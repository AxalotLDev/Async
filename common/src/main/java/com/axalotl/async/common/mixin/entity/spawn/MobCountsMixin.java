package com.axalotl.async.common.mixin.entity.spawn;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import net.minecraft.world.level.LocalMobCapCalculator;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalMobCapCalculator.MobCounts.class)
public class MobCountsMixin {

    @Shadow
    @Final
    @Mutable
    private Object2IntMap<?> counts;

    @SuppressWarnings("unchecked")
    @Inject(method = "<init>", at = @At("RETURN"))
    private void synchronizeCounts(CallbackInfo ci) {
        this.counts = Object2IntMaps.synchronize((Object2IntMap<Object>) this.counts);
    }
}