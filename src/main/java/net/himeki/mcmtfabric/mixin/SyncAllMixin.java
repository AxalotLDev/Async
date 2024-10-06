package net.himeki.mcmtfabric.mixin;

import net.minecraft.advancement.criterion.AbstractCriterion;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.ai.pathing.PathMinHeap;
import net.minecraft.util.math.random.CheckedRandom;
import net.minecraft.world.chunk.light.LevelPropagator;
import net.minecraft.world.event.listener.SimpleGameEventDispatcher;
import net.minecraft.world.tick.ChunkTickScheduler;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = {PathMinHeap.class, ChunkTickScheduler.class, LevelPropagator.class, EntityNavigation.class, CheckedRandom.class, SimpleGameEventDispatcher.class, AbstractCriterion.class})
public class SyncAllMixin {
}
