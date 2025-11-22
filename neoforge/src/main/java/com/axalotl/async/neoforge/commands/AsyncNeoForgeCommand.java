package com.axalotl.async.neoforge.commands;

import com.axalotl.async.common.commands.ConfigCommand;
import com.axalotl.async.common.commands.StatsCommand;
import com.axalotl.async.neoforge.platform.NeoForgePermissions;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;

import static net.minecraft.commands.Commands.literal;

public class AsyncNeoForgeCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> main = literal("async");
        main = ConfigCommand.registerConfig(main).requires(source -> hasPermission(source, "command.config", 4));
        main = StatsCommand.registerStatus(main).requires(source -> hasPermission(source, "command.statistics", 2));
        dispatcher.register(main);
    }

    public static boolean hasPermission(CommandSourceStack source, String node, int level) {
        if (source.hasPermission(level)) {
            return true;
        }

        ServerPlayer player = source.getPlayer();
        PermissionNode<Boolean> permission = NeoForgePermissions.getPermissionNode(node);
        if (player == null || permission == null) {
            return false;
        }

        return PermissionAPI.getPermission(player, permission);
    }
}
