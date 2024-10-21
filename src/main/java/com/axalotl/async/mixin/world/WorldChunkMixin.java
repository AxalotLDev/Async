package com.axalotl.async.mixin.world;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.event.listener.GameEventDispatcher;
import net.minecraft.world.event.listener.SimpleGameEventDispatcher;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(WorldChunk.class)
public abstract class WorldChunkMixin {
    @Shadow
    protected abstract void removeGameEventDispatcher(int ySectionCoord);

    @Shadow public abstract World getWorld();

    @Shadow @Final
    World world;

    @WrapMethod(method = "getGameEventDispatcher")
    private synchronized GameEventDispatcher getGameEventDispatcher(int ySectionCoord, Operation<GameEventDispatcher> original) {
        GameEventDispatcher dispatcher = original.call(ySectionCoord);
        if (dispatcher == null && this.world instanceof ServerWorld serverWorld) {
            return new SimpleGameEventDispatcher(serverWorld, ySectionCoord, this::removeGameEventDispatcher);
        }
        return dispatcher;
    }
}
