package com.axalotl.async.common.mixin.compat.sable;

import com.axalotl.async.common.compat.SableCompatibility;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.ryanhcode.sable.SableCommonEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Entities ticked on worker threads change blocks concurrently. Sable feeds every server block
 * change into unsynchronized plot bookkeeping and the level's native physics scene.
 */
@Mixin(value = SableCommonEvents.class, remap = false)
public abstract class SableCommonEventsMixin {
    @WrapMethod(method = "handleBlockChange")
    private static void async$serializeBlockChange(
            ServerLevel level,
            LevelChunk chunk,
            int x,
            int y,
            int z,
            BlockState oldState,
            BlockState newState,
            Operation<Void> original
    ) {
        SableCompatibility.lockSharedState();
        try {
            original.call(level, chunk, x, y, z, oldState, newState);
        } finally {
            SableCompatibility.unlockSharedState();
        }
    }
}
