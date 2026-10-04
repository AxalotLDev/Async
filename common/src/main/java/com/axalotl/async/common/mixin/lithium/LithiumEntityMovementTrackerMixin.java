package com.axalotl.async.common.mixin.lithium;

import com.axalotl.async.common.utils.LithiumMovementTrackingLock;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.caffeinemc.mods.lithium.common.tracking.entity.EntityMovementTrackerSection;
import net.caffeinemc.mods.lithium.common.tracking.entity.SectionedEntityMovementListener;
import net.caffeinemc.mods.lithium.common.tracking.entity.SectionedEntityMovementTracker;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = SectionedEntityMovementTracker.class, priority = 1500)
public abstract class LithiumEntityMovementTrackerMixin {

    @WrapMethod(method = "register")
    private void wrapRegister(ServerLevel world, Operation<Void> original) {
        synchronized (LithiumMovementTrackingLock.LOCK) {
            original.call(world);
        }
    }

    @WrapMethod(method = "unRegister")
    private void wrapUnRegister(ServerLevel world, Operation<Void> original) {
        synchronized (LithiumMovementTrackingLock.LOCK) {
            original.call(world);
        }
    }

    @WrapMethod(method = "isUnchangedSince")
    private boolean wrapIsUnchangedSince(long lastCheckedTime, Operation<Boolean> original) {
        synchronized (LithiumMovementTrackingLock.LOCK) {
            return original.call(lastCheckedTime);
        }
    }

    @WrapMethod(method = "onSectionEnteredRange")
    private void wrapOnSectionEnteredRange(EntityMovementTrackerSection section, Operation<Void> original) {
        synchronized (LithiumMovementTrackingLock.LOCK) {
            original.call(section);
        }
    }

    @WrapMethod(method = "onSectionLeftRange")
    private void wrapOnSectionLeftRange(EntityMovementTrackerSection section, Operation<Void> original) {
        synchronized (LithiumMovementTrackingLock.LOCK) {
            original.call(section);
        }
    }

    @WrapMethod(method = "listenToEntityMovementOnce")
    private void wrapListenToEntityMovementOnce(SectionedEntityMovementListener listener, Operation<Void> original) {
        synchronized (LithiumMovementTrackingLock.LOCK) {
            original.call(listener);
        }
    }

    @WrapMethod(method = "emitEntityMovement")
    private void wrapEmitEntityMovement(int classMask, EntityMovementTrackerSection section, Operation<Void> original) {
        synchronized (LithiumMovementTrackingLock.LOCK) {
            original.call(classMask, section);
        }
    }
}