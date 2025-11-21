package com.axalotl.async.common.parallelised.utils;

import net.minecraft.server.level.ChunkHolder;
import net.minecraft.world.level.chunk.ChunkAccess;

public class ChunkLoadTricks {

    public static ChunkAccess tryRetrieveCurrentlyLoading(ChunkHolder holder) {
        return null; //Overwritten by ChunkLoadTricksMixin, NeoForge only
    }
}