package com.axalotl.async.common.mixin.entity;

import com.axalotl.async.common.parallelised.ConcurrentCollections;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Map;

@Mixin(AttributeInstance.class)
public class AttributeInstanceMixin {

    @Shadow
    private final Map<AttributeModifier.Operation, Map<Identifier, AttributeModifier>> modifiersByOperation = ConcurrentCollections.newHashMap();

    @Shadow
    private final Map<Identifier, AttributeModifier> modifierById = ConcurrentCollections.newHashMap();

    @Shadow
    private final Map<Identifier, AttributeModifier> permanentModifiers = ConcurrentCollections.newHashMap();
}