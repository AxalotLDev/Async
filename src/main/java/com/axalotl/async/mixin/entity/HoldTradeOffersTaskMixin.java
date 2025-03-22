package com.axalotl.async.mixin.entity;

import net.minecraft.world.entity.ai.behavior.ShowTradesToPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Mixin(ShowTradesToPlayer.class)
public class HoldTradeOffersTaskMixin {
    @Shadow
    private final List<ItemStack> displayItems = new CopyOnWriteArrayList<>();
}
