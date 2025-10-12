package com.axalotl.async.common.mixin.utils;

import net.minecraft.world.entity.ai.behavior.ShufflingList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Mixin(ShufflingList.class)
public abstract class ShufflingListMixin<U> implements Iterable<U> {

    @Shadow
    protected final List<ShufflingList.WeightedEntry<U>> entries = new CopyOnWriteArrayList<>();
}