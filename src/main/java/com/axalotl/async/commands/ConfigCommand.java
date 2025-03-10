package com.axalotl.async.commands;

import com.axalotl.async.config.AsyncConfig;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.command.suggestion.SuggestionProviders;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.Set;

import static com.axalotl.async.commands.AsyncCommand.prefix;
import static net.minecraft.server.command.CommandManager.literal;

public class ConfigCommand {
    public static LiteralArgumentBuilder<ServerCommandSource> registerConfig(LiteralArgumentBuilder<ServerCommandSource> root) {
        return root.then(literal("config")
                .then(CommandManager.literal("toggle").requires(cmdSrc -> cmdSrc.hasPermissionLevel(4)).executes(cmdCtx -> {
                    AsyncConfig.disabled = !AsyncConfig.disabled;
                    AsyncConfig.saveConfig();
                    MutableText message = prefix.copy().append(Text.literal("Async is now ").styled(style -> style.withColor(Formatting.WHITE)))
                            .append(Text.literal(AsyncConfig.disabled ? "disabled" : "enabled").styled(style -> style.withColor(Formatting.GREEN)));
                    cmdCtx.getSource().sendFeedback(() -> message, true);
                    return 1;
                }))
                .then(literal("synchronizedEntities")
                        .requires(cmdSrc -> cmdSrc.hasPermissionLevel(4))
                        .executes(cmdCtx -> {
                            Set<Identifier> currentValue = AsyncConfig.synchronizedEntities;
                            MutableText message = prefix.copy().append(Text.literal("Synchronized Entities: ").styled(style -> style.withColor(Formatting.WHITE)));
                            if (currentValue.isEmpty()) {
                                message.append(Text.literal("No entities synchronized.").styled(style -> style.withColor(Formatting.RED)));
                            } else {
                                message.append(Text.literal("\n").styled(style -> style.withColor(Formatting.WHITE)));
                                for (Identifier entity : currentValue) {
                                    message.append(Text.literal("- ").styled(style -> style.withColor(Formatting.GREEN)))
                                            .append(Text.literal(entity.toString()).styled(style -> style.withColor(Formatting.YELLOW)))
                                            .append(Text.literal("\n"));
                                }
                            }
                            cmdCtx.getSource().sendFeedback(() -> message, false);
                            return 1;
                        })
                        .then(literal("add")
                                .then(CommandManager.argument("entity", IdentifierArgumentType.identifier()).suggests(SuggestionProviders.SUMMONABLE_ENTITIES).executes(cmdCtx -> {
                                    Identifier id = IdentifierArgumentType.getIdentifier(cmdCtx, "entity");

                                    if (AsyncConfig.synchronizedEntities.contains(id)) {
                                        MutableText message = prefix.copy()
                                                .append(Text.literal("Error entity class ").styled(style -> style.withColor(Formatting.RED)))
                                                .append(Text.literal(id.toString()).styled(style -> style.withColor(Formatting.RED)))
                                                .append(Text.literal(" is already synchronized.").styled(style -> style.withColor(Formatting.RED)));
                                        cmdCtx.getSource().sendFeedback(() -> message, true);
                                        return 1;
                                    }

                                    AsyncConfig.syncEntity(id);
                                    MutableText message = prefix.copy()
                                            .append(Text.literal("Entity class ").styled(style -> style.withColor(Formatting.WHITE)))
                                            .append(Text.literal(id.toString()).styled(style -> style.withColor(Formatting.GREEN)))
                                            .append(Text.literal(" has been added to the synchronized list.").styled(style -> style.withColor(Formatting.WHITE)));
                                    cmdCtx.getSource().sendFeedback(() -> message, true);
                                    return 1;
                                })))
                        .then(literal("remove")
                                .then(CommandManager.argument("entity", IdentifierArgumentType.identifier())
                                        .suggests((context, builder) -> {
                                            AsyncConfig.synchronizedEntities.forEach(id -> builder.suggest(id.toString()));
                                            return builder.buildFuture();
                                        })
                                        .executes(cmdCtx -> {
                                            Identifier identifier = cmdCtx.getArgument("entity", Identifier.class);

                                            if (!AsyncConfig.synchronizedEntities.contains(identifier)) {
                                                MutableText message = prefix.copy()
                                                        .append(Text.literal("Error entity class ").styled(style -> style.withColor(Formatting.RED)))
                                                        .append(Text.literal(identifier.toString()).styled(style -> style.withColor(Formatting.RED)))
                                                        .append(Text.literal(" is not in the synchronized list.").styled(style -> style.withColor(Formatting.RED)));
                                                cmdCtx.getSource().sendFeedback(() -> message, true);
                                                return 1;
                                            }

                                            AsyncConfig.asyncEntity(identifier);
                                            MutableText message = prefix.copy()
                                                    .append(Text.literal("Entity class ").styled(style -> style.withColor(Formatting.WHITE)))
                                                    .append(Text.literal(identifier.toString()).styled(style -> style.withColor(Formatting.GREEN)))
                                                    .append(Text.literal(" has been removed from synchronized list.").styled(style -> style.withColor(Formatting.WHITE)));
                                            cmdCtx.getSource().sendFeedback(() -> message, true);
                                            return 1;
                                        }))))
                .then(CommandManager.literal("setEntityMoveSync").requires(cmdSrc -> cmdSrc.hasPermissionLevel(4))
                        .executes(cmdCtx -> {
                            boolean currentValue = AsyncConfig.enableEntityMoveSync;
                            MutableText message = prefix.copy().append(Text.literal("Current value of entity move sync: ").styled(style -> style.withColor(Formatting.WHITE)))
                                    .append(Text.literal(String.valueOf(currentValue)).styled(style -> style.withColor(Formatting.GREEN)));
                            cmdCtx.getSource().sendFeedback(() -> message, false);
                            return 1;
                        })
                        .then(CommandManager.argument("value", BoolArgumentType.bool()).executes(cmdCtx -> {
                            boolean value = BoolArgumentType.getBool(cmdCtx, "value");
                            AsyncConfig.enableEntityMoveSync = value;
                            AsyncConfig.saveConfig();
                            MutableText message = prefix.copy().append(Text.literal("Entity move sync set to ").styled(style -> style.withColor(Formatting.WHITE)))
                                    .append(Text.literal(String.valueOf(value)).styled(style -> style.withColor(Formatting.GREEN)));
                            cmdCtx.getSource().sendFeedback(() -> message, true);
                            return 1;
                        })))
                .then(CommandManager.literal("setAsyncEntitySpawn").requires(cmdSrc -> cmdSrc.hasPermissionLevel(4))
                        .executes(cmdCtx -> {
                            boolean currentValue = AsyncConfig.enableAsyncSpawn;
                            MutableText message = prefix.copy().append(Text.literal("Current value of async entity spawn: ").styled(style -> style.withColor(Formatting.WHITE)))
                                    .append(Text.literal(String.valueOf(currentValue)).styled(style -> style.withColor(Formatting.GREEN)));
                            cmdCtx.getSource().sendFeedback(() -> message, false);
                            return 1;
                        })
                        .then(CommandManager.argument("value", BoolArgumentType.bool()).executes(cmdCtx -> {
                            boolean value = BoolArgumentType.getBool(cmdCtx, "value");
                            AsyncConfig.enableAsyncSpawn = value;
                            AsyncConfig.saveConfig();
                            MutableText message = prefix.copy().append(Text.literal("Async Entity Spawn set to ").styled(style -> style.withColor(Formatting.WHITE)))
                                    .append(Text.literal(String.valueOf(value)).styled(style -> style.withColor(Formatting.GREEN)));
                            cmdCtx.getSource().sendFeedback(() -> message, true);
                            return 1;
                        })))
        );
    }
}
