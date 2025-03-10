package com.axalotl.async.mixin.entity;

import net.minecraft.block.entity.BeehiveBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Mixin(BeehiveBlockEntity.class)
public class BeehiveBlockEntityMixin {

    @Shadow
    private final List<BeehiveBlockEntity.Bee> bees = Collections.synchronizedList(new ArrayList<>());
}
