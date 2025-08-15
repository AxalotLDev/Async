package com.axalotl.async.common.mixin.entity;

import com.axalotl.async.common.parallelised.ConcurrentCollections;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Set;

@Mixin(GoalSelector.class)
public class GoalSelectorMixin {

    @Shadow
    private final Set<WrappedGoal> availableGoals = ConcurrentCollections.newHashSet();
}