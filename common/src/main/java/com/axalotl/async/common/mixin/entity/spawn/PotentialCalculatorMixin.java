package com.axalotl.async.common.mixin.entity.spawn;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.level.PotentialCalculator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(PotentialCalculator.class)
public class PotentialCalculatorMixin {

    // Plain ArrayList instead of CopyOnWriteArrayList.
    // Charges are built sequentially on the main thread in createState(), then the
    // PotentialCalculator is passed into SpawnState and only read during spawnForChunk().
    // The happens-before from ForkJoinPool task submission guarantees visibility.
    // CopyOnWriteArrayList was O(n) per addCharge (full array copy each time) = O(n²) total.
    @Shadow
    private final List<PotentialCalculator.PointCharge> charges =
        new ArrayList<>();
}
