package com.axalotl.async.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.event.listener.GameEventDispatcher;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(WorldChunk.class)
public abstract class WorldChunkMixin {
    @WrapMethod(method = "getGameEventDispatcher")
    private synchronized GameEventDispatcher injectGetGameEventDispatcher(int ySectionCoord, Operation<GameEventDispatcher> original) {
        return original.call(ySectionCoord);
    }
}
