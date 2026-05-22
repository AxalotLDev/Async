package com.axalotl.async.common.mixin.vmp;

import com.axalotl.async.common.ParallelProcessor;
import com.axalotl.async.common.config.AsyncConfig;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.locks.LockSupport;

@Pseudo
@Mixin(targets = "com.ishland.vmp.common.playerwatching.NearbyEntityTracking", remap = false)
public abstract class NearbyEntityTrackingMixin {

    @Unique
    private static final Logger ASYNC_LOGGER = LoggerFactory.getLogger("Async-VmpTracking");

    @Shadow
    private static void tryTickTracker(ChunkMap.TrackedEntity entityTracker) {
        throw new AssertionError("shadow");
    }

    @Shadow
    private static void tryUpdateTracker(ChunkMap.TrackedEntity entityTracker, ServerPlayer player) {
        throw new AssertionError("shadow");
    }

    @Unique
    private final Object async$lock = new Object();

    @Unique
    private IdentityHashMap<ChunkMap.TrackedEntity, TrackerOps> async$pendingOps = new IdentityHashMap<>(256);

    @Unique
    private ChunkMap.TrackedEntity[] async$drainKeys = new ChunkMap.TrackedEntity[256];

    @Unique
    private TrackerOps[] async$drainVals = new TrackerOps[256];

    @Unique
    private CompletableFuture<?>[] async$futuresBuf = new CompletableFuture<?>[16];

    @Unique
    private static final class TrackerOps {
        boolean doTick;
        ServerPlayer u0, u1;
        ArrayList<ServerPlayer> uMore;

        void addUpdate(ServerPlayer p) {
            if (u0 == null) { u0 = p; return; }
            if (u1 == null) { u1 = p; return; }
            if (uMore == null) uMore = new ArrayList<>(4);
            uMore.add(p);
        }
    }

    @WrapMethod(method = "addEntityTracker")
    private void async$lockAdd(ChunkMap.TrackedEntity tracker, Operation<Void> original) {
        synchronized (async$lock) { original.call(tracker); }
    }

    @WrapMethod(method = "removeEntityTracker")
    private void async$lockRemove(ChunkMap.TrackedEntity tracker, Operation<Void> original) {
        synchronized (async$lock) { original.call(tracker); }
    }

    @WrapMethod(method = "addPlayer")
    private void async$lockAddPlayer(ServerPlayer player, Operation<Void> original) {
        synchronized (async$lock) { original.call(player); }
    }

    @WrapMethod(method = "removePlayer")
    private void async$lockRemovePlayer(ServerPlayer player, Operation<Void> original) {
        synchronized (async$lock) { original.call(player); }
    }

    @WrapMethod(method = "tick")
    private void async$lockTick(ChunkMap.DistanceManager ticketManager, Operation<Void> original) {
        IdentityHashMap<ChunkMap.TrackedEntity, TrackerOps> snapshot = null;
        synchronized (async$lock) {
            original.call(ticketManager);
            if (!async$pendingOps.isEmpty()) {
                snapshot = async$pendingOps;
                async$pendingOps = new IdentityHashMap<>(Math.max(16, snapshot.size()));
            }
        }
        if (snapshot != null) async$drainOps(snapshot);
    }

    @WrapOperation(method = "handleTracker", at = @At(value = "INVOKE", target = "Lcom/ishland/vmp/common/playerwatching/NearbyEntityTracking;tryTickTracker(Lnet/minecraft/server/level/ChunkMap$TrackedEntity;)V"))
    private void async$queueTryTick(ChunkMap.TrackedEntity tracker, Operation<Void> original) {
        if (!async$canDispatch()) { original.call(tracker); return; }
        TrackerOps ops = async$pendingOps.get(tracker);
        if (ops == null) {
            ops = new TrackerOps();
            async$pendingOps.put(tracker, ops);
        }
        ops.doTick = true;
    }

    @WrapOperation(method = "handleTracker", at = @At(value = "INVOKE", target = "Lcom/ishland/vmp/common/playerwatching/NearbyEntityTracking;tryUpdateTracker(Lnet/minecraft/server/level/ChunkMap$TrackedEntity;Lnet/minecraft/server/level/ServerPlayer;)V"))
    private void async$queueTryUpdate(ChunkMap.TrackedEntity tracker, ServerPlayer player, Operation<Void> original) {
        if (!async$canDispatch()) { original.call(tracker, player); return; }
        TrackerOps ops = async$pendingOps.get(tracker);
        if (ops == null) {
            ops = new TrackerOps();
            async$pendingOps.put(tracker, ops);
        }
        ops.addUpdate(player);
    }

    @Unique
    private void async$drainOps(IdentityHashMap<ChunkMap.TrackedEntity, TrackerOps> ops) {
        ExecutorService exec = ParallelProcessor.executor;
        boolean canDispatch = async$canDispatch() && exec != null && !exec.isShutdown();

        if (!canDispatch) {
            for (Map.Entry<ChunkMap.TrackedEntity, TrackerOps> e : ops.entrySet()) {
                async$applyOps(e.getKey(), e.getValue());
            }
            return;
        }

        int n = ops.size();
        if (async$drainKeys.length < n) {
            int cap = Math.max(n, async$drainKeys.length << 1);
            async$drainKeys = new ChunkMap.TrackedEntity[cap];
            async$drainVals = new TrackerOps[cap];
        }
        ChunkMap.TrackedEntity[] keys = async$drainKeys;
        TrackerOps[] vals = async$drainVals;
        int i = 0;
        for (Map.Entry<ChunkMap.TrackedEntity, TrackerOps> e : ops.entrySet()) {
            keys[i] = e.getKey();
            vals[i] = e.getValue();
            i++;
        }

        int pool = Math.max(1, ParallelProcessor.getPoolSize());
        int chunkSize = Math.max(1, (n + pool - 1) / pool);
        int batchCount = (n + chunkSize - 1) / chunkSize;
        if (async$futuresBuf.length < batchCount) {
            async$futuresBuf = new CompletableFuture<?>[Math.max(batchCount, async$futuresBuf.length << 1)];
        }
        CompletableFuture<?>[] futures = async$futuresBuf;

        final ChunkMap.TrackedEntity[] keysRef = keys;
        final TrackerOps[] valsRef = vals;
        int b = 0;
        for (int s = 0; s < n; s += chunkSize, b++) {
            final int from = s;
            final int to = Math.min(s + chunkSize, n);
            try {
                futures[b] = CompletableFuture.runAsync(() -> {
                    for (int k = from; k < to; k++) {
                        async$applyOps(keysRef[k], valsRef[k]);
                    }
                }, exec);
            } catch (java.util.concurrent.RejectedExecutionException rex) {
                for (int j = 0; j < b; j++) {
                    try { futures[j].join(); } catch (Throwable joinEx) {
                        ASYNC_LOGGER.error("VMP tracking batch join failed during fallback", joinEx);
                    }
                    futures[j] = null;
                }
                for (int k = from; k < n; k++) async$applyOps(keysRef[k], valsRef[k]);
                for (int j = 0; j < n; j++) { keysRef[j] = null; valsRef[j] = null; }
                return;
            }
        }

        CompletableFuture<?>[] slice = (b == futures.length) ? futures : Arrays.copyOf(futures, b);
        CompletableFuture<Void> all = CompletableFuture.allOf(slice);
        while (!all.isDone()) LockSupport.parkNanos(50_000L);
        if (all.isCompletedExceptionally()) {
            try { all.join(); } catch (Throwable batchEx) {
                ASYNC_LOGGER.error("VMP tracking batch failed", batchEx);
            }
        }

        for (int j = 0; j < n; j++) { keysRef[j] = null; valsRef[j] = null; }
        for (int j = 0; j < b; j++) futures[j] = null;
    }

    @Unique
    private static void async$applyOps(ChunkMap.TrackedEntity tracker, TrackerOps ops) {
        if (ops.doTick) {
            try { tryTickTracker(tracker); } catch (Throwable t) {
                ASYNC_LOGGER.error("tryTickTracker failed for tracker {}", tracker, t);
            }
        }
        if (ops.u0 != null) {
            try { tryUpdateTracker(tracker, ops.u0); } catch (Throwable t) {
                ASYNC_LOGGER.error("tryUpdateTracker failed for tracker {} / player {}", tracker, ops.u0, t);
            }
        }
        if (ops.u1 != null) {
            try { tryUpdateTracker(tracker, ops.u1); } catch (Throwable t) {
                ASYNC_LOGGER.error("tryUpdateTracker failed for tracker {} / player {}", tracker, ops.u1, t);
            }
        }
        if (ops.uMore != null) {
            ArrayList<ServerPlayer> rest = ops.uMore;
            for (int i = 0, sz = rest.size(); i < sz; i++) {
                ServerPlayer p = rest.get(i);
                try { tryUpdateTracker(tracker, p); } catch (Throwable t) {
                    ASYNC_LOGGER.error("tryUpdateTracker failed for tracker {} / player {}", tracker, p, t);
                }
            }
        }
    }

    @Unique
    private static boolean async$canDispatch() {
        if (AsyncConfig.disabled || !AsyncConfig.enableAsyncVmpTracking) return false;
        ExecutorService exec = ParallelProcessor.executor;
        return exec != null && !exec.isShutdown();
    }
}
