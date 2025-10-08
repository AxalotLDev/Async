package com.axalotl.async.common.commands;

import com.axalotl.async.common.config.AsyncConfig;
import com.axalotl.async.common.platform.PlatformEvents;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.synchronization.SuggestionProviders;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

import java.util.Set;

import static com.axalotl.async.common.commands.AsyncCommand.prefix;
import static net.minecraft.commands.Commands.literal;

public class ConfigCommand {
    public static LiteralArgumentBuilder<CommandSourceStack> registerConfig(LiteralArgumentBuilder<CommandSourceStack> root) {
        return root.then(literal("config")
                .then(literal("toggle").requires(cmdSrc -> cmdSrc.hasPermission(4)).executes(cmdCtx -> {
                    AsyncConfig.disabled = !AsyncConfig.disabled;
                    PlatformEvents.getInstance().saveConfig();
                    MutableComponent message = prefix.copy().append(Component.literal("Async is now ").withStyle(style -> style.withColor(ChatFormatting.WHITE)))
                            .append(Component.literal(AsyncConfig.disabled ? "disabled" : "enabled").withStyle(style -> style.withColor(ChatFormatting.GREEN)));
                    cmdCtx.getSource().sendSuccess(() -> message, true);
                    return 1;
                }))
                .then(literal("synchronizedEntities")
                        .requires(cmdSrc -> cmdSrc.hasPermission(4))
                        .executes(cmdCtx -> {
                            Set<ResourceLocation> currentValue = AsyncConfig.synchronizedEntities;
                            MutableComponent message = prefix.copy().append(Component.literal("Synchronized Entities: ").withStyle(style -> style.withColor(ChatFormatting.WHITE)));
                            if (currentValue.isEmpty()) {
                                message.append(Component.literal("No entities synchronized.").withStyle(style -> style.withColor(ChatFormatting.RED)));
                            } else {
                                message.append(Component.literal("\n").withStyle(style -> style.withColor(ChatFormatting.WHITE)));
                                for (ResourceLocation entity : currentValue) {
                                    message.append(Component.literal("- ").withStyle(style -> style.withColor(ChatFormatting.GREEN)))
                                            .append(Component.literal(entity.toString()).withStyle(style -> style.withColor(ChatFormatting.YELLOW)))
                                            .append(Component.literal("\n"));
                                }
                            }
                            cmdCtx.getSource().sendSuccess(() -> message, false);
                            return 1;
                        })
                        .then(literal("add")
                                .then(Commands.argument("entity", ResourceLocationArgument.id()).suggests(SuggestionProviders.cast(SuggestionProviders.SUMMONABLE_ENTITIES)).executes(cmdCtx -> {
                                    ResourceLocation id = ResourceLocationArgument.getId(cmdCtx, "entity");
                                    if (AsyncConfig.synchronizedEntities.contains(id)) {
                                        MutableComponent message = prefix.copy()
                                                .append(Component.literal("Error entity class ").withStyle(style -> style.withColor(ChatFormatting.RED)))
                                                .append(Component.literal(id.toString()).withStyle(style -> style.withColor(ChatFormatting.RED)))
                                                .append(Component.literal(" is already synchronized.").withStyle(style -> style.withColor(ChatFormatting.RED)));
                                        cmdCtx.getSource().sendSuccess(() -> message, true);
                                        return 1;
                                    }
                                    AsyncConfig.syncEntity(id);
                                    MutableComponent message = prefix.copy()
                                            .append(Component.literal("Entity class ").withStyle(style -> style.withColor(ChatFormatting.WHITE)))
                                            .append(Component.literal(id.toString()).withStyle(style -> style.withColor(ChatFormatting.GREEN)))
                                            .append(Component.literal(" has been added to the synchronized list.").withStyle(style -> style.withColor(ChatFormatting.WHITE)));
                                    cmdCtx.getSource().sendSuccess(() -> message, true);
                                    return 1;
                                })))
                        .then(literal("remove")
                                .then(Commands.argument("entity", ResourceLocationArgument.id())
                                        .suggests((context, builder) -> {
                                            AsyncConfig.synchronizedEntities.forEach(id -> builder.suggest(id.toString()));
                                            return builder.buildFuture();
                                        })
                                        .executes(cmdCtx -> {
                                            ResourceLocation identifier = cmdCtx.getArgument("entity", ResourceLocation.class);
                                            if (!AsyncConfig.synchronizedEntities.contains(identifier)) {
                                                MutableComponent message = prefix.copy()
                                                        .append(Component.literal("Error entity class ").withStyle(style -> style.withColor(ChatFormatting.RED)))
                                                        .append(Component.literal(identifier.toString()).withStyle(style -> style.withColor(ChatFormatting.RED)))
                                                        .append(Component.literal(" is not in the synchronized list.").withStyle(style -> style.withColor(ChatFormatting.RED)));
                                                cmdCtx.getSource().sendSuccess(() -> message, true);
                                                return 1;
                                            }
                                            AsyncConfig.asyncEntity(identifier);
                                            MutableComponent message = prefix.copy()
                                                    .append(Component.literal("Entity class ").withStyle(style -> style.withColor(ChatFormatting.WHITE)))
                                                    .append(Component.literal(identifier.toString()).withStyle(style -> style.withColor(ChatFormatting.GREEN)))
                                                    .append(Component.literal(" has been removed from synchronized list.").withStyle(style -> style.withColor(ChatFormatting.WHITE)));
                                            cmdCtx.getSource().sendSuccess(() -> message, true);
                                            return 1;
                                        }))))
                .then(literal("setAsyncEntitySpawn").requires(cmdSrc -> cmdSrc.hasPermission(4))
                        .executes(cmdCtx -> {
                            boolean currentValue = AsyncConfig.enableAsyncSpawn;
                            MutableComponent message = prefix.copy().append(Component.literal("Current value of async entity spawn: ").withStyle(style -> style.withColor(ChatFormatting.WHITE)))
                                    .append(Component.literal(String.valueOf(currentValue)).withStyle(style -> style.withColor(ChatFormatting.GREEN)));
                            cmdCtx.getSource().sendSuccess(() -> message, false);
                            return 1;
                        })
                        .then(Commands.argument("value", BoolArgumentType.bool()).executes(cmdCtx -> {
                            boolean value = BoolArgumentType.getBool(cmdCtx, "value");
                            AsyncConfig.enableAsyncSpawn = value;
                            PlatformEvents.getInstance().saveConfig();
                            MutableComponent message = prefix.copy().append(Component.literal("Async Entity Spawn set to ").withStyle(style -> style.withColor(ChatFormatting.WHITE)))
                                    .append(Component.literal(String.valueOf(value)).withStyle(style -> style.withColor(ChatFormatting.GREEN)));
                            cmdCtx.getSource().sendSuccess(() -> message, true);
                            return 1;
                        }))
                )
                .then(literal("setAsyncRandomTicks").requires(cmdSrc -> cmdSrc.hasPermission(4))
                        .executes(cmdCtx -> {
                            boolean currentValue = AsyncConfig.enableAsyncRandomTicks;
                            MutableComponent message = prefix.copy().append(Component.literal("Current value of async random ticks: ").withStyle(style -> style.withColor(ChatFormatting.WHITE)))
                                    .append(Component.literal(String.valueOf(currentValue)).withStyle(style -> style.withColor(ChatFormatting.GREEN)));
                            cmdCtx.getSource().sendSuccess(() -> message, false);
                            return 1;
                        })
                        .then(Commands.argument("value", BoolArgumentType.bool()).executes(cmdCtx -> {
                            boolean value = BoolArgumentType.getBool(cmdCtx, "value");
                            AsyncConfig.enableAsyncRandomTicks = value;
                            PlatformEvents.getInstance().saveConfig();
                            MutableComponent message = prefix.copy().append(Component.literal("Async Random Ticks set to ").withStyle(style -> style.withColor(ChatFormatting.WHITE)))
                                    .append(Component.literal(String.valueOf(value)).withStyle(style -> style.withColor(ChatFormatting.GREEN)));
                            cmdCtx.getSource().sendSuccess(() -> message, true);
                            return 1;
                        }))
                ));
    }
}