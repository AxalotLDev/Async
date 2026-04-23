package com.axalotl.async.common.mixin.entity;

import com.axalotl.async.api.utils.ConcurrentList;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.List;

@Mixin(BeehiveBlockEntity.class)
public class BeehiveBlockEntityBeeMixin {

    @Shadow
    private final List<BeehiveBlockEntity.BeeData> stored = new ConcurrentList<>();
}