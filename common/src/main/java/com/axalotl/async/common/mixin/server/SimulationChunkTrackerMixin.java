package com.axalotl.async.common.mixin.server;

import com.axalotl.async.common.parallelised.fastutil.Long2ByteConcurrentHashMap;
import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.server.level.SimulationChunkTracker;

@Mixin(SimulationChunkTracker.class)
public class SimulationChunkTrackerMixin {

    @Shadow
    @Final
    @Mutable
    protected Long2ByteMap chunks;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void async$replaceConcurrentChunks(CallbackInfo ci) {
        byte defaultVal = this.chunks.defaultReturnValue();
        this.chunks = new Long2ByteConcurrentHashMap();
        this.chunks.defaultReturnValue(defaultVal);
    }
}