package com.axalotl.async.common.mixin.entity;

import com.axalotl.async.common.parallelised.ConcurrentCollections;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Map;

@Mixin(AttributeInstance.class)
public class AttributeInstanceMixin {

    @Shadow
    @Final
    private Map<AttributeModifier.Operation, Map<ResourceLocation, AttributeModifier>> modifiersByOperation = ConcurrentCollections.newHashMap();

    @Shadow
    @Final
    private Map<ResourceLocation, AttributeModifier> modifierById = ConcurrentCollections.newHashMap();

    @Shadow
    @Final
    private Map<ResourceLocation, AttributeModifier> permanentModifiers = ConcurrentCollections.newHashMap();
}