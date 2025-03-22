package com.axalotl.async.mixin.entity;

import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Mixin(BeehiveBlockEntity.class)
public class BeehiveBlockEntityMixin {

    @Shadow
    private final List<BeehiveBlockEntity.BeeData> stored = Collections.synchronizedList(new ArrayList<>());
}
