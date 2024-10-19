package com.axalotl.async.mixin;

import com.axalotl.async.parallelised.ConcurrentCollections;
import net.minecraft.entity.ai.goal.GoalSelector;
import net.minecraft.entity.ai.goal.PrioritizedGoal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Set;

@Mixin(GoalSelector.class)
public abstract class GoalSelectorMixin {
    @Shadow
    private final Set<PrioritizedGoal> goals = ConcurrentCollections.newHashSet();
}
