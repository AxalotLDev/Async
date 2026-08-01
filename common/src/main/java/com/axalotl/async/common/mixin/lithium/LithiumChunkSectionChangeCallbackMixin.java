package com.axalotl.async.common.mixin.lithium;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.caffeinemc.mods.lithium.common.block.BlockListeningSection;
import net.caffeinemc.mods.lithium.common.block.ListeningBlockStatePredicate;
import net.caffeinemc.mods.lithium.common.tracking.block.ChunkSectionChangeCallback;
import net.caffeinemc.mods.lithium.common.tracking.block.SectionedBlockChangeTracker;
import net.minecraft.core.SectionPos;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = ChunkSectionChangeCallback.class, remap = false)
public class LithiumChunkSectionChangeCallbackMixin {

    @WrapMethod(method = "onBlockChange")
    private synchronized short onBlockChange(int blockIndex, BlockListeningSection section,
                                             Operation<Short> original) {
        return original.call(blockIndex, section);
    }

    @WrapMethod(method = "addTracker")
    private synchronized short addTracker(SectionedBlockChangeTracker tracker,
                                          ListeningBlockStatePredicate predicate,
                                          Operation<Short> original) {
        return original.call(tracker, predicate);
    }

    @WrapMethod(method = "removeTracker")
    private synchronized short removeTracker(SectionedBlockChangeTracker tracker,
                                             ListeningBlockStatePredicate predicate,
                                             Operation<Short> original) {
        return original.call(tracker, predicate);
    }

    @WrapMethod(method = "onChunkSectionInvalidated")
    private synchronized void onChunkSectionInvalidated(SectionPos sectionPosition,
                                                        Operation<Void> original) {
        original.call(sectionPosition);
    }
}
