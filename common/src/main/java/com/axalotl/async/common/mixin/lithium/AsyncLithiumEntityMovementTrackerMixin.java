package com.axalotl.async.common.mixin.lithium;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.caffeinemc.mods.lithium.common.tracking.entity.EntityMovementTrackerSection;
import net.caffeinemc.mods.lithium.common.tracking.entity.SectionedEntityMovementListener;
import net.caffeinemc.mods.lithium.common.tracking.entity.SectionedEntityMovementTracker;
import net.caffeinemc.mods.lithium.common.util.tuples.WorldSectionBox;
import net.caffeinemc.mods.lithium.common.world.LithiumData;
import net.caffeinemc.mods.lithium.mixin.util.entity_movement_tracking.PersistentEntitySectionManagerAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.level.entity.EntitySectionStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

@Mixin(value = SectionedEntityMovementTracker.class, remap = false)
public abstract class AsyncLithiumEntityMovementTrackerMixin<E extends EntityAccess> {

    @Shadow
    @Final
    Object clazz;

    @Shadow
    @Final
    private int trackedIndex;

    @Shadow
    @Final
    WorldSectionBox trackedWorldSections;

    @Shadow
    ArrayList<EntitySection<E>> sortedSections;

    @Shadow
    boolean[] sectionVisible;

    @Shadow
    private int timesRegistered;

    @Shadow
    private long maxChangeTime;

    @Unique
    private ConcurrentLinkedQueue<EntityMovementTrackerSection> async$notListeningTo;

    @Unique
    private Set<SectionedEntityMovementListener> async$listeners;

    @Unique
    private void async$setChanged(long atTime) {
        if (atTime > this.maxChangeTime) {
            this.maxChangeTime = atTime;
        }
    }

    @Unique
    private void async$notifyListeners() {
        Set<SectionedEntityMovementListener> listeners = this.async$listeners;
        if (!listeners.isEmpty()) {
            Iterator<SectionedEntityMovementListener> it = listeners.iterator();
            while (it.hasNext()) {
                SectionedEntityMovementListener listener = it.next();
                it.remove();
                listener.lithium$handleEntityMovement(this.clazz);
            }
        }
    }

    @Unique
    @SuppressWarnings("unchecked")
    private long async$listenToAllSections() {
        long max = Long.MIN_VALUE;
        SectionedEntityMovementTracker<E> self = (SectionedEntityMovementTracker<E>) (Object) this;
        EntityMovementTrackerSection section;
        while ((section = this.async$notListeningTo.poll()) != null) {
            section.lithium$listenToMovementOnce(self, this.trackedIndex);
            max = Math.max(max, section.lithium$getChangeTime(this.trackedIndex));
        }
        return max;
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void async$init(CallbackInfo ci) {
        this.async$notListeningTo = new ConcurrentLinkedQueue<>();
        this.async$listeners = ConcurrentHashMap.newKeySet();
    }

    @WrapMethod(method = "notifyAllListeners")
    private void async$wrapNotifyAllListeners(Operation<Void> original) {
        async$notifyListeners();
    }

    @WrapMethod(method = "isUnchangedSince")
    private boolean async$wrapIsUnchangedSince(long lastCheckedTime, Operation<Boolean> original) {
        if (lastCheckedTime <= this.maxChangeTime) {
            return false;
        }
        if (!this.async$notListeningTo.isEmpty()) {
            this.async$setChanged(this.async$listenToAllSections());
            return lastCheckedTime > this.maxChangeTime;
        }
        return true;
    }

    @WrapMethod(method = "listenToEntityMovementOnce")
    private void async$wrapListenOnce(SectionedEntityMovementListener listener, Operation<Void> original) {
        this.async$listeners.add(listener);
        if (!this.async$notListeningTo.isEmpty()) {
            this.async$setChanged(this.async$listenToAllSections());
        }
    }

    @WrapMethod(method = "emitEntityMovement")
    private void async$wrapEmitEntityMovement(int classMask, EntityMovementTrackerSection section, Operation<Void> original) {
        if ((classMask & (1 << this.trackedIndex)) != 0) {
            this.async$notifyListeners();
            this.async$notListeningTo.add(section);
        }
    }

    @WrapMethod(method = "onSectionEnteredRange")
    private void async$wrapOnSectionEnteredRange(EntityMovementTrackerSection section, Operation<Void> original) {
        this.async$setChanged(this.trackedWorldSections.world().getGameTime());
        int sectionIndex = this.sortedSections.lastIndexOf(section);
        this.sectionVisible[sectionIndex] = true;
        this.async$notListeningTo.add(section);
        this.async$notifyListeners();
    }

    @WrapMethod(method = "onSectionLeftRange")
    @SuppressWarnings("unchecked")
    private void async$wrapOnSectionLeftRange(EntityMovementTrackerSection section, Operation<Void> original) {
        this.async$setChanged(this.trackedWorldSections.world().getGameTime());
        int sectionIndex = this.sortedSections.lastIndexOf(section);
        this.sectionVisible[sectionIndex] = false;
        if (!this.async$notListeningTo.remove(section)) {
            SectionedEntityMovementTracker<E> self = (SectionedEntityMovementTracker<E>) (Object) this;
            section.lithium$removeListenToMovementOnce(self, this.trackedIndex);
            this.async$notifyListeners();
        }
    }

    @WrapMethod(method = "unRegister")
    @SuppressWarnings("unchecked")
    private void async$wrapUnRegister(ServerLevel world, Operation<Void> original) {
        if (--this.timesRegistered > 0) {
            return;
        }

        SectionedEntityMovementTracker<E> self = (SectionedEntityMovementTracker<E>) (Object) this;

        EntitySectionStorage<E> cache = ((PersistentEntitySectionManagerAccessor<E>)
                ((net.caffeinemc.mods.lithium.mixin.util.entity_movement_tracking.ServerLevelAccessor) world)
                        .getEntityManager()).getCache();

        ((LithiumData) world).lithium$getData().entityMovementTrackers().deleteCanonical(self);

        ArrayList<EntitySection<E>> sections = this.sortedSections;
        for (int i = sections.size() - 1; i >= 0; i--) {
            EntitySection<E> section = sections.get(i);
            EntityMovementTrackerSection sectionAccess = (EntityMovementTrackerSection) section;
            sectionAccess.lithium$removeListener(cache, self);
            if (!this.async$notListeningTo.remove(sectionAccess)) {
                sectionAccess.lithium$removeListenToMovementOnce(self, this.trackedIndex);
            }
        }
        this.async$setChanged(world.getGameTime());
    }
}