package com.axalotl.async.common.mixin.world;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.spongepowered.asm.mixin.Mixin;

import java.util.function.Predicate;

@Mixin(LevelChunkSection.class)
public abstract class LevelChunkSectionMixin {

    @WrapMethod(method = "maybeHas")
    private boolean async$nullSafeMaybeHas(Predicate<BlockState> predicate, Operation<Boolean> original) {
        return original.call((Predicate<BlockState>) state -> state != null && predicate.test(state));
    }
}