package net.himeki.mcmtfabric.mixin;

import net.minecraft.advancement.criterion.AbstractCriterion;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.ai.pathing.PathMinHeap;
import net.minecraft.server.world.ServerEntityManager;
import net.minecraft.util.math.random.CheckedRandom;
import net.minecraft.world.chunk.light.LevelPropagator;
import net.minecraft.world.entity.EntityTrackingSection;
import net.minecraft.world.event.listener.GameEventDispatchManager;
import net.minecraft.world.event.listener.SimpleGameEventDispatcher;
import net.minecraft.world.tick.ChunkTickScheduler;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = {PathMinHeap.class, ChunkTickScheduler.class, LevelPropagator.class, EntityNavigation.class, CheckedRandom.class, SimpleGameEventDispatcher.class, AbstractCriterion.class, EntityTrackingSection.class, GameEventDispatchManager.class, ServerEntityManager.class})
public class SyncAllMixin {
}
