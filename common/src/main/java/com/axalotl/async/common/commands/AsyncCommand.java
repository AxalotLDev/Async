package com.axalotl.async.common.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

import static net.minecraft.commands.Commands.literal;

public class AsyncCommand {
    public final static Component prefix = Component.literal("§8[§f\uD83C\uDF00§8]§7 ");

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> main = literal("async");
        main = ConfigCommand.registerConfig(main);
        main = StatsCommand.registerStatus(main);
        dispatcher.register(main);
    }
}