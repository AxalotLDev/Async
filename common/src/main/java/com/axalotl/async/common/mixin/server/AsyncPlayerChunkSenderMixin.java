package com.axalotl.async.common.mixin.server;

import com.axalotl.async.common.ParallelProcessor;
import com.axalotl.async.common.config.AsyncConfig;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.PlayerChunkSender;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;
import java.util.BitSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.locks.LockSupport;

@Mixin(PlayerChunkSender.class)
public abstract class AsyncPlayerChunkSenderMixin {

    @Unique
    private static final Logger ASYNC_LOG = LoggerFactory.getLogger("Async-ChunkSend");

    @Unique
    private static final ThreadLocal<IdentityHashMap<LevelChunk, ClientboundLevelChunkWithLightPacket>> async$cache = ThreadLocal.withInitial(IdentityHashMap::new);

    @Inject(method = "sendNextChunks", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/PlayerChunkSender;collectChunksToSend(Lnet/minecraft/server/level/ChunkMap;Lnet/minecraft/world/level/ChunkPos;)Ljava/util/List;", shift = At.Shift.BY, by = 2))
    private void async$prebuildPackets(ServerPlayer player, CallbackInfo ci,
                                       @Local List<LevelChunk> chunksToSend) {
        if (AsyncConfig.disabled || !AsyncConfig.enableAsyncChunkSend) return;
        if (chunksToSend == null || chunksToSend.isEmpty()) return;

        ExecutorService exec = ParallelProcessor.executor;
        if (exec == null || exec.isShutdown()) return;

        final LevelLightEngine lightEngine = player.level().getLightEngine();
        final int n = chunksToSend.size();
        final LevelChunk[] arr = chunksToSend.toArray(new LevelChunk[0]);
        final ClientboundLevelChunkWithLightPacket[] packets = new ClientboundLevelChunkWithLightPacket[n];

        int pool = Math.max(1, ParallelProcessor.getPoolSize());
        int batchSize = Math.max(1, (n + pool - 1) / pool);
        int batchCount = (n + batchSize - 1) / batchSize;
        CompletableFuture<?>[] futures = new CompletableFuture<?>[batchCount];

        int dispatched = 0;
        for (int s = 0; s < n; s += batchSize, dispatched++) {
            final int from = s;
            final int to = Math.min(s + batchSize, n);
            try {
                futures[dispatched] = CompletableFuture.runAsync(() -> {
                    for (int k = from; k < to; k++) {
                        try {
                            packets[k] = new ClientboundLevelChunkWithLightPacket(
                                    arr[k], lightEngine, null, null);
                        } catch (Throwable t) {
                            ASYNC_LOG.warn("Async chunk packet build failed for {} — falling back to main thread build",
                                    arr[k].getPos(), t);
                        }
                    }
                }, exec);
            } catch (RejectedExecutionException rex) {
                break;
            }
        }

        if (dispatched > 0) {
            CompletableFuture<?>[] active = (dispatched == futures.length)
                    ? futures : Arrays.copyOf(futures, dispatched);
            CompletableFuture<Void> all = CompletableFuture.allOf(active);
            while (!all.isDone()) LockSupport.parkNanos(50_000L);
            if (all.isCompletedExceptionally()) {
                try { all.join(); } catch (Throwable ignored) {}
            }
        }

        IdentityHashMap<LevelChunk, ClientboundLevelChunkWithLightPacket> cache = async$cache.get();
        for (int i = 0; i < n; i++) {
            if (packets[i] != null) cache.put(arr[i], packets[i]);
        }
    }

    @WrapOperation(method = "sendChunk", at = @At(value = "NEW", target = "(Lnet/minecraft/world/level/chunk/LevelChunk;Lnet/minecraft/world/level/lighting/LevelLightEngine;Ljava/util/BitSet;Ljava/util/BitSet;)Lnet/minecraft/network/protocol/game/ClientboundLevelChunkWithLightPacket;"))
    private static ClientboundLevelChunkWithLightPacket async$takeCached(
            LevelChunk chunk, LevelLightEngine engine, BitSet sky, BitSet block,
            Operation<ClientboundLevelChunkWithLightPacket> original) {
        ClientboundLevelChunkWithLightPacket cached = async$cache.get().remove(chunk);
        return cached != null ? cached : original.call(chunk, engine, sky, block);
    }
}
