package com.axalotl.async.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerEntityManager;
import net.minecraft.world.entity.EntityLike;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ServerEntityManager.Listener.class)
public abstract class ServerEntityManagerMixin<T extends EntityLike> implements AutoCloseable {
    @WrapMethod(method = "updateEntityPosition")
    private void updateEntityPosition(Operation<Void> original) {
        try {
            original.call();
        } catch (Throwable ignored) {
        }
    }

    @WrapMethod(method = "remove")
    private void remove(Entity.RemovalReason reason, Operation<Void> original) {
        try {
            original.call(reason);
        } catch (Throwable ignored) {
        }
    }
}
