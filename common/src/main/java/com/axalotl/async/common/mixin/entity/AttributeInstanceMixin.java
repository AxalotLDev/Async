package com.axalotl.async.common.mixin.entity;

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
public abstract class AttributeInstanceMixin {

    @Mutable
    @Shadow
    @Final
    private Map<AttributeModifier.Operation, Map<Identifier, AttributeModifier>> modifiersByOperation;

    @Mutable
    @Shadow
    @Final
    private Map<Identifier, AttributeModifier> modifierById;

    @Mutable
    @Shadow
    @Final
    private Map<Identifier, AttributeModifier> permanentModifiers;

    @Shadow
    abstract Map<Identifier, AttributeModifier> getModifiers(AttributeModifier.Operation operation);

    @Shadow
    protected abstract void setDirty();

    @Inject(method = "<init>", at = @At("RETURN"))
    private void async$init(CallbackInfo ci) {
        this.modifierById = new ConcurrentHashMap<>();
        this.permanentModifiers = new ConcurrentHashMap<>();

        ConcurrentHashMap<AttributeModifier.Operation, Map<Identifier, AttributeModifier>> concurrent =
                new ConcurrentHashMap<>();
        for (AttributeModifier.Operation op : AttributeModifier.Operation.values()) {
            concurrent.put(op, new ConcurrentHashMap<>());
        }
        this.modifiersByOperation = concurrent;
    }

    @WrapMethod(method = "addModifier")
    private void async$addModifier(AttributeModifier modifier, Operation<Void> original) {
        modifierById.put(modifier.id(), modifier);
        getModifiers(modifier.operation()).put(modifier.id(), modifier);
        setDirty();
    }

    @WrapMethod(method = "getModifiers(Lnet/minecraft/world/entity/ai/attributes/AttributeModifier$Operation;)Ljava/util/Map;")
    private Map<Identifier, AttributeModifier> getModifiersConcurrent(AttributeModifier.Operation operation, Operation<Map<Identifier, AttributeModifier>> original) {
        return modifiersByOperation.computeIfAbsent(operation, op -> new ConcurrentHashMap<>());
    }
}