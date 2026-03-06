package com.axalotl.async.common.mixin.entity;

import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(AttributeMap.class)
public class AttributeMapMixin {

    @Mutable
    @Shadow
    @Final
    private Set<AttributeInstance> attributesToSync;

    @Mutable
    @Shadow
    @Final
    private Map<Holder<Attribute>, AttributeInstance> attributes;

    @Mutable
    @Shadow
    @Final
    private Set<AttributeInstance> attributesToUpdate;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void async$init(CallbackInfo ci) {
        this.attributes = new ConcurrentHashMap<>();
        this.attributesToSync = ConcurrentHashMap.newKeySet();
        this.attributesToUpdate = ConcurrentHashMap.newKeySet();
    }
}