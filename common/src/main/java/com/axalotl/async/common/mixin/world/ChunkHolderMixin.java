package com.axalotl.async.common.mixin.world;

import com.axalotl.async.common.parallelised.fastutil.ConcurrentShortHashSet;
import it.unimi.dsi.fastutil.shorts.ShortSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ChunkHolder.class, priority = 1500)
public abstract class ChunkHolderMixin {

    @Mutable
    @Shadow
    @Final
    private ShortSet[] changedBlocksPerSection;

    @Shadow
    private boolean hasChangedSections;

    @Shadow
    @Final
    private LevelHeightAccessor levelHeightAccessor;

    @Shadow
    public abstract LevelChunk getTickingChunk();

    @Unique
    private final Object async$lock = new Object();

    @Inject(method = "<init>", at = @At("TAIL"))
    private void initConcurrentSets(
            ChunkPos pos,
            int level,
            LevelHeightAccessor world,
            LevelLightEngine lightingProvider,
            ChunkHolder.LevelChangeListener levelUpdateListener,
            ChunkHolder.PlayerProvider playersWatchingChunkProvider,
            CallbackInfo ci
    ) {
        this.changedBlocksPerSection = new ConcurrentShortHashSet[world.getSectionsCount()];
    }

    @Inject(method = "blockChanged", at = @At("HEAD"), cancellable = true)
    private void onBlockChanged(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (this.getTickingChunk() == null) {
            cir.setReturnValue(false);
            return;
        }

        int index = this.levelHeightAccessor.getSectionIndex(pos.getY());
        ShortSet set = this.changedBlocksPerSection[index];

        if (set == null) {
            synchronized (this.async$lock) {
                set = this.changedBlocksPerSection[index];
                if (set == null) {
                    set = new ConcurrentShortHashSet();
                    this.changedBlocksPerSection[index] = set;
                    this.hasChangedSections = true;
                }
            }
        }

        set.add(SectionPos.sectionRelativePos(pos));
        cir.setReturnValue(true);
    }
}
