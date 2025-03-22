package com.axalotl.async.mixin.world;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.redstone.CollectingNeighborUpdater;
import net.minecraft.world.level.redstone.NeighborUpdater;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Mixin(CollectingNeighborUpdater.class)
public abstract class ChainRestrictedNeighborUpdaterMixin implements NeighborUpdater {

    @Shadow
    @Final
    @Mutable
    private List<CollectingNeighborUpdater.NeighborUpdates> addedThisLayer = new CopyOnWriteArrayList<>();

    @WrapMethod(method = "addAndRun")
    private synchronized void syncEnqueue(BlockPos pos, CollectingNeighborUpdater.NeighborUpdates entry, Operation<Void> original) {
        original.call(pos, entry);
    }

}
