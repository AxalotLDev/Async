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
    private volatile long async$snapshotCenterXBits = 0;
    @Unique
    private volatile long async$snapshotCenterYBits = 0;
    @Unique
    private volatile long async$snapshotCenterZBits = 0;
    @Unique
    private volatile int async$snapshotRemaining = 0;

    @Inject(method = "moveTo(Lnet/minecraft/world/level/pathfinder/Path;D)Z", at = @At("RETURN"))
    private void async$onMoveTo(Path path, double speed, CallbackInfoReturnable<Boolean> cir) {
        async$rebuildSnapshot();
    }

    @Inject(method = "recomputePath", at = @At("RETURN"))
    private void async$onRecompute(CallbackInfo ci) {
        async$rebuildSnapshot();
    }

    @Inject(method = "stop", at = @At("RETURN"))
    private void async$onStop(CallbackInfo ci) {
        this.async$snapshotRemaining = 0;
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void async$onTick(CallbackInfo ci) {
        async$rebuildSnapshot();
    }

    @Unique
    private void async$rebuildSnapshot() {
        Path currentPath = this.path;

        if (currentPath == null || currentPath.isDone()) {
            this.async$snapshotRemaining = 0;
            return;
        }

        int nodeCount = currentPath.getNodeCount();
        if (nodeCount == 0) {
            this.async$snapshotRemaining = 0;
            return;
        }

        Node endNode = currentPath.getEndNode();
        if (endNode == null) {
            this.async$snapshotRemaining = 0;
            return;
        }

        int remaining = nodeCount - currentPath.getNextNodeIndex();
        if (remaining <= 0) {
            this.async$snapshotRemaining = 0;
            return;
        }

        this.async$snapshotCenterXBits = Double.doubleToRawLongBits((endNode.x + this.mob.getX()) * 0.5);
        this.async$snapshotCenterYBits = Double.doubleToRawLongBits((endNode.y + this.mob.getY()) * 0.5);
        this.async$snapshotCenterZBits = Double.doubleToRawLongBits((endNode.z + this.mob.getZ()) * 0.5);
        this.async$snapshotRemaining = remaining;
    }

    @Override
    public boolean async$shouldRecomputePathSafe(BlockPos pos) {
        int remaining = this.async$snapshotRemaining;
        if (remaining <= 0 || this.hasDelayedRecomputation) {
            return false;
        }

        double cx = Double.longBitsToDouble(this.async$snapshotCenterXBits);
        double cy = Double.longBitsToDouble(this.async$snapshotCenterYBits);
        double cz = Double.longBitsToDouble(this.async$snapshotCenterZBits);

        double dx = pos.getX() + 0.5 - cx;
        if (dx > remaining || dx < -remaining) return false;

        double dy = pos.getY() + 0.5 - cy;
        if (dy > remaining || dy < -remaining) return false;

        double dz = pos.getZ() + 0.5 - cz;
        if (dz > remaining || dz < -remaining) return false;

        return dx * dx + dy * dy + dz * dz < (double) remaining * remaining;
    }
}