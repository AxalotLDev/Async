package com.axalotl.async.common.mixin.entity;

import com.axalotl.async.common.parallelised.fastutil.ConcurrentLongSortedSet;
import com.axalotl.async.common.parallelised.fastutil.Long2ObjectConcurrentHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongSortedSet;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.level.entity.EntitySectionStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntitySectionStorage.class)
public abstract class EntitySectionStorageMixin<T extends EntityAccess> {

    @Mutable
    @Shadow
    @Final
    private Long2ObjectMap<EntitySection<T>> sections;

    @Mutable
    @Shadow
    @Final
    private LongSortedSet sectionIds;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void replaceConVars(CallbackInfo ci) {
        sections = new Long2ObjectConcurrentHashMap<>();
        sectionIds = new ConcurrentLongSortedSet();
    }
}