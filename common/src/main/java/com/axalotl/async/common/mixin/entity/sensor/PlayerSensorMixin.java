package com.axalotl.async.common.mixin.entity.sensor;

import com.axalotl.async.common.parallelised.utils.FastBitRadixSort;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.PlayerSensor;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.ArrayList;
import java.util.List;

import static net.minecraft.world.entity.ai.sensing.Sensor.isEntityAttackable;
import static net.minecraft.world.entity.ai.sensing.Sensor.isEntityTargetable;

@Mixin(value = PlayerSensor.class, priority = 1500)
public abstract class PlayerSensorMixin {

    @Shadow
    protected abstract double getFollowDistance(LivingEntity entity);
    @Unique
    private static final FastBitRadixSort async$playerSort = new FastBitRadixSort();

    @WrapMethod(method = "doTick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;)V")
    private void doTick(ServerLevel level, LivingEntity entity, Operation<Void> original) {
        double followDist = this.getFollowDistance(entity);

        Object[] arr = level.players().stream()
                .filter(EntitySelector.NO_SPECTATORS)
                .filter(p -> entity.closerThan(p, followDist))
                .toArray();

        async$playerSort.sort(arr, arr.length, entity.position());

        List<Player> list = new ArrayList<>(arr.length);
        for (Object o : arr) list.add((Player) o);

        Brain<?> brain = entity.getBrain();
        brain.setMemory(MemoryModuleType.NEAREST_PLAYERS, list);

        List<Player> list1 = list.stream()
                .filter(p -> isEntityTargetable(level, entity, p))
                .toList();

        brain.setMemory(MemoryModuleType.NEAREST_VISIBLE_PLAYER, list1.isEmpty() ? null : list1.getFirst());

        List<Player> list2 = list1.stream()
                .filter(p -> isEntityAttackable(level, entity, p))
                .toList();

        brain.setMemory(MemoryModuleType.NEAREST_VISIBLE_ATTACKABLE_PLAYERS, list2);
        brain.setMemory(MemoryModuleType.NEAREST_VISIBLE_ATTACKABLE_PLAYER, list2.isEmpty() ? null : list2.getFirst());
    }
}
