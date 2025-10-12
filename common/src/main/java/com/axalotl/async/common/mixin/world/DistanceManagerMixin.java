package com.axalotl.async.common.mixin.world;

import com.axalotl.async.common.parallelised.ConcurrentCollections;
import com.axalotl.async.common.parallelised.fastutil.ConcurrentLongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.DistanceManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Set;

@Mixin(DistanceManager.class)
public abstract class DistanceManagerMixin {

    @Shadow
    @Final
    @Mutable
    protected Set<ChunkHolder> chunksToUpdateFutures = ConcurrentCollections.newHashSet();

    @Shadow
    @Final
    @Mutable
    LongSet ticketsToRelease = new ConcurrentLongLinkedOpenHashSet();
}