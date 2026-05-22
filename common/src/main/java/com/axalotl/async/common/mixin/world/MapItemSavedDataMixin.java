package com.axalotl.async.common.mixin.world;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(MapItemSavedData.class)
public abstract class MapItemSavedDataMixin {

    @WrapMethod(method = "tickCarriedBy")
    private synchronized void async$lockTickCarriedBy(Player player, ItemStack stack, @Nullable ItemFrame frame, Operation<Void> original) {
        original.call(player, stack, frame);
    }
}
