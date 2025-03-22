package com.axalotl.async.mixin.world;

import com.axalotl.async.parallelised.fastutil.Int2ObjectConcurrentHashMap;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.gameevent.GameEventListenerRegistry;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelChunk.class)
public abstract class WorldChunkMixin {
    @Mutable
    @Shadow
    @Final
    private Int2ObjectMap<GameEventListenerRegistry> gameEventListenerRegistrySections;

    @Inject(method = "<init>*", at = @At("RETURN"))
    private void init(CallbackInfo ci) {
        gameEventListenerRegistrySections = new Int2ObjectConcurrentHashMap<>();
    }

    @WrapMethod(method = "getListenerRegistry")
    private synchronized GameEventListenerRegistry getGameEventDispatcher(int ySectionCoord, Operation<GameEventListenerRegistry> original) {
        return original.call(ySectionCoord);
    }
}