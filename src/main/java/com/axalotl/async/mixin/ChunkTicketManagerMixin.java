package com.axalotl.async.mixin;

import com.axalotl.async.parallelised.ConcurrentCollections;
import com.axalotl.async.parallelised.fastutil.ConcurrentLongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.server.world.*;
import org.spongepowered.asm.mixin.*;

import java.util.Set;

@Mixin(ChunkTicketManager.class)
public abstract class ChunkTicketManagerMixin {

    @Shadow
    @Final
    @Mutable
    Set<ChunkHolder> chunkHoldersWithPendingUpdates = ConcurrentCollections.newHashSet();

    @Shadow
    @Final
    @Mutable
    LongSet freshPlayerTicketPositions = new ConcurrentLongLinkedOpenHashSet();
}
