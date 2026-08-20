package com.axalotl.async.common.mixin.server;

import com.axalotl.async.common.ParallelProcessor;
import com.axalotl.async.common.utils.SynchronizationStats;
import com.axalotl.async.common.utils.TickStats;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BooleanSupplier;

@Mixin(value = MinecraftServer.class)
public class MinecraftServerMixin {

    @Redirect(method = "reloadResources", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;isSameThread()Z"))
    private boolean onServerExecutionThreadPatch(MinecraftServer minecraftServer) {
        return minecraftServer.isSameThread() || ParallelProcessor.isServerExecutionThread();
    }

    @Inject(method = "tickServer", at = @At("HEAD"))
    private void asyncStatsTickStart(BooleanSupplier haveTime, CallbackInfo callbackInfo) {
        SynchronizationStats.onServerTickStart();
    }

    @Inject(method = "tickServer", at = @At("TAIL"))
    private void asyncStatsTickEnd(BooleanSupplier haveTime, CallbackInfo callbackInfo) {
        TickStats.onServerTick();
        SynchronizationStats.onServerTickEnd();
    }
}
