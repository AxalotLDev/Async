package com.axalotl.async.common.mixin.server;

import com.axalotl.async.common.ParallelProcessor;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerEntity;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = ServerEntity.class, priority = 1500)
public class ServerEntityMixin {

    @WrapOperation(
            method = "sendPairingData",
            at = @At(value = "INVOKE", target = "Lorg/slf4j/Logger;warn(Ljava/lang/String;Ljava/lang/Object;)V")
    )
    private void async$suppressAsyncRemovedWarn(Logger logger, String message, Object arg, Operation<Void> original) {
        if (!ParallelProcessor.isServerExecutionThread()) {
            original.call(logger, message, arg);
        }
    }
}