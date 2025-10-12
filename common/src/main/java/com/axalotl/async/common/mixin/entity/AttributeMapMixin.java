package com.axalotl.async.common.mixin.entity;

import com.axalotl.async.common.parallelised.ConcurrentCollections;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(AttributeMap.class)
public class AttributeMapMixin {

    @Shadow
    private final Set<AttributeInstance> attributesToSync = ConcurrentHashMap.newKeySet();

    @Shadow
    private final Map<Holder<Attribute>, AttributeInstance> attributes = ConcurrentCollections.newHashMap();

    @Shadow
    private final Set<AttributeInstance> attributesToUpdate = ConcurrentHashMap.newKeySet();
}