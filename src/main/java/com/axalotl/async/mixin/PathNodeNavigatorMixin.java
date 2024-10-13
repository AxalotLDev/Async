package com.axalotl.async.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.entity.ai.pathing.PathNodeNavigator;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.ChunkCache;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

import java.util.Set;

@Mixin(PathNodeNavigator.class)
public class PathNodeNavigatorMixin {
    @WrapMethod(method = "findPathToAny(Lnet/minecraft/world/chunk/ChunkCache;Lnet/minecraft/entity/mob/MobEntity;Ljava/util/Set;FIF)Lnet/minecraft/entity/ai/pathing/Path;")
    private synchronized @Nullable Path syncFindPathToAny(ChunkCache world, MobEntity mob, Set<BlockPos> positions, float followRange, int distance, float rangeMultiplier, Operation<Path> original) {
        return original.call(world, mob, positions, followRange, distance, rangeMultiplier);
    }
}
