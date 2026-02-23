package com.axalotl.async.common.mixin.utils;

import com.axalotl.async.common.config.AsyncConfig;
import com.axalotl.async.common.parallelised.utils.PortalCreationCache;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.BlockUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.portal.DimensionTransition;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.Optional;

import static net.minecraft.world.level.block.NetherPortalBlock.AXIS;

@Mixin(NetherPortalBlock.class)
public abstract class NetherPortalBlockMixin {

    @Unique
    private static final Object async$lock = new Object();

    @Shadow
    @Final
    private static Logger LOGGER;

    @Shadow
    private static DimensionTransition getDimensionTransitionFromExit(
            Entity entity, BlockPos pos, BlockUtil.FoundRectangle rectangle,
            ServerLevel level, DimensionTransition.PostDimensionTransition postDimensionTransition) {
        return null;
    }

    @WrapMethod(method = "getExitPortal")
    private DimensionTransition async_getExitPortal(
            ServerLevel level,
            Entity entity,
            BlockPos pos,
            BlockPos exitPos,
            boolean isNether,
            WorldBorder worldBorder,
            Operation<DimensionTransition> original
    ) {
        if (AsyncConfig.disabled) return original.call(level, entity, pos, exitPos, isNether, worldBorder);
        ResourceKey<Level> dimension = level.dimension();
        synchronized (async$lock) {
            BlockUtil.FoundRectangle cached = PortalCreationCache.get(dimension);
            if (cached != null) {
                BlockPos blockpos = cached.minCorner;
                DimensionTransition.PostDimensionTransition post =
                        DimensionTransition.PLAY_PORTAL_SOUND.then(
                                async$mainThreadTicket(level, blockpos)
                        );
                return getDimensionTransitionFromExit(entity, pos, cached, level, post);
            }

            Optional<BlockPos> optional = level.getPortalForcer().findClosestPortalPosition(exitPos, isNether, worldBorder);
            BlockUtil.FoundRectangle blockutil$foundrectangle;
            DimensionTransition.PostDimensionTransition dimensiontransition$postdimensiontransition;

            if (optional.isPresent()) {
                BlockPos blockpos = optional.get();
                BlockState blockstate = level.getBlockState(blockpos);
                blockutil$foundrectangle = BlockUtil.getLargestRectangleAround(
                        blockpos,
                        blockstate.getValue(BlockStateProperties.HORIZONTAL_AXIS),
                        21,
                        Direction.Axis.Y,
                        21,
                        p_351970_ -> level.getBlockState(p_351970_) == blockstate
                );
                dimensiontransition$postdimensiontransition =
                        DimensionTransition.PLAY_PORTAL_SOUND.then(
                                async$mainThreadTicket(level, blockpos)
                        );
            } else {
                Direction.Axis direction$axis = entity.level()
                        .getBlockState(pos)
                        .getOptionalValue(AXIS)
                        .orElse(Direction.Axis.X);
                Optional<BlockUtil.FoundRectangle> optional1 =
                        level.getPortalForcer().createPortal(exitPos, direction$axis);
                if (optional1.isEmpty()) {
                    LOGGER.error("Unable to create a portal, likely target out of worldborder");
                    return null;
                }
                blockutil$foundrectangle = optional1.get();
                dimensiontransition$postdimensiontransition =
                        DimensionTransition.PLAY_PORTAL_SOUND.then(
                                async$mainThreadPlaceTicket(level)
                        );
            }

            PortalCreationCache.put(dimension, blockutil$foundrectangle);
            return getDimensionTransitionFromExit(
                    entity, pos, blockutil$foundrectangle, level, dimensiontransition$postdimensiontransition
            );
        }
    }

    @Unique
    private static DimensionTransition.PostDimensionTransition async$mainThreadTicket(
            ServerLevel level, BlockPos blockpos
    ) {
        return entity -> {
            if (Thread.currentThread() == level.getServer().getRunningThread()) {
                entity.placePortalTicket(blockpos);
            } else {
                level.getServer().execute(() -> entity.placePortalTicket(blockpos));
            }
        };
    }

    @Unique
    private static DimensionTransition.PostDimensionTransition async$mainThreadPlaceTicket(
            ServerLevel level
    ) {
        return entity -> {
            BlockPos ticketPos = entity.blockPosition();
            if (Thread.currentThread() == level.getServer().getRunningThread()) {
                entity.placePortalTicket(ticketPos);
            } else {
                level.getServer().execute(() -> entity.placePortalTicket(ticketPos));
            }
        };
    }
}