package com.axalotl.async.common.mixin.entity.sensor;

import com.axalotl.async.common.utils.FastBitRadixSort;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities;
import net.minecraft.world.entity.ai.sensing.NearestLivingEntitySensor;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.ArrayList;
import java.util.List;

@Mixin(value = NearestLivingEntitySensor.class, priority = 1500)
public class NearestLivingEntitiesSensorMixin<T extends LivingEntity> {
    @Unique
    private static final FastBitRadixSort entitySorter = new FastBitRadixSort();

    @WrapMethod(method = "doTick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;)V")
    private void doTick(ServerLevel level, T body, Operation<Void> original) {
        double d0 = body.getAttributeValue(Attributes.FOLLOW_RANGE);
        AABB aabb = body.getBoundingBox().inflate(d0, d0, d0);

        List<LivingEntity> list = level.getEntitiesOfClass(
                LivingEntity.class,
                aabb,
                e -> e != body && e.isAlive()
        );

        Object[] arr = list.toArray();
        entitySorter.sort(arr, arr.length, body.position());

        List<LivingEntity> sorted = new ArrayList<>(arr.length);
        for (Object o : arr) sorted.add((LivingEntity) o);

        Brain<?> brain = body.getBrain();
        brain.setMemory(MemoryModuleType.NEAREST_LIVING_ENTITIES, sorted);
        brain.setMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES,
                new NearestVisibleLivingEntities(level, body, sorted));
    }
}