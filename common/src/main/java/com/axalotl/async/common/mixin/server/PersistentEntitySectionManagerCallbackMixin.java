package com.axalotl.async.common.mixin.server;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PersistentEntitySectionManager.Callback.class)
public abstract class PersistentEntitySectionManagerCallbackMixin {

    @Final
    @Shadow
    private EntityAccess entity;

    @Shadow
    private volatile long currentSectionKey;

    @Shadow
    private volatile EntitySection<EntityAccess> currentSection;

    @Unique
    private PersistentEntitySectionManager<?> async$outerManager;

    @Unique
    private volatile boolean async$pendingRemoval = false;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void async$captureOuter(PersistentEntitySectionManager<?> entity, EntityAccess currentSectionKey, long currentSection, EntitySection<?> section, CallbackInfo ci) {
        this.async$outerManager = entity;
    }

    @SuppressWarnings("unchecked")
    @WrapOperation(method = "onRemove", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/entity/EntitySection;remove(Lnet/minecraft/world/level/entity/EntityAccess;)Z"))
    private boolean async$wrapRemoveInOnRemove(EntitySection<EntityAccess> section, EntityAccess entity, Operation<Boolean> original) {
        this.async$pendingRemoval = true;

        if (original.call(section, entity)) {
            return true;
        }

        long actualKey = SectionPos.asLong(entity.blockPosition());
        if (actualKey != this.currentSectionKey) {
            EntitySectionStorage<EntityAccess> storage = (EntitySectionStorage<EntityAccess>) this.async$outerManager.sectionStorage;
            EntitySection<EntityAccess> actualSection = storage.getSection(actualKey);
            if (actualSection != null && actualSection != section) {
                if (original.call(actualSection, entity)) {
                    this.currentSection = actualSection;
                    this.currentSectionKey = actualKey;
                    return true;
                }
            }
        }

        return false;
    }

    @WrapMethod(method = "onMove")
    private void async$onMove(Operation<Void> original) {
        if (this.async$pendingRemoval) {
            return;
        }

        original.call();
        if (this.async$pendingRemoval) {
            this.currentSection.remove(this.entity);
        }
    }
}