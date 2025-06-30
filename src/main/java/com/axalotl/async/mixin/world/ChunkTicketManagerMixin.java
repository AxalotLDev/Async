package com.axalotl.async.mixin.world;

import com.axalotl.async.parallelised.fastutil.ConcurrentLongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.server.world.*;
import org.spongepowered.asm.mixin.*;

@Mixin(ChunkTicketManager.class)
public abstract class ChunkTicketManagerMixin {
    @Shadow
    private final LongSet forcedChunks = new ConcurrentLongLinkedOpenHashSet();
}