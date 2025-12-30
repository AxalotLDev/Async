package com.axalotl.async.common.mixin.world;

import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerChunkCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fix for concurrent modification of chunkHoldersToBroadcast set.
 *
 * Problem: ReferenceOpenHashSet is not thread-safe. In async environment,
 * blockChanged() can be called from entity tick threads while
 * broadcastChangedChunks() iterates on server thread.
 */
@Mixin(ServerChunkCache.class)
public class ServerChunkCacheMixin {

    @Shadow
    @Final
    @Mutable
    private Set<ChunkHolder> chunkHoldersToBroadcast;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void async$replaceWithConcurrentSet(CallbackInfo ci) {
        this.chunkHoldersToBroadcast = ConcurrentHashMap.newKeySet();
    }
}