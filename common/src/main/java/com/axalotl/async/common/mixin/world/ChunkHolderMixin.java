package com.axalotl.async.common.mixin.world;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = ChunkHolder.class, priority = 1500)
public class ChunkHolderMixin {

    @Unique
    private static final Object lock = new Object();

    @WrapMethod(method = "broadcastChanges")
    private void wrapBroadcastChanges(LevelChunk chunk, Operation<Void> original) {
        synchronized (lock) {
            original.call(chunk);
        }
    }

    @WrapMethod(method = "blockChanged")
    private boolean wrapBlockChanged(BlockPos pos, Operation<Boolean> original) {
        synchronized (lock) {
            return original.call(pos);
        }
    }

    @WrapMethod(method = "sectionLightChanged")
    private boolean wrapSectionLightChanged(LightLayer lightLayer, int y, Operation<Boolean> original) {
        synchronized (lock) {
            return original.call(lightLayer, y);
        }
    }
}