package com.axalotl.async.common.mixin.c2me;

import com.axalotl.async.api.fastutil.Long2IntConcurrentHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.ishland.c2me.rewrites.chunksystem.common.TheChunkSystem", remap = false)
public class TheChunkSystemMixin {

    @Mutable
    @Shadow
    private Long2IntMap managedTickets;

    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void async$replaceManagedTickets(CallbackInfo ci) {
        int defaultVal = this.managedTickets.defaultReturnValue();
        Long2IntConcurrentHashMap concurrent = new Long2IntConcurrentHashMap();
        concurrent.defaultReturnValue(defaultVal);
        concurrent.putAll(this.managedTickets);
        this.managedTickets = concurrent;
    }
}