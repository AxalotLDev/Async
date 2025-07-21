package com.axalotl.async.common.mixin.entity.movement;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

@Mixin(PoiManager.class)
public class PoiManagerMixin {

    @Unique
    private static final Object async$lock = new Object();

    @WrapMethod(method = "getInSquare")
    private Stream<PoiRecord> getInSquare(Predicate<RegistryAccess.RegistryEntry<PoiType>> typePredicate, BlockPos pos, int radius, PoiManager.Occupancy occupationStatus, Operation<Stream<PoiRecord>> original) {
        synchronized (async$lock) {
            return original.call(typePredicate, pos, radius, occupationStatus);
        }
    }

    @WrapMethod(method = "getInRange")
    private Stream<PoiRecord> getInRange(Predicate<RegistryAccess.RegistryEntry<PoiType>> typePredicate, BlockPos pos, int radius, PoiManager.Occupancy occupationStatus, Operation<Stream<PoiRecord>> original) {
        synchronized (async$lock) {
            return original.call(typePredicate, pos, radius, occupationStatus);
        }
    }

    @WrapMethod(method = "getInChunk")
    private Stream<PoiRecord> getInChunk(Predicate<RegistryAccess.RegistryEntry<PoiType>> typePredicate, ChunkPos chunkPos, PoiManager.Occupancy occupationStatus, Operation<Stream<PoiRecord>> original) {
        synchronized (async$lock) {
            return original.call(typePredicate, chunkPos, occupationStatus);
        }
    }

    @WrapMethod(method = "getCountInRange")
    private long getInChunk(Predicate<RegistryAccess.RegistryEntry<PoiType>> typePredicate, BlockPos pos, int radius, PoiManager.Occupancy occupationStatus, Operation<Long> original) {
        synchronized (async$lock) {
            return original.call(typePredicate, pos, radius, occupationStatus);
        }
    }

    @WrapMethod(method = "findClosest(Ljava/util/function/Predicate;Lnet/minecraft/core/BlockPos;ILnet/minecraft/world/entity/ai/village/poi/PoiManager$Occupancy;)Ljava/util/Optional;")
    private Optional<BlockPos> getNearestPosition(Predicate<RegistryAccess.RegistryEntry<PoiType>> typePredicate, BlockPos pos, int radius, PoiManager.Occupancy occupationStatus, Operation<Optional<BlockPos>> original) {
        synchronized (async$lock) {
            return original.call(typePredicate, pos, radius, occupationStatus);
        }
    }

    @WrapMethod(method = "findClosest(Ljava/util/function/Predicate;Ljava/util/function/Predicate;Lnet/minecraft/core/BlockPos;ILnet/minecraft/world/entity/ai/village/poi/PoiManager$Occupancy;)Ljava/util/Optional;")
    private Optional<BlockPos> getNearestPosition(Predicate<RegistryAccess.RegistryEntry<PoiType>> typePredicate, Predicate<BlockPos> posPredicate, BlockPos pos, int radius, PoiManager.Occupancy occupationStatus, Operation<Optional<BlockPos>> original) {
        synchronized (async$lock) {
            return original.call(typePredicate, posPredicate, pos, radius, occupationStatus);
        }
    }

    @WrapMethod(method = "getRandom(Ljava/util/function/Predicate;Ljava/util/function/Predicate;Lnet/minecraft/world/entity/ai/village/poi/PoiManager$Occupancy;Lnet/minecraft/core/BlockPos;ILnet/minecraft/util/RandomSource;)Ljava/util/Optional;")
    private Optional<BlockPos> getNearestPosition(Predicate<Holder<PoiType>> typePredicate, Predicate<BlockPos> positionPredicate, PoiManager.Occupancy occupationStatus, BlockPos pos, int radius, RandomSource random, Operation<Optional<BlockPos>> original) {
        synchronized (async$lock) {
            return original.call(typePredicate, positionPredicate, occupationStatus, pos, radius, random);
        }
    }
}