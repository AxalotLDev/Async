package com.axalotl.async.common.mixin.entity;

import com.axalotl.async.common.parallelised.ConcurrentCollections;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(AttributeInstance.class)
public class AttributeInstanceMixin {

    @Shadow
    @Final
    @Mutable
    private Map<Identifier, AttributeModifier> modifierById;

    @Shadow
    @Final
    @Mutable
    private Map<Identifier, AttributeModifier> permanentModifiers;

    @Shadow
    private final Map<AttributeModifier.Operation, Map<Identifier, AttributeModifier>> modifiersByOperation = ConcurrentCollections.newHashMap();

    @Inject(method = "<init>", at = @At("RETURN"))
    private void makeThreadSafe(CallbackInfo ci) {
        modifierById = new ConcurrentHashMap<>(modifierById);
        permanentModifiers = new ConcurrentHashMap<>(permanentModifiers);
    }

    @WrapMethod(method = "getModifiers(Lnet/minecraft/world/entity/ai/attributes/AttributeModifier$Operation;)Ljava/util/Map;")
    private Map<Identifier, AttributeModifier> getModifiersConcurrent(AttributeModifier.Operation operation, Operation<Map<Identifier, AttributeModifier>> original) {
        return modifiersByOperation.computeIfAbsent(operation, op -> ConcurrentCollections.newHashMap());
    }
}