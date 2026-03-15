package com.axalotl.async.common.mixin.lithium;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import net.caffeinemc.mods.lithium.common.entity.PositionedEntityTrackingSection;
import net.caffeinemc.mods.lithium.common.tracking.entity.EntityMovementTrackerSection;
import net.caffeinemc.mods.lithium.common.tracking.entity.MovementTrackerHelper;
import net.caffeinemc.mods.lithium.common.tracking.entity.SectionedEntityMovementTracker;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.level.entity.Visibility;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.ArrayList;

@Mixin(value = EntitySection.class, priority = 1500)
public abstract class AsyncLithiumEntitySectionMixin<T extends EntityAccess>
        implements EntityMovementTrackerSection, PositionedEntityTrackingSection {

    @Shadow
    private Visibility chunkStatus;

    @Shadow
    public abstract boolean isEmpty();

    @Unique
    private final ReferenceOpenHashSet<SectionedEntityMovementTracker<?>> asyncMultiloader$sectionVisibilityListeners = new ReferenceOpenHashSet<>(0);
    @Unique
    @SuppressWarnings("unchecked")
    private final ArrayList<SectionedEntityMovementTracker<?>>[] asyncMultiloader$entityMovementListenersByType = new ArrayList[MovementTrackerHelper.NUM_MOVEMENT_NOTIFYING_CLASSES];
    @Unique
    private final long[] asyncMultiloader$lastEntityMovementByType = new long[MovementTrackerHelper.NUM_MOVEMENT_NOTIFYING_CLASSES];

    @Override
    public synchronized void lithium$addListener(SectionedEntityMovementTracker<?> listener) {
        this.asyncMultiloader$sectionVisibilityListeners.add(listener);
        if (this.chunkStatus.isAccessible()) {
            listener.onSectionEnteredRange(this);
        }
    }

    @Override
    public synchronized void lithium$removeListener(EntitySectionStorage<?> sectionedEntityCache, SectionedEntityMovementTracker<?> listener) {
        boolean removed = this.asyncMultiloader$sectionVisibilityListeners.remove(listener);
        if (this.chunkStatus.isAccessible() && removed) {
            listener.onSectionLeftRange(this);
        }
        if (this.isEmpty()) {
            sectionedEntityCache.remove(this.lithium$getPos());
        }
    }

    @Override
    public synchronized void lithium$trackEntityMovement(int notificationMask, long time) {
        long[] lastEntityMovementByType = this.asyncMultiloader$lastEntityMovementByType;
        int size = lastEntityMovementByType.length;
        int mask;
        for (int entityClassIndex = Integer.numberOfTrailingZeros(notificationMask); entityClassIndex < size; ) {
            lastEntityMovementByType[entityClassIndex] = time;

            ArrayList<SectionedEntityMovementTracker<?>> entityMovementListeners = this.asyncMultiloader$entityMovementListenersByType[entityClassIndex];
            if (entityMovementListeners != null) {
                for (int listIndex = entityMovementListeners.size() - 1; listIndex >= 0; listIndex--) {
                    SectionedEntityMovementTracker<?> sectionedEntityMovementTracker = entityMovementListeners.remove(listIndex);
                    sectionedEntityMovementTracker.emitEntityMovement(notificationMask, this);
                }
            }

            mask = 0xffff_fffe << entityClassIndex;
            entityClassIndex = Integer.numberOfTrailingZeros(notificationMask & mask);
        }
    }

    @Override
    public synchronized long lithium$getChangeTime(int trackedClass) {
        return this.asyncMultiloader$lastEntityMovementByType[trackedClass];
    }

    @ModifyReturnValue(method = "isEmpty()Z", at = @At(value = "RETURN"))
    public boolean modifyIsEmpty(boolean previousIsEmpty) {
        return previousIsEmpty && this.asyncMultiloader$sectionVisibilityListeners.isEmpty();
    }

    @ModifyVariable(method = "updateChunkStatus(Lnet/minecraft/world/level/entity/Visibility;)Lnet/minecraft/world/level/entity/Visibility;", at = @At(value = "HEAD"), argsOnly = true)
    public Visibility swapStatus(final Visibility newStatus) {
        if (this.chunkStatus.isAccessible() != newStatus.isAccessible()) {
            if (!newStatus.isAccessible()) {
                if (!this.asyncMultiloader$sectionVisibilityListeners.isEmpty()) {
                    for (SectionedEntityMovementTracker<?> listener : this.asyncMultiloader$sectionVisibilityListeners) {
                        listener.onSectionLeftRange(this);
                    }
                }
            } else {
                if (!this.asyncMultiloader$sectionVisibilityListeners.isEmpty()) {
                    for (SectionedEntityMovementTracker<?> listener : this.asyncMultiloader$sectionVisibilityListeners) {
                        listener.onSectionEnteredRange(this);
                    }
                }
            }
        }
        return newStatus;
    }

    @Override
    public synchronized <S, E extends EntityAccess> void lithium$listenToMovementOnce(SectionedEntityMovementTracker<E> listener, int trackedClass) {
        if (this.asyncMultiloader$entityMovementListenersByType[trackedClass] == null) {
            this.asyncMultiloader$entityMovementListenersByType[trackedClass] = new ArrayList<>();
        }
        this.asyncMultiloader$entityMovementListenersByType[trackedClass].add(listener);
    }

    @Override
    public synchronized <S, E extends EntityAccess> void lithium$removeListenToMovementOnce(SectionedEntityMovementTracker<E> listener, int trackedClass) {
        if (this.asyncMultiloader$entityMovementListenersByType[trackedClass] != null) {
            this.asyncMultiloader$entityMovementListenersByType[trackedClass].remove(listener);
        }
    }
}