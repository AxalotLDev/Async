package com.axalotl.async.mixin.entity;

import net.minecraft.world.level.PotentialCalculator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Mixin(PotentialCalculator.class)
public class GravityFieldMixin {

    @Shadow
    private final List<PotentialCalculator.PointCharge> charges = new CopyOnWriteArrayList<>();
}
