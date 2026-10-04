package com.axalotl.async.common.mixin.world;

import com.axalotl.async.api.utils.ConcurrentCollections;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Score;
import net.minecraft.world.scores.Scoreboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Map;

@Mixin(Scoreboard.class)
public class ScoreboardMixin {

    @Shadow
    private final Map<String, Map<Objective, Score>> playerScores = ConcurrentCollections.newHashMap();

    @WrapMethod(method = "getPlayersTeam")
    private PlayerTeam getPlayersTeam(String name, Operation<PlayerTeam> original) {
        synchronized (this) {
            return original.call(name);
        }
    }

    @WrapMethod(method = "addPlayerToTeam")
    private boolean addPlayerToTeam(String player, PlayerTeam team, Operation<Boolean> original) {
        synchronized (this) {
            return original.call(player, team);
        }
    }

    @WrapMethod(method = "removePlayerTeam")
    private void removePlayerFromTeam(PlayerTeam team, Operation<Void> original) {
        synchronized (this) {
            original.call(team);
        }
    }

    @WrapMethod(method = "removePlayerFromTeam(Ljava/lang/String;Lnet/minecraft/world/scores/PlayerTeam;)V")
    private void removePlayerFromTeam(String player, PlayerTeam team, Operation<Void> original) {
        synchronized (this) {
            original.call(player, team);
        }
    }

    @WrapMethod(method = "addPlayerTeam")
    private PlayerTeam addPlayerTeam(String name, Operation<PlayerTeam> original) {
        synchronized (this) {
            return original.call(name);
        }
    }

    @WrapMethod(method = "removePlayerTeam")
    private void removePlayerTeam(PlayerTeam team, Operation<Void> original) {
        synchronized (this) {
            original.call(team);
        }
    }
}