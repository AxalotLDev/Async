package com.axalotl.async.common.mixin.entity.spawn;

import com.axalotl.async.api.fastutil.AtomicMobCategoryCounts;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.LocalMobCapCalculator;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.atomic.AtomicIntegerArray;


@Mixin(value = LocalMobCapCalculator.MobCounts.class, priority = 1100)
public class MobCountsMixin {

    @Mutable
    @Shadow
    @Final
    private Object2IntMap<MobCategory> counts;

    @Unique
    private final AtomicIntegerArray async$atomicCounts = new AtomicIntegerArray(MobCategory.values().length);

    @Inject(method = "<init>", at = @At("TAIL"))
    private void async$swapCountsView(CallbackInfo ci) {
        this.counts = new AtomicMobCategoryCounts(this.async$atomicCounts);
    }

    @WrapMethod(method = "add")
    private void async$add(MobCategory category, Operation<Void> original) {
        async$atomicCounts.incrementAndGet(category.ordinal());
    }

    @WrapMethod(method = "canSpawn")
    private boolean async$canSpawn(MobCategory category, Operation<Boolean> original) {
        return async$atomicCounts.get(category.ordinal()) < category.getMaxInstancesPerChunk();
    }
}
