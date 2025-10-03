package com.axalotl.async.common.mixin.entity;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntSets;
import net.minecraft.world.entity.ai.sensing.Sensing;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(Sensing.class)
public class SensingMixin {
    @Shadow
    private final IntSet seen = IntSets.synchronize(new IntOpenHashSet());
    @Shadow
    private final IntSet unseen = IntSets.synchronize(new IntOpenHashSet());
}