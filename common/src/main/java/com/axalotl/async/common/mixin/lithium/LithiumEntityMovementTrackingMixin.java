package com.axalotl.async.common.mixin.lithium;

import com.bawnorton.mixinsquared.TargetHandler;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.caffeinemc.mods.lithium.common.tracking.entity.SectionedEntityMovementTracker;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.level.entity.EntitySectionStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = EntitySection.class, priority = 1500)
public class LithiumEntityMovementTrackingMixin {
    @Unique
    private static final String LITHIUM_MIXIN = "net.caffeinemc.mods.lithium.mixin.util.entity_movement_tracking.EntitySectionMixin";

    @WrapMethod(method = "@MixinSquared:Handler")
    @TargetHandler(mixin = LITHIUM_MIXIN, name = "lithium$trackEntityMovement")
    private void trackEntityMovement(int trackerType, long time, Operation<Void> original) {
        synchronized (LithiumMovementTrackingLock.LOCK) {
            original.call(trackerType, time);
        }
    }

    @WrapMethod(method = "@MixinSquared:Handler")
    @TargetHandler(mixin = LITHIUM_MIXIN, name = "lithium$listenToMovementOnce")
    private void listenToMovementOnce(SectionedEntityMovementTracker<?> tracker, int trackerType, Operation<Void> original) {
        synchronized (LithiumMovementTrackingLock.LOCK) {
            original.call(tracker, trackerType);
        }
    }

    @WrapMethod(method = "@MixinSquared:Handler")
    @TargetHandler(mixin = LITHIUM_MIXIN, name = "lithium$removeListenToMovementOnce")
    private void removeListenToMovementOnce(SectionedEntityMovementTracker<?> tracker, int trackerType, Operation<Void> original) {
        synchronized (LithiumMovementTrackingLock.LOCK) {
            original.call(tracker, trackerType);
        }
    }

    @WrapMethod(method = "@MixinSquared:Handler")
    @TargetHandler(mixin = LITHIUM_MIXIN, name = "lithium$addListener")
    private void addListener(SectionedEntityMovementTracker<?> tracker, Operation<Void> original) {
        synchronized (LithiumMovementTrackingLock.LOCK) {
            original.call(tracker);
        }
    }

    @WrapMethod(method = "@MixinSquared:Handler")
    @TargetHandler(mixin = LITHIUM_MIXIN, name = "lithium$removeListener")
    private void removeListener(EntitySectionStorage<?> storage, SectionedEntityMovementTracker<?> tracker, Operation<Void> original) {
        synchronized (LithiumMovementTrackingLock.LOCK) {
            original.call(storage, tracker);
        }
    }
}