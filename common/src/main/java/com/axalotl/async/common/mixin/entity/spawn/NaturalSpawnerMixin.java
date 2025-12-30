package com.axalotl.async.common.mixin.entity.spawn;

import com.axalotl.async.common.ParallelProcessor;
import net.minecraft.util.profiling.InactiveProfiler;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.NaturalSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(NaturalSpawner.class)
public class NaturalSpawnerMixin {

    @Redirect(
            method = "spawnForChunk",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/profiling/Profiler;get()Lnet/minecraft/util/profiling/ProfilerFiller;")
    )
    private static ProfilerFiller async$safeProfiler() {
        return ParallelProcessor.isServerExecutionThread()
                ? InactiveProfiler.INSTANCE
                : Profiler.get();
    }
}