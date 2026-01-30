package com.axalotl.async.common.mixin.spawn;

import com.axalotl.async.common.ParallelProcessor;
import com.axalotl.async.common.config.AsyncConfig;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevelAccessor.class)
public interface ServerLevelAccessorMixin {

    @Inject(method = "addFreshEntityWithPassengers", at = @At("HEAD"), cancellable = true)
    default void async$syncAddFreshEntityWithPassengers(Entity entity, CallbackInfo ci) {
        if (AsyncConfig.disabled || !AsyncConfig.enableAsyncSpawn) {
            return;
        }

        ci.cancel();

        synchronized (ParallelProcessor.getEntityAddLock()) {
            entity.getSelfAndPassengers().forEach(e -> ((ServerLevelAccessor) this).addFreshEntity(e));
        }
    }
}