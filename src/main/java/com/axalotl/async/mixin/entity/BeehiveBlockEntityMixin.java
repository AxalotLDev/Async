package com.axalotl.async.mixin.entity;

import net.minecraft.block.entity.BeehiveBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Mixin(BeehiveBlockEntity.class)
public class BeehiveBlockEntityMixin {

    @Shadow
    private final List<BeehiveBlockEntity.Bee> bees = new CopyOnWriteArrayList<>();
}
