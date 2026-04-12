package com.axalotl.async.common.mixin.entity.sensor;

import com.axalotl.async.api.utils.SensorUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.PlayerSensor;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.*;
import java.util.stream.Collectors;

import static net.minecraft.world.entity.ai.sensing.Sensor.isEntityAttackable;
import static net.minecraft.world.entity.ai.sensing.Sensor.isEntityTargetable;

@Mixin(value = PlayerSensor.class, priority = 1500)
public abstract class PlayerSensorMixin {

    @Shadow
    protected abstract double getFollowDistance(LivingEntity body);

    /**
     * @author _Axa_lotL_
     * @reason async distance cache
     */
    @Overwrite
    protected void doTick(final ServerLevel level, final LivingEntity body) {
        List<Player> players = level.players()
                .stream()
                .filter(EntitySelector.NO_SPECTATORS)
                .filter(player -> body.closerThan(player, this.getFollowDistance(body)))
                .sorted(SensorUtils.comparingDouble(body))
                .collect(Collectors.toList());
        Brain<?> brain = body.getBrain();
        brain.setMemory(MemoryModuleType.NEAREST_PLAYERS, players);
        List<Player> visiblePlayers = players.stream()
                .filter(livingEntity -> isEntityTargetable(level, body, livingEntity))
                .toList();
        brain.setMemory(MemoryModuleType.NEAREST_VISIBLE_PLAYER, visiblePlayers.isEmpty() ? null : visiblePlayers.getFirst());
        List<Player> visibleAttackablePlayers = visiblePlayers.stream().filter(livingEntity -> isEntityAttackable(level, body, livingEntity)).toList();
        brain.setMemory(MemoryModuleType.NEAREST_VISIBLE_ATTACKABLE_PLAYERS, visibleAttackablePlayers);
        brain.setMemory(MemoryModuleType.NEAREST_VISIBLE_ATTACKABLE_PLAYER, visibleAttackablePlayers.isEmpty() ? null : visibleAttackablePlayers.getFirst());
    }
}