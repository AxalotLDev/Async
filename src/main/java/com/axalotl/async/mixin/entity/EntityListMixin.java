package com.axalotl.async.mixin.entity;

import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.entity.Entity;
import net.minecraft.world.EntityList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(EntityList.class)
public class EntityListMixin {

    @Shadow
    private Int2ObjectMap<Entity> temp = new Int2ObjectLinkedOpenHashMap<>();
}