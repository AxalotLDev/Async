package com.axalotl.async.common.mixin.world;

import com.axalotl.async.common.parallelised.ConcurrentCollections;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.RandomSequence;
import net.minecraft.world.RandomSequences;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Map;

@Mixin(RandomSequences.class)
public class RandomSequencesMixin {

    @Shadow
    private final Map<ResourceLocation, RandomSequence> sequences = ConcurrentCollections.newHashMap();
}