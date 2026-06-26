package com.axalotl.async.common.mixin.entity;

import com.axalotl.async.api.utils.ConcurrentCollections;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Map;

@Mixin(AttributeInstance.class)
public class AttributeInstanceMixin {

    @Shadow
    private final Map<AttributeModifier.Operation, Map<ResourceLocation, AttributeModifier>> modifiersByOperation = ConcurrentCollections.newHashMap();

    @Shadow
    private final Map<ResourceLocation, AttributeModifier> modifierById = ConcurrentCollections.newHashMap();

    @Shadow
    private final Map<ResourceLocation, AttributeModifier> permanentModifiers = ConcurrentCollections.newHashMap();

    @WrapMethod(method = "getModifiers(Lnet/minecraft/world/entity/ai/attributes/AttributeModifier$Operation;)Ljava/util/Map;")
    private Map<ResourceLocation, AttributeModifier> getModifiersConcurrent(AttributeModifier.Operation operation, Operation<Map<ResourceLocation, AttributeModifier>> original) {
        return modifiersByOperation.computeIfAbsent(operation, ignored -> ConcurrentCollections.newHashMap());
    }

    @WrapMethod(method = "addModifier")
    private void addModifierIdempotent(AttributeModifier modifier, Operation<Void> original) {
        modifierById.remove(modifier.id());
        original.call(modifier);
    }
}