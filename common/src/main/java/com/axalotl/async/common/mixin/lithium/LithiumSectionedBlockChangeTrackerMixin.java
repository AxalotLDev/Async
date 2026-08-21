package com.axalotl.async.common.mixin.lithium;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.caffeinemc.mods.lithium.common.block.BlockListeningSection;
import net.caffeinemc.mods.lithium.common.tracking.block.SectionedBlockChangeTracker;
import net.caffeinemc.mods.lithium.common.util.deduplication.LithiumInterner;
import net.caffeinemc.mods.lithium.common.util.tuples.WorldSectionBox;
import net.caffeinemc.mods.lithium.common.world.LithiumData;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.state.BlockState;
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
            synchronized (this) {
                original.call();
            }
        }
    }

    @WrapMethod(method = "unregister")
    private void wrapUnregister(Operation<Void> original) {
        LithiumInterner<SectionedBlockChangeTracker> blockChangeTrackers = ((LithiumData) this.trackedWorldSections.world()).lithium$getData().blockChangeTrackers();
        synchronized (blockChangeTrackers) {
            synchronized (this) {
                original.call();
            }
        }
    }

    @WrapMethod(method = "listenToAllSections")
    private void wrapListenToAllSections(Operation<Void> original) {
        synchronized (this) {
            original.call();
        }
    }

    @WrapMethod(method = "onChunkSectionInvalidated")
    private void wrapOnChunkSectionInvalidated(SectionPos sectionPos, Operation<Void> original) {
        synchronized (this) {
            original.call(sectionPos);
        }
    }

    @WrapMethod(method = "setChanged(Lnet/caffeinemc/mods/lithium/common/block/BlockListeningSection;IIILnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;)Z")
    private boolean wrapSetChanged(BlockListeningSection section, int localX, int localY, int localZ, BlockState oldState, BlockState newState, Operation<Boolean> original) {
        synchronized (this) {
            return original.call(section, localX, localY, localZ, oldState, newState);
        }
    }
}