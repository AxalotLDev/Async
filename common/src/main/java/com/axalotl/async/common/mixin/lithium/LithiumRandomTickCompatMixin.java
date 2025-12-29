package com.axalotl.async.common.mixin.lithium;

import com.axalotl.async.common.config.AsyncConfig;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Slice;

/**
 * Disables Lithium's random tick optimization when async random ticks are enabled.
 *
 * Lithium caches random tickable block counts in byte arrays. When random ticks run
 * asynchronously, blocks can change between counting and searching, causing
 * "Failed to find random tickable position" errors.
 *
 * This mixin has higher priority (1600) than Lithium's (default 1000) to override it.
 * When enableAsyncRandomTicks is true, we return 0 to let vanilla code handle ticks.
 */
@Mixin(value = ServerLevel.class, priority = 1600)
public abstract class LithiumRandomTickCompatMixin {

    /**
     * Intercepts Lithium's modified return value and forces vanilla behavior
     * when async random ticks are enabled.
     *
     * Lithium's ServerLevelMixin.lithiumRandomTick returns randomTickSpeed to skip vanilla,
     * or the original value (0) to use vanilla. We override to always return 0 (use vanilla)
     * when async is enabled.
     */
    @ModifyExpressionValue(
            method = "tickChunk(Lnet/minecraft/world/level/chunk/LevelChunk;I)V",
            at = @At(value = "CONSTANT", args = "intValue=0"),
            slice = @Slice(
                    from = @At(value = "INVOKE", target = "Lnet/minecraft/core/SectionPos;sectionToBlockCoord(I)I"),
                    to = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;getBlockRandomPos(IIII)Lnet/minecraft/core/BlockPos;", ordinal = 1)
            ),
            require = 0 // Don't fail if Lithium isn't present
    )
    private int disableLithiumRandomTickForAsync(int lithiumValue) {
        // If async random ticks are enabled, force vanilla behavior by returning 0
        // This bypasses Lithium's optimization which is not thread-safe
        if (!AsyncConfig.disabled && AsyncConfig.enableAsyncRandomTicks) {
            return 0;
        }
        // Otherwise keep Lithium's optimized value
        return lithiumValue;
    }
}