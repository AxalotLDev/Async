package com.axalotl.async.common.mixin.entity;

import com.axalotl.async.common.parallelised.ConcurrentList;
import net.minecraft.world.entity.ai.behavior.ShowTradesToPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.List;

@Mixin(ShowTradesToPlayer.class)
public class ShowTradesToPlayerMixin {

    @Shadow
    private final List<ItemStack> displayItems = new ConcurrentList<>();
}