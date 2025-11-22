package com.axalotl.async.fabric.commands;

import com.axalotl.async.common.AsyncCommon;
import com.axalotl.async.common.commands.ConfigCommand;
import com.axalotl.async.common.commands.StatsCommand;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.commands.CommandSourceStack;

import static net.minecraft.commands.Commands.literal;

public class AsyncFabricCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> main = literal("async");
        main = ConfigCommand.registerConfig(main).requires(source -> hasPermission(source, "command.config", 4));
        main = StatsCommand.registerStatus(main).requires(source -> hasPermission(source, "command.statistics", 2));
        dispatcher.register(main);
    }

    public static boolean hasPermission(CommandSourceStack source, String node, int level) {
        String permission = String.format("%s.%s", AsyncCommon.MODID, node);
        return Permissions.check(source, permission, level);
    }
}