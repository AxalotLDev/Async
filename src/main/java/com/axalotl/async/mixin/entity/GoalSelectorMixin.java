package com.axalotl.async.mixin.entity;

import com.axalotl.async.parallelised.ConcurrentCollections;
import net.minecraft.entity.ai.goal.GoalSelector;
import net.minecraft.entity.ai.goal.PrioritizedGoal;
import org.spongepowered.asm.mixin.*;

import java.util.Set;

@Mixin(GoalSelector.class)
public class GoalSelectorMixin {

    @Shadow
    final private Set<PrioritizedGoal> goals = ConcurrentCollections.newHashSet();
}
