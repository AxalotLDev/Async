package com.axalotl.async.common.mixin.entity;

import com.axalotl.async.api.utils.ConcurrentCollections;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.Set;

@Mixin(value = AttributeMap.class, priority = 1500)
public class AttributeMapMixin {

    @Shadow
    private final Map<Holder<Attribute>, AttributeInstance> attributes = ConcurrentCollections.newHashMap();

    @Mutable
    @Shadow
    @Final
    private Set<AttributeInstance> attributesToUpdate;

    @Mutable
    @Shadow
    @Final
    private Set<AttributeInstance> attributesToSync;

    @Inject(
            method = "<init>",
            at = @At("RETURN")
    )
    private void initCollections(AttributeSupplier supplier, CallbackInfo ci) {
        this.attributesToUpdate = ConcurrentCollections.newHashSet();
        this.attributesToSync = ConcurrentCollections.newHashSet();
    }
}