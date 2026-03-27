package com.axalotl.async.common.mixin.entity.sensor;

import com.axalotl.async.common.parallelised.utils.FastBitRadixSort;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.NearestItemSensor;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.*;

@Mixin(value = NearestItemSensor.class, priority = 1500)
public class NearestItemSensorMixin {
    @Unique
    private static final FastBitRadixSort itemSorter = new FastBitRadixSort();

    @WrapMethod(method = "doTick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/Mob;)V")
    private void doTick(ServerLevel level, Mob entity, Operation<Void> original) {
        Brain<?> brain = entity.getBrain();
        List<ItemEntity> list = level.getEntitiesOfClass(
                ItemEntity.class,
                entity.getBoundingBox().inflate(32.0F, 16.0F, 32.0F),
                _ -> true
        );

        Object[] arr = list.toArray();
        itemSorter.sort(arr, arr.length, entity.position());

        Optional<ItemEntity> optional = Arrays.stream(arr)
                .map(o -> (ItemEntity) o)
                .filter(e -> entity.wantsToPickUp(level, e.getItem()))
                .filter(e -> e.closerThan(entity, 32.0F))
                .filter(entity::hasLineOfSight)
                .findFirst();

        brain.setMemory(MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM, optional);
    }
}