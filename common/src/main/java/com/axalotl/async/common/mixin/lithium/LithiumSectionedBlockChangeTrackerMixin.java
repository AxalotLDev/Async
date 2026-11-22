package com.axalotl.async.common.mixin.lithium;

import com.axalotl.async.common.utils.LithiumBlockChangeTrackerLock;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.caffeinemc.mods.lithium.common.block.BlockListeningSection;
import net.caffeinemc.mods.lithium.common.tracking.block.SectionedBlockChangeTracker;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = SectionedBlockChangeTracker.class, remap = false)
public class LithiumSectionedBlockChangeTrackerMixin {

    @WrapMethod(method = "register")
    private void wrapRegisterAt(Operation<Void> original) {
        synchronized (LithiumBlockChangeTrackerLock.LOCK) {
            original.call();
        }
    }

    @WrapMethod(method = "unregister")
    private void wrapUnregister(Operation<Void> original) {
        synchronized (LithiumBlockChangeTrackerLock.LOCK) {
            original.call();
        }
    }

    @WrapMethod(method = "listenToAllSections")
    private void wrapListenToAllSections(Operation<Void> original) {
        synchronized (LithiumBlockChangeTrackerLock.LOCK) {
            original.call();
        }
    }

    @WrapMethod(method = "onChunkSectionInvalidated")
    private void wrapOnChunkSectionInvalidated(SectionPos sectionPos, Operation<Void> original) {
        synchronized (LithiumBlockChangeTrackerLock.LOCK) {
            original.call(sectionPos);
        }
    }

    @WrapMethod(method = "setChanged(Lnet/caffeinemc/mods/lithium/common/block/BlockListeningSection;IIILnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;)Z")
    private boolean wrapSetChanged(BlockListeningSection section, int localX, int localY, int localZ, BlockState oldState, BlockState newState, Operation<Boolean> original) {
        synchronized (LithiumBlockChangeTrackerLock.LOCK) {
            return original.call(section, localX, localY, localZ, oldState, newState);
        }
    }
}