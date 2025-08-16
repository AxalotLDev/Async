package com.axalotl.async.common.mixin.entity.movement;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

import java.util.Set;

@Mixin(PathFinder.class)
public class PathFinderMixin {

    @WrapMethod(method = "findPath(Lnet/minecraft/world/level/PathNavigationRegion;Lnet/minecraft/world/entity/Mob;Ljava/util/Set;FIF)Lnet/minecraft/world/level/pathfinder/Path;")
    private synchronized @Nullable Path syncFindPath(PathNavigationRegion world, Mob mob, Set<BlockPos> positions, float followRange, int distance, float rangeMultiplier, Operation<Path> original) {
        return original.call(world, mob, positions, followRange, distance, rangeMultiplier);
    }
}