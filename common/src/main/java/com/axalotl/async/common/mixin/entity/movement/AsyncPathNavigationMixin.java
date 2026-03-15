package com.axalotl.async.common.mixin.entity.movement;

import com.axalotl.async.common.parallelised.utils.AsyncSafeNavigation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(PathNavigation.class)
public abstract class AsyncPathNavigationMixin implements AsyncSafeNavigation {

    @Shadow
    protected Path path;

    @Shadow
    protected boolean hasDelayedRecomputation;

    @Shadow
    @Final
    protected Mob mob;

    @Override
    public boolean async$shouldRecomputePathSafe(BlockPos pos) {
        if (this.hasDelayedRecomputation) {
            return false;
        }
        Path currentPath = this.path;
        if (currentPath == null || currentPath.isDone()) {
            return false;
        }
        int nodeCount = currentPath.getNodeCount();
        if (nodeCount == 0) {
            return false;
        }
        Node endNode = currentPath.getEndNode();
        if (endNode == null) {
            return false;
        }
        int remaining = nodeCount - currentPath.getNextNodeIndex();
        if (remaining <= 0) {
            return false;
        }
        double cx = (endNode.x + this.mob.getX()) * 0.5;
        double cy = (endNode.y + this.mob.getY()) * 0.5;
        double cz = (endNode.z + this.mob.getZ()) * 0.5;
        double dx = pos.getX() + 0.5 - cx;
        if (dx > remaining || dx < -remaining) return false;
        double dy = pos.getY() + 0.5 - cy;
        if (dy > remaining || dy < -remaining) return false;
        double dz = pos.getZ() + 0.5 - cz;
        if (dz > remaining || dz < -remaining) return false;
        return dx * dx + dy * dy + dz * dz < (double) remaining * remaining;
    }
}