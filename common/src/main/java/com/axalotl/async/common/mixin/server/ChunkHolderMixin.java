package com.axalotl.async.common.mixin.server;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ChunkHolder.class)
public class ChunkHolderMixin {

    @Unique
    private final Object async$lock = new Object();

    @WrapMethod(method = "broadcastChanges")
    private void wrapBroadcastChanges(LevelChunk chunk, Operation<Void> original) {
        synchronized (async$lock) {
            original.call(chunk);
        }
    }

    @WrapMethod(method = "blockChanged")
    private boolean wrapBlockChanged(BlockPos pos, Operation<Boolean> original) {
        synchronized (async$lock) {
            return original.call(pos);
        }
    }
}