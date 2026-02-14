package com.axalotl.async.common.mixin.entity.spawn;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.level.PotentialCalculator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(PotentialCalculator.class)
public class PotentialCalculatorMixin {

    @Shadow
    private final List<PotentialCalculator.PointCharge> charges =
        new ArrayList<>();
}
