package com.axalotl.async.common.mixin.utils;

import com.axalotl.async.common.config.AsyncConfig;
import com.axalotl.async.common.parallelised.utils.PortalCreationCache;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.BlockUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.portal.TeleportTransition;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.Optional;

@Mixin(NetherPortalBlock.class)
public abstract class NetherPortalBlockMixin {

    @Unique
    private static final Object async$lock = new Object();

    @Shadow
    private static TeleportTransition getDimensionTransitionFromExit(
            Entity entity, BlockPos pos, BlockUtil.FoundRectangle rectangle,
            ServerLevel level, TeleportTransition.PostTeleportTransition post
    ) { return null; }

    @Shadow @Final
    private static Logger LOGGER;

    @Shadow @Final
    public static net.minecraft.world.level.block.state.properties.EnumProperty<Direction.Axis> AXIS;

    @WrapMethod(method = "getExitPortal")
    private TeleportTransition async_getExitPortal(
            ServerLevel level, Entity entity, BlockPos pos, BlockPos exitPos,
            boolean isNether, WorldBorder worldBorder, Operation<TeleportTransition> original
    ) {
        if (AsyncConfig.disabled) return original.call(level, entity, pos, exitPos, isNether, worldBorder);

        ResourceKey<Level> dimension = level.dimension();

        synchronized (async$lock) {
            BlockUtil.FoundRectangle cached = PortalCreationCache.get(dimension, exitPos);
            if (cached != null) {
                BlockPos blockpos = cached.minCorner;
                return getDimensionTransitionFromExit(entity, pos, cached, level,
                        TeleportTransition.PLAY_PORTAL_SOUND.then(p -> p.placePortalTicket(blockpos)));
            }

            Optional<BlockPos> optional = level.getPortalForcer().findClosestPortalPosition(exitPos, isNether, worldBorder);
            BlockUtil.FoundRectangle blockutil$foundrectangle;
            TeleportTransition.PostTeleportTransition teleporttransition$postteleporttransition;

            if (optional.isPresent()) {
                BlockPos blockpos = optional.get();
                BlockState blockstate = level.getBlockState(blockpos);
                blockutil$foundrectangle = BlockUtil.getLargestRectangleAround(
                        blockpos,
                        blockstate.getValue(BlockStateProperties.HORIZONTAL_AXIS),
                        21, Direction.Axis.Y, 21,
                        p -> level.getBlockState(p) == blockstate
                );
                teleporttransition$postteleporttransition = TeleportTransition.PLAY_PORTAL_SOUND.then(p -> p.placePortalTicket(blockpos));
            } else {
                Direction.Axis direction$axis = entity.level().getBlockState(pos).getOptionalValue(AXIS).orElse(Direction.Axis.X);
                Optional<BlockUtil.FoundRectangle> optional1 = level.getPortalForcer().createPortal(exitPos, direction$axis);
                if (optional1.isEmpty()) {
                    LOGGER.error("Unable to create a portal, likely target out of worldborder");
                    return null;
                }
                blockutil$foundrectangle = optional1.get();
                teleporttransition$postteleporttransition = TeleportTransition.PLAY_PORTAL_SOUND.then(TeleportTransition.PLACE_PORTAL_TICKET);
            }

            PortalCreationCache.put(dimension, exitPos, blockutil$foundrectangle);
            return getDimensionTransitionFromExit(entity, pos, blockutil$foundrectangle, level, teleporttransition$postteleporttransition);
        }
    }
}