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
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PathNavigation.class)
public abstract class AsyncPathNavigationMixin implements AsyncSafeNavigation {

    @Shadow
    protected Path path;

    @Shadow
    protected boolean hasDelayedRecomputation;

    @Shadow
    @Final
    protected Mob mob;

    @Unique
    private volatile PathSnapshot async$snapshot = null;

    @Unique
    private final Object async$lock = new Object();

    @Inject(method = "moveTo(Lnet/minecraft/world/level/pathfinder/Path;D)Z", at = @At("RETURN"))
    private void async$onMoveTo(Path path, double speed, CallbackInfoReturnable<Boolean> cir) {
        async$updateSnapshot();
    }

    @Inject(method = "recomputePath", at = @At("RETURN"))
    private void async$onRecompute(CallbackInfo ci) {
        async$updateSnapshot();
    }

    @Inject(method = "stop", at = @At("RETURN"))
    private void async$onStop(CallbackInfo ci) {
        this.async$snapshot = null;
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void async$onTick(CallbackInfo ci) {
        synchronized (async$lock) {
            Path currentPath = this.path;
            if (currentPath == null || currentPath.isDone()) {
                return;
            }

            PathSnapshot currentSnapshot = this.async$snapshot;
            if (currentSnapshot == null) {
                return;
            }

            int nodeCount = currentPath.getNodeCount();
            int nextIndex = currentPath.getNextNodeIndex();
            int remainingNodes = nodeCount - nextIndex;

            if (remainingNodes <= 0) {
                return;
            }

            Node endNode = currentPath.getEndNode();
            if (endNode == null) {
                return;
            }

            double mobX = this.mob.getX();
            double mobY = this.mob.getY();
            double mobZ = this.mob.getZ();

            double newCenterX = (endNode.x + mobX) / 2.0;
            double newCenterY = (endNode.y + mobY) / 2.0;
            double newCenterZ = (endNode.z + mobZ) / 2.0;

            double dx = newCenterX - currentSnapshot.centerX();
            double dy = newCenterY - currentSnapshot.centerY();
            double dz = newCenterZ - currentSnapshot.centerZ();

            double newMaxDistSq = (double) remainingNodes * remainingNodes;

            // Обновляем только если центр сместился >1 блока или remaining изменилось значительно
            if (dx * dx + dy * dy + dz * dz > 1.0 ||
                    Math.abs(newMaxDistSq - currentSnapshot.maxDistanceSq()) > remainingNodes) {
                this.async$snapshot = new PathSnapshot(newCenterX, newCenterY, newCenterZ, newMaxDistSq);
            }
        }
    }

    @Unique
    private void async$updateSnapshot() {
        synchronized (async$lock) {
            Path currentPath = this.path;

            if (currentPath == null || currentPath.isDone() || currentPath.getNodeCount() == 0) {
                this.async$snapshot = null;
                return;
            }

            Node endNode = currentPath.getEndNode();
            if (endNode == null) {
                this.async$snapshot = null;
                return;
            }

            int remainingNodes = currentPath.getNodeCount() - currentPath.getNextNodeIndex();
            if (remainingNodes <= 0) {
                this.async$snapshot = null;
                return;
            }

            double centerX = (endNode.x + this.mob.getX()) / 2.0;
            double centerY = (endNode.y + this.mob.getY()) / 2.0;
            double centerZ = (endNode.z + this.mob.getZ()) / 2.0;
            double maxDistanceSq = (double) remainingNodes * remainingNodes;

            this.async$snapshot = new PathSnapshot(centerX, centerY, centerZ, maxDistanceSq);
        }
    }

    @Override
    public boolean async$shouldRecomputePathSafe(BlockPos pos) {
        if (this.hasDelayedRecomputation) {
            return false;
        }

        PathSnapshot snapshot = this.async$snapshot;
        if (snapshot == null) {
            return false;
        }

        return snapshot.shouldRecompute(pos);
    }
}