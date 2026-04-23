package com.axalotl.async.common.mixin.entity.spawn;

import com.axalotl.async.api.fastutil.AtomicMobCategoryCounts;
import com.axalotl.async.api.spawn.AsyncSpawnStateView;
import com.axalotl.async.common.ParallelProcessor;
import com.axalotl.async.common.parallelised.utils.AsyncSpawnContext;
import com.axalotl.async.common.config.AsyncConfig;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.LocalMobCapCalculator;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.PotentialCalculator;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.atomic.AtomicIntegerArray;

@Mixin(NaturalSpawner.SpawnState.class)
public abstract class SpawnStateMixin implements AsyncSpawnStateView {

    @Shadow @Final private PotentialCalculator spawnPotential;
    @Shadow @Final private LocalMobCapCalculator localMobCapCalculator;
    @Shadow @Final private int spawnableChunkCount;

    @Mutable @Shadow @Final private Object2IntOpenHashMap<MobCategory> mobCategoryCounts;
    @Mutable @Shadow @Final private Object2IntMap<MobCategory> unmodifiableMobCategoryCounts;


    @Shadow
    private boolean canSpawnForCategoryGlobal(MobCategory mobCategory) {
        throw new AssertionError("shadow");
    }

    @Unique private AtomicIntegerArray async$backing;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void async$initAtomicCounts(int spawnableChunkCount, Object2IntOpenHashMap<MobCategory> mobCategoryCounts, PotentialCalculator spawnPotential, LocalMobCapCalculator localMobCapCalculator, CallbackInfo ci) {
        MobCategory[] categories = ParallelProcessor.CATEGORIES;
        int n = categories.length;
        AtomicIntegerArray atomic = new AtomicIntegerArray(n);
        for (int i = 0; i < n; i++) {
            atomic.set(i, this.mobCategoryCounts.getInt(categories[i]));
        }

        AtomicMobCategoryCounts view = new AtomicMobCategoryCounts(atomic);
        this.mobCategoryCounts = view;
        this.unmodifiableMobCategoryCounts = Object2IntMaps.unmodifiable(view);
        this.async$backing = atomic;
    }

    @Override
    public AtomicIntegerArray async$countsBacking() {
        return this.async$backing;
    }

    @Override
    public boolean async$canSpawnForCategoryGlobal(MobCategory cat) {
        return this.canSpawnForCategoryGlobal(cat);
    }

    @Inject(method = "canSpawn", at = @At("HEAD"), cancellable = true)
    private void async$capFastBail(EntityType<?> type, BlockPos testPos, ChunkAccess chunk, CallbackInfoReturnable<Boolean> cir) {
        if (AsyncConfig.disabled || !AsyncConfig.enableAsyncSpawn) return;
        if (!this.canSpawnForCategoryGlobal(type.getCategory())) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "afterSpawn", at = @At("HEAD"), cancellable = true)
    private void async$maybeSkipAfterSpawn(Mob mob, ChunkAccess chunk, CallbackInfo ci) {
        AsyncSpawnContext.Frame frame = AsyncSpawnContext.FRAME.get();
        if (frame.skipAfterSpawn) {
            frame.skipAfterSpawn = false;
            frame.suppressCountIncrement = false;
            ci.cancel();
        }
    }


    @WrapOperation(method = "afterSpawn", at = @At(value = "INVOKE", target = "Lit/unimi/dsi/fastutil/objects/Object2IntOpenHashMap;addTo(Ljava/lang/Object;I)I"))
    private int async$maybeSuppressCountIncrement(Object2IntOpenHashMap<MobCategory> counts, Object k, int incr, Operation<Integer> original) {
        AsyncSpawnContext.Frame frame = AsyncSpawnContext.FRAME.get();
        if (frame.suppressCountIncrement) {
            frame.suppressCountIncrement = false;
            return counts.getInt(k);
        }
        return original.call(counts, k, incr);
    }
}