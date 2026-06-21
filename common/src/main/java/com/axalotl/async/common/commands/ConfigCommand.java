package com.axalotl.async.common.commands;

import com.axalotl.async.common.config.AsyncConfig;
import com.axalotl.async.common.platform.PlatformPermission;
import com.axalotl.async.common.platform.PlatformUtils;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;

import java.util.Set;

import static com.axalotl.async.common.commands.AsyncCommand.prefix;
import static net.minecraft.commands.Commands.literal;

public class ConfigCommand {

    public static LiteralArgumentBuilder<CommandSourceStack> registerConfig(LiteralArgumentBuilder<CommandSourceStack> root) {
        return root.then(literal("config")
                .requires(PlatformPermission.require("command.config", 4))
                .then(buildToggleCommand())
                .then(buildReloadCommand())
                .then(buildSynchronizedEntitiesCommand())
                .then(buildAsyncEntitySpawnCommand())
                .then(buildAsyncRandomTicksCommand())
        );
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildReloadCommand() {
        return literal("reload")
                .executes(ctx -> {
                    try {
                        PlatformUtils.reloadConfig();

                        MutableComponent msg = prefix.copy()
                                .append(Component.literal("Configuration reloaded successfully.")
                                        .withStyle(style -> style.withColor(ChatFormatting.GREEN)));
                        ctx.getSource().sendSuccess(() -> msg, true);

                    } catch (Exception e) {
                        MutableComponent msg = prefix.copy()
                                .append(Component.literal("Failed to reload config: " + e.getMessage())
                                        .withStyle(style -> style.withColor(ChatFormatting.RED)));
                        ctx.getSource().sendFailure(msg);
                    }
                    return 1;
                });
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildToggleCommand() {
        return literal("toggle").executes(ctx -> {
            AsyncConfig.disabled = !AsyncConfig.disabled;
            PlatformUtils.saveConfig();

            sendMessage(ctx, "Async is now ",
                    AsyncConfig.disabled ? "disabled" : "enabled",
                    true);
            return 1;
        });
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildSynchronizedEntitiesCommand() {
        return literal("synchronizedEntities")
                .executes(ctx -> {
                    displaySynchronizedEntities(ctx);
                    return 1;
                })
                .then(buildAddEntityCommand())
                .then(buildRemoveEntityCommand());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildAddEntityCommand() {
        return literal("add")
                .then(Commands.argument("entity", IdentifierArgument.id())
                        .suggests((_, builder) -> {
                            BuiltInRegistries.ENTITY_TYPE.keySet().forEach(
                                    id -> builder.suggest(id.toString())
                            );
                            BuiltInRegistries.ENTITY_TYPE.keySet().stream()
                                    .map(Identifier::getNamespace)
                                    .distinct()
                                    .forEach(ns -> builder.suggest(ns + ":*"));
                            return builder.buildFuture();
                        })
                        .executes(ConfigCommand::addEntity)
                )
                .then(Commands.argument("namespace", StringArgumentType.greedyString())
                        .executes(ConfigCommand::addNamespace)
                );
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildRemoveEntityCommand() {
        return literal("remove")
                .then(Commands.argument("entity", IdentifierArgument.id())
                        .suggests((_, builder) -> {
                            AsyncConfig.synchronizedEntities.forEach(builder::suggest);
                            return builder.buildFuture();
                        })
                        .executes(ConfigCommand::removeEntity)
                )
                .then(Commands.argument("namespace", StringArgumentType.greedyString())
                        .executes(ConfigCommand::removeNamespace)
                );
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildAsyncEntitySpawnCommand() {
        return literal("setAsyncEntitySpawn")
                .executes(ctx -> {
                    sendMessage(ctx, "Current value of async entity spawn: ",
                            String.valueOf(AsyncConfig.enableAsyncSpawn),
                            false);
                    return 1;
                })
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> {
                            boolean value = BoolArgumentType.getBool(ctx, "value");
                            AsyncConfig.enableAsyncSpawn = value;
                            PlatformUtils.saveConfig();

                            sendMessage(ctx, "Async Entity Spawn set to ",
                                    String.valueOf(value),
                                    true);
                            return 1;
                        })
                );
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildAsyncRandomTicksCommand() {
        return literal("setAsyncRandomTicks")
                .executes(ctx -> {
                    sendMessage(ctx, "Current value of async random ticks: ",
                            String.valueOf(AsyncConfig.enableAsyncRandomTicks),
                            false);
                    return 1;
                })
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> {
                            boolean value = BoolArgumentType.getBool(ctx, "value");
                            AsyncConfig.enableAsyncRandomTicks = value;
                            PlatformUtils.saveConfig();

                            sendMessage(ctx, "Async Random Ticks set to ",
                                    String.valueOf(value),
                                    true);
                            return 1;
                        })
                );
    }

    private static void displaySynchronizedEntities(CommandContext<CommandSourceStack> ctx) {
        Set<String> entities = AsyncConfig.synchronizedEntities;
        MutableComponent message = prefix.copy()
                .append(Component.literal("Synchronized Entities: ")
                        .withStyle(style -> style.withColor(ChatFormatting.WHITE)));

        if (entities.isEmpty()) {
            message.append(Component.literal("No entities synchronized.")
                    .withStyle(style -> style.withColor(ChatFormatting.RED)));
        } else {
            message.append(Component.literal("\n"));
            entities.forEach(entity ->
                    message.append(Component.literal("- ").withStyle(style -> style.withColor(ChatFormatting.GREEN)))
                            .append(Component.literal(entity).withStyle(style -> style.withColor(ChatFormatting.YELLOW)))
                            .append(Component.literal("\n"))
            );
        }

        ctx.getSource().sendSuccess(() -> message, false);
    }

    private static int addEntity(CommandContext<CommandSourceStack> ctx) {
        Identifier id = IdentifierArgument.getId(ctx, "entity");

        if (!BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
            sendErrorMessage(ctx, "Error entity class ", id.toString(), " does not exist.");
            return 1;
        }

        if (AsyncConfig.synchronizedEntities.contains(id.toString())) {
            sendErrorMessage(ctx, "Error entity class ", id.toString(), " is already synchronized.");
            return 1;
        }

        AsyncConfig.syncEntity(id.toString());
        sendMessage(ctx, "Entity class ", id.toString(),
                " has been added to the synchronized list.");
        return 1;
    }

    private static int addNamespace(CommandContext<CommandSourceStack> ctx) {
        String namespace = StringArgumentType.getString(ctx, "namespace");

        if (AsyncConfig.matchesExistingNamespaceWildcard(namespace)) {
            AsyncConfig.syncEntity(namespace);
            sendMessage(ctx, "All entities with namespace ", namespace,
                    " has been added to the synchronized list.");
        } else {
            sendErrorMessage(ctx, "Error namespace ", namespace, " does not exist.");
        }
        return 1;
    }

    private static int removeEntity(CommandContext<CommandSourceStack> ctx) {
        Identifier id = IdentifierArgument.getId(ctx, "entity");

        if (!AsyncConfig.synchronizedEntities.contains(id.toString())) {
            sendErrorMessage(ctx, "Error entity class ", id.toString(), " is not in the synchronized list.");
            return 1;
        }

        AsyncConfig.removeEntity(id.toString());
        sendMessage(ctx, "Entity class ", id.toString(),
                " has been removed from synchronized list.");
        return 1;
    }

    private static int removeNamespace(CommandContext<CommandSourceStack> ctx) {
        String namespace = StringArgumentType.getString(ctx, "namespace");

        if (!AsyncConfig.synchronizedEntities.contains(namespace)) {
            sendErrorMessage(ctx, "Error namespace ", namespace, " is not in the synchronized list.");
            return 1;
        }

        AsyncConfig.removeEntity(namespace);
        sendMessage(ctx, "All entities with namespace ", namespace,
                " has been removed from synchronized list.");
        return 1;
    }

    private static void sendMessage(CommandContext<CommandSourceStack> ctx, String prefix,
                                    String highlight, boolean broadcast) {
        MutableComponent message = AsyncCommand.prefix.copy()
                .append(Component.literal(prefix).withStyle(style -> style.withColor(ChatFormatting.WHITE)))
                .append(Component.literal(highlight).withStyle(style -> style.withColor(ChatFormatting.GREEN)));
        ctx.getSource().sendSuccess(() -> message, broadcast);
    }

    private static void sendMessage(CommandContext<CommandSourceStack> ctx, String prefix,
                                    String highlight, String suffix) {
        MutableComponent message = AsyncCommand.prefix.copy()
                .append(Component.literal(prefix).withStyle(style -> style.withColor(ChatFormatting.WHITE)))
                .append(Component.literal(highlight).withStyle(style -> style.withColor(ChatFormatting.GREEN)))
                .append(Component.literal(suffix).withStyle(style -> style.withColor(ChatFormatting.WHITE)));
        ctx.getSource().sendSuccess(() -> message, true);
    }

    private static void sendErrorMessage(CommandContext<CommandSourceStack> ctx, String prefix,
                                         String error, String suffix) {
        MutableComponent message = AsyncCommand.prefix.copy()
                .append(Component.literal(prefix).withStyle(style -> style.withColor(ChatFormatting.RED)))
                .append(Component.literal(error).withStyle(style -> style.withColor(ChatFormatting.RED)))
                .append(Component.literal(suffix).withStyle(style -> style.withColor(ChatFormatting.RED)));
        ctx.getSource().sendFailure(message);
    }
}