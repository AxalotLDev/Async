package com.axalotl.async.common.mixin.utils;

import com.axalotl.async.common.parallelised.utils.PortalTeleportationManager;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PortalProcessor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.Portal;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(Entity.class)
public abstract class EntityHandlePortalMixin {

    @Shadow
    public PortalProcessor portalProcess;

    @Shadow
    protected abstract void processPortalCooldown();

    @Shadow
    public abstract boolean canUsePortal(boolean ignorePassenger);

    @Shadow
    public abstract void setPortalCooldown();

    @Shadow
    public abstract boolean canTeleport(Level from, Level _to);

    @Shadow
    public abstract Entity teleport(TeleportTransition transition);

    @Shadow
    public abstract Level level();

    @WrapMethod(method = "handlePortal")
    protected void async$handlePortal(Operation<Void> original) {
        Level level = this.level();
        if (!(level instanceof ServerLevel serverLevel)) return;

        this.processPortalCooldown();

        PortalProcessor process = this.portalProcess;
        if (process == null) return;

        Entity self = (Entity) (Object) this;

        if (process.processPortalTeleportation(serverLevel, self, this.canUsePortal(false))) {
            Portal portal = process.portal;

            if (portal instanceof NetherPortalBlock) {
                this.setPortalCooldown();
                PortalTeleportationManager.submitAndAwait(
                        self, portal,
                        process.getEntryPosition(),
                        serverLevel
                );
                this.portalProcess = null;
            } else {
                ProfilerFiller profiler = Profiler.get();
                profiler.push("portal");
                this.setPortalCooldown();

                TeleportTransition transition = process.getPortalDestination(serverLevel, self);
                if (transition != null) {
                    ServerLevel newLevel = transition.newLevel();
                    if (serverLevel.isAllowedToEnterPortal(newLevel)
                            && (newLevel.dimension() == serverLevel.dimension()
                            || this.canTeleport(serverLevel, newLevel))) {
                        this.teleport(transition);
                    }
                }
                profiler.pop();
            }
        } else if (process.hasExpired()) {
            this.portalProcess = null;
        }
    }
}