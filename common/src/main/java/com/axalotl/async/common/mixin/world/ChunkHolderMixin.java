package com.axalotl.async.common.mixin.world;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = ChunkHolder.class, priority = 1500)
public class ChunkHolderMixin {

    @WrapMethod(method = "broadcastChanges")
    private void wrapBroadcastChanges(LevelChunk chunk, Operation<Void> original) {
        synchronized (this) {
            original.call(chunk);
        }
    }

    @WrapMethod(method = "blockChanged")
    private void wrapBlockChanged(BlockPos pos, Operation<Void> original) {
        synchronized (this) {
            original.call(pos);
        }
    }

    @WrapMethod(method = "sectionLightChanged")
    private void wrapSectionLightChanged(LightLayer layer, int chunkY, Operation<Void> original) {
        synchronized (this) {
            original.call(layer, chunkY);
        }
    }
}
