package com.axalotl.async.common.mixin.lithium;

import com.llamalad7.mixinextras.sugar.Local;
import net.caffeinemc.mods.lithium.common.entity.NavigatingEntity;
import net.caffeinemc.mods.lithium.common.world.ServerWorldExtended;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.WritableLevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(value = ServerLevel.class, priority = 1500)
public abstract class LithiumServerLevel extends Level implements WorldGenLevel, ServerWorldExtended {
    @Unique
    private final Set<PathNavigation> async$activeNavigationsOver = Collections.newSetFromMap(new ConcurrentHashMap<>());

    protected LithiumServerLevel(WritableLevelData levelData, ResourceKey<Level> dimension, RegistryAccess registryAccess, Holder<DimensionType> dimensionTypeRegistration, boolean isClientSide, boolean isDebug, long biomeZoomSeed, int maxChainedNeighborUpdates) {
        super(levelData, dimension, registryAccess, dimensionTypeRegistration, isClientSide, isDebug, biomeZoomSeed, maxChainedNeighborUpdates);
    }


    @Inject(
            method = "sendBlockUpdated",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/Set;iterator()Ljava/util/Iterator;"
            )
    )
    private void updateActiveListeners(BlockPos pos, BlockState oldState, BlockState newState, int arg3, CallbackInfo ci, @Local List<PathNavigation> list) {
        for (PathNavigation nav : async$activeNavigationsOver) {
            if (nav.shouldRecomputePath(pos)) {
                list.add(nav);
            }
        }
    }

    @Override
    public void lithium$setNavigationActive(Mob mobEntity) {
        async$activeNavigationsOver.add(((NavigatingEntity) mobEntity).lithium$getRegisteredNavigation());
    }

    @Override
    public void lithium$setNavigationInactive(Mob mobEntity) {
        async$activeNavigationsOver.remove(((NavigatingEntity) mobEntity).lithium$getRegisteredNavigation());
    }
}