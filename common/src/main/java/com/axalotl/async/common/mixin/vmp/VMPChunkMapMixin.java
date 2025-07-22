package com.axalotl.async.common.mixin.vmp;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ChunkMap.class)
public class VMPChunkMapMixin {
    @WrapMethod(method = "addEntity")
    private synchronized void loadEntity(Entity entity, Operation<Void> original) {
        original.call(entity);
    }

    @WrapMethod(method = "removeEntity")
    private synchronized void unloadEntity(Entity entity, Operation<Void> original) {
        original.call(entity);
    }

    @WrapMethod(method = "tick()V")
    private synchronized void tickEntityMovement(Operation<Void> original) {
        original.call();
    }
}