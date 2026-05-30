package com.axalotl.async.common.mixin.lithium;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.caffeinemc.mods.lithium.common.tracking.block.SectionedBlockChangeTracker;
import net.caffeinemc.mods.lithium.common.util.deduplication.LithiumInterner;
import net.caffeinemc.mods.lithium.common.util.tuples.WorldSectionBox;
import net.caffeinemc.mods.lithium.common.world.LithiumData;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = SectionedBlockChangeTracker.class, remap = false)
public class LithiumSectionedBlockChangeTrackerMixin {

    @Shadow
    @Final
    public WorldSectionBox trackedWorldSections;

    @WrapMethod(method = "register")
    private void wrapRegisterAt(Operation<Void> original) {
        LithiumInterner<SectionedBlockChangeTracker> blockChangeTrackers = ((LithiumData) this.trackedWorldSections.world()).lithium$getData().blockChangeTrackers();
        synchronized (blockChangeTrackers) {
            original.call();
        }
    }

    @WrapMethod(method = "unregister")
    private void wrapUnregister(Operation<Void> original) {
        LithiumInterner<SectionedBlockChangeTracker> blockChangeTrackers = ((LithiumData) this.trackedWorldSections.world()).lithium$getData().blockChangeTrackers();
        synchronized (blockChangeTrackers) {
            original.call();
        }
    }
}