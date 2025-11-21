package com.axalotl.async.neoforge.mixin.lithium;

import com.axalotl.async.common.parallelised.utils.ChunkLoadTricks;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(ChunkLoadTricks.class)
public class LithiumChunkLoadTricksMixin {

    /**
     * @author 2No2Name
     * @reason Apply NeoForge's chunk loading trick
     */
    @Overwrite
    public static ChunkAccess tryRetrieveCurrentlyLoading(ChunkHolder holder) {
        if (holder != null) {
            return holder.currentlyLoading;
        }
        return null;
    }
}