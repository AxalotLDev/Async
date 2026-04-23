package com.axalotl.async.common.mixin.server;

import com.axalotl.async.api.fastutil.Long2ByteConcurrentHashMap;
import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import net.minecraft.server.level.DistanceManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.server.level.DistanceManager$FixedPlayerDistanceChunkTracker")
public class FixedPlayerDistanceChunkTrackerMixin {

    @Shadow
    @Final
    @Mutable
    protected Long2ByteMap chunks;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void async$replaceConcurrentChunks(DistanceManager maxDistance, int par2, CallbackInfo ci) {
        byte defaultVal = this.chunks.defaultReturnValue();
        this.chunks = new Long2ByteConcurrentHashMap();
        this.chunks.defaultReturnValue(defaultVal);
    }
}