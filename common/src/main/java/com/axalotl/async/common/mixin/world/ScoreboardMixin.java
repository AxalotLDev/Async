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

    // Async's async mob spawning (enableAsyncSpawn) runs mob spawn logic - including
    // third-party team assignment such as Incendium's infernal mob team - on a background
    // thread pool concurrently with the main thread. Vanilla Scoreboard's team maps
    // (playersTeamMap/teamsByName) aren't thread-safe, so racing team add/remove/lookup
    // calls can desync a player's tracked team, producing a bad ClientboundSetPlayerTeamPacket
    // and an IllegalStateException kick on the client (#194). Wrap the methods that
    // read/mutate team membership so every caller, regardless of thread, is mutually
    // exclusive on the scoreboard's own monitor - mirroring the synchronized(this)/
    // synchronized(lock) pattern used elsewhere in this package for the same reason.
    @WrapMethod(method = "getPlayersTeam")
    private PlayerTeam getPlayersTeam(String scoreHolder, Operation<PlayerTeam> original) {
        synchronized (this) {
            return original.call(scoreHolder);
        }
    }

    @WrapMethod(method = "addPlayerToTeam")
    private boolean addPlayerToTeam(String scoreHolder, PlayerTeam playerTeam, Operation<Boolean> original) {
        synchronized (this) {
            return original.call(scoreHolder, playerTeam);
        }
    }

    @WrapMethod(method = "removePlayerFromTeam(Ljava/lang/String;)V")
    private void removePlayerFromTeam(String scoreHolder, Operation<Void> original) {
        synchronized (this) {
            original.call(scoreHolder);
        }
    }

    @WrapMethod(method = "removePlayerFromTeam(Ljava/lang/String;Lnet/minecraft/world/scores/PlayerTeam;)V")
    private void removePlayerFromTeam(String scoreHolder, PlayerTeam playerTeam, Operation<Void> original) {
        synchronized (this) {
            original.call(scoreHolder, playerTeam);
        }
    }

    @WrapMethod(method = "addPlayerTeam")
    private PlayerTeam addPlayerTeam(String name, Operation<PlayerTeam> original) {
        synchronized (this) {
            return original.call(name);
        }
    }

    @WrapMethod(method = "removePlayerTeam")
    private void removePlayerTeam(PlayerTeam playerTeam, Operation<Void> original) {
        synchronized (this) {
            original.call(playerTeam);
        }
    }
}