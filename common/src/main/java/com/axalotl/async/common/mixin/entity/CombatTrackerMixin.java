package com.axalotl.async.common.mixin.entity;

import net.minecraft.world.damagesource.CombatEntry;
import net.minecraft.world.damagesource.CombatTracker;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Mixin(CombatTracker.class)
public class CombatTrackerMixin {

    @Shadow
    @Final
    @Mutable
    private List<CombatEntry> entries = new CopyOnWriteArrayList<>();
}