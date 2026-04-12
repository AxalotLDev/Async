package com.axalotl.async.common.mixin.entity.sensor;

import com.axalotl.async.api.utils.SensorUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.NearestItemSensor;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import java.util.*;

@Mixin(value = NearestItemSensor.class, priority = 1500)
public class NearestItemSensorMixin {

    /**
     * @author _Axa_lotL_
     * @reason async distance cache
     */
    @Overwrite
    protected void doTick(final ServerLevel level, final Mob body) {
        Brain<?> brain = body.getBrain();
        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, body.getBoundingBox().inflate(32.0, 16.0, 32.0), _ -> true);
        items.sort(SensorUtils.comparingDouble(body));
        Optional<ItemEntity> nearestVisibleLovedItem = items.stream()
                .filter(itemEntity -> body.wantsToPickUp(level, itemEntity.getItem()))
                .filter(itemEntity -> itemEntity.closerThan(body, 32.0))
                .filter(body::hasLineOfSight)
                .findFirst();
        brain.setMemory(MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM, nearestVisibleLovedItem);
    }
}