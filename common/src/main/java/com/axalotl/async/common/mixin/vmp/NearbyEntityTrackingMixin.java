package com.axalotl.async.common.mixin.vmp;

import com.axalotl.async.common.ParallelProcessor;
import com.axalotl.async.common.config.AsyncConfig;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ChunkMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;

/**
 * Moves VMP's per-tick {@code tryTickTracker} invocations off the Server
 * thread onto the async pool.
 *
 * <p>{@code NearbyEntityTracking#tick0} is on the profile's critical path
 * at ~8% Server self. Its three sub-phases have different parallelism
 * properties:
 *
 * <ul>
 *   <li><b>tickStaging</b> + position-update sweep — mutates the
 *       {@code areaMap} and the {@code tracker2ChunkPos} map; must stay
 *       sequential on the Server thread.</li>
 *   <li><b>Per-player tracker loop</b> — iterates {@code playerTrackers}
 *       and calls {@code handleTracker}. Each {@code handleTracker} does
 *       two things: adds the tracker to {@code trackerTickList} (a shared
 *       set, used as a "only tryTick once per tick" guard) and optionally
 *       calls {@code entityTracker.updatePlayer(player)}. The
 *       {@code updatePlayer} path mutates {@code TrackedEntity.seenBy},
 *       which races if the same tracker is touched by multiple players in
 *       parallel — so we leave this pass on the Server thread too.</li>
 *   <li><b>tryTickTracker</b> — the conditional branch inside
 *       {@code handleTracker} that fires <em>once per tracker per tick</em>
 *       (guarded by the {@code trackerTickList.add} first-insertion check)
 *       and calls {@code entityTracker.tryTick()}. {@code tryTick} only
 *       mutates the tracker's own {@code seenBy} / packet state and
 *       sends packets through Netty channels (thread-safe). Each tracker
 *       is touched at most once per tick, so parallelising across
 *       <em>different</em> trackers has no shared-state contention.</li>
 * </ul>
 *
 * <p>Design: a {@link WrapOperation} on the {@code tryTickTracker} call
 * inside {@code handleTracker} queues the tracker into a thread-safe
 * {@link ConcurrentLinkedQueue} instead of ticking it inline. After
 * {@code tick0} returns, the queue is drained across the async pool.
 * With async tick disabled (default) the wrapper calls the original, so
 * vanilla VMP behaviour is preserved.
 *
 * <p>{@link Pseudo} marks this mixin as targeting a class that may not be
 * present (VMP is an optional mod); mixin skips the mixin application
 * gracefully when VMP is absent rather than failing the whole config.
 */
@Pseudo
@Mixin(targets = "com.ishland.vmp.common.playerwatching.NearbyEntityTracking", remap = false)
public abstract class NearbyEntityTrackingMixin {

    /**
     * Shadow of VMP's {@code private static void tryTickTracker(TrackedEntity)}.
     * VMP's body is just
     * {@code ((EntityTrackerExtension)entityTracker).tryTick()}; our drain
     * calls this so the cast to VMP's injected interface stays inside VMP's
     * own class (we have no compile-time dependency on
     * {@code EntityTrackerExtension}).
     */
    @Shadow
    private static void tryTickTracker(ChunkMap.TrackedEntity entityTracker) {
        throw new AssertionError("shadow");
    }

    @Unique
    private final ConcurrentLinkedQueue<ChunkMap.TrackedEntity> async$pendingTryTick = new ConcurrentLinkedQueue<>();

    @WrapOperation(
            method = "handleTracker",
            at = @At(value = "INVOKE",
                     target = "Lcom/ishland/vmp/common/playerwatching/NearbyEntityTracking;tryTickTracker(Lnet/minecraft/server/level/ChunkMap$TrackedEntity;)V")
    )
    private void async$queueTryTick(ChunkMap.TrackedEntity tracker, Operation<Void> original) {
        if (!async$canDispatch()) {
            original.call(tracker);
            return;
        }
        async$pendingTryTick.add(tracker);
    }

    @Inject(method = "tick0", at = @At("RETURN"))
    private void async$drainTryTick(CallbackInfo ci) {
        if (async$pendingTryTick.isEmpty()) return;

        // Fallback to synchronous drain when the pool has been shut down or
        // is otherwise unavailable. VMP's `removeEntityTracker` calls
        // `tick0()` from inside `ChunkMap.removeEntity`, which fires during
        // server shutdown's `PlayerList.removeAll` (disconnect every player,
        // remove their tracked entities). By that point
        // `ParallelProcessor.stop()` has already shut down the executor, and
        // an unchecked `CompletableFuture.runAsync` would throw
        // `RejectedExecutionException` and abort the shutdown cleanup path.
        // Running the trackers on the current thread is always correct —
        // `tryTick` is thread-safe as long as per-tracker work is serial,
        // and the caller owns the serialisation guarantee here.
        ExecutorService exec = ParallelProcessor.executor;
        boolean canDispatch = async$canDispatch() && exec != null && !exec.isShutdown();

        if (!canDispatch) {
            ChunkMap.TrackedEntity t;
            while ((t = async$pendingTryTick.poll()) != null) {
                try { tryTickTracker(t); } catch (Throwable ignored) {}
            }
            return;
        }

        List<ChunkMap.TrackedEntity> batch = new ArrayList<>(async$pendingTryTick.size());
        ChunkMap.TrackedEntity t;
        while ((t = async$pendingTryTick.poll()) != null) batch.add(t);

        int n = batch.size();
        int pool = Math.max(1, ParallelProcessor.getPoolSize());
        int chunkSize = Math.max(1, (n + pool - 1) / pool);
        int batchCount = (n + chunkSize - 1) / chunkSize;
        CompletableFuture<?>[] futures = new CompletableFuture<?>[batchCount];
        ChunkMap.TrackedEntity[] arr = batch.toArray(new ChunkMap.TrackedEntity[0]);

        for (int b = 0, i = 0; i < n; i += chunkSize, b++) {
            final int from = i;
            final int to = Math.min(i + chunkSize, n);
            try {
                futures[b] = CompletableFuture.runAsync(() -> {
                    for (int k = from; k < to; k++) {
                        try {
                            tryTickTracker(arr[k]);
                        } catch (Throwable ignored) {
                            // One tracker's failure must not poison the whole
                            // batch. VMP's tryTick can race with entity removal
                            // on a neighbour thread — swallow and continue.
                        }
                    }
                }, exec);
            } catch (java.util.concurrent.RejectedExecutionException rex) {
                // Pool shut down between our canDispatch check and the actual
                // submit — finish remaining work synchronously.
                for (int k = from; k < n; k++) {
                    try { tryTickTracker(arr[k]); } catch (Throwable ignored) {}
                }
                for (int j = 0; j < b; j++) {
                    try { futures[j].join(); } catch (Throwable ignored) {}
                }
                return;
            }
        }

        CompletableFuture<Void> all = CompletableFuture.allOf(futures);
        while (!all.isDone()) Thread.onSpinWait();
        if (all.isCompletedExceptionally()) {
            try { all.join(); } catch (Throwable ignored) {}
        }
    }

    @Unique
    private static boolean async$canDispatch() {
        if (AsyncConfig.disabled || !AsyncConfig.enableAsyncVmpTracking) return false;
        ExecutorService exec = ParallelProcessor.executor;
        return exec != null && !exec.isShutdown();
    }
}
