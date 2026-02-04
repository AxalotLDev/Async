package com.axalotl.async.common.commands;

import com.axalotl.async.common.ParallelProcessor;
import com.axalotl.async.common.config.AsyncConfig;
import com.axalotl.async.common.platform.Permission;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EntityType;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static com.axalotl.async.common.commands.AsyncCommand.prefix;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class StatsCommand {

    public static LiteralArgumentBuilder<CommandSourceStack> registerStatus(LiteralArgumentBuilder<CommandSourceStack> root) {
        return root.then(literal("stats").requires(Permission.require("command.statistics", 0))
                .executes(cmdCtx -> {
                    showGeneralStats(cmdCtx.getSource());
                    return 1;
                })
                .then(literal("entity")
                        .executes(cmdCtx -> {
                            showEntityStats(cmdCtx.getSource(), 0);
                            return 1;
                        })
                        .then(argument("count", IntegerArgumentType.integer(1, 100))
                                .executes(cmdCtx -> {
                                    int count = IntegerArgumentType.getInteger(cmdCtx, "count");
                                    showEntityStats(cmdCtx.getSource(), count);
                                    return 1;
                                }))));
    }

    private static void showGeneralStats(CommandSourceStack source) {
        MutableComponent message = prefix.copy()
                .append(Component.literal("Async Status: ")
                        .withStyle(style -> style.withColor(ChatFormatting.WHITE)))
                .append(Component.literal(AsyncConfig.disabled ? "Disabled" : "Enabled")
                        .withStyle(style -> style.withColor(
                                AsyncConfig.disabled ? ChatFormatting.RED : ChatFormatting.GREEN)));

        source.sendSuccess(() -> message, true);
    }

    private static void showEntityStats(CommandSourceStack source, int topCount) {
        MinecraftServer server = source.getServer();
        server.execute(() -> {
            Map<EntityType<?>, Integer> entityTypeCounts = new HashMap<>();
            Map<EntityType<?>, Integer> asyncEntityTypeCounts = new HashMap<>();
            AtomicInteger totalEntities = new AtomicInteger();
            AtomicInteger totalAsyncEntities = new AtomicInteger();

            MutableComponent message = prefix.copy()
                    .append(Component.literal("Entity Statistics ")
                            .withStyle(style -> style.withColor(ChatFormatting.GOLD)));

            server.getAllLevels().forEach(world -> {
                String worldName = world.dimensionTypeRegistration().getRegisteredName();
                AtomicInteger worldCount = new AtomicInteger();
                AtomicInteger asyncCount = new AtomicInteger();

                world.getAllEntities().forEach(entity -> {
                    if (entity != null && entity.isAlive()) {
                        EntityType<?> type = entity.getType();

                        worldCount.incrementAndGet();
                        totalEntities.incrementAndGet();
                        entityTypeCounts.merge(type, 1, Integer::sum);

                        if (!ParallelProcessor.shouldTickSynchronously(entity)) {
                            asyncCount.incrementAndGet();
                            totalAsyncEntities.incrementAndGet();
                            asyncEntityTypeCounts.merge(type, 1, Integer::sum);
                        }
                    }
                });

                message.append(Component.literal("\n" + worldName + ": ")
                                .withStyle(style -> style.withColor(ChatFormatting.YELLOW)))
                        .append(Component.literal(String.valueOf(worldCount.get()))
                                .withStyle(style -> style.withColor(ChatFormatting.GREEN)))
                        .append(Component.literal(" entities (")
                                .withStyle(style -> style.withColor(ChatFormatting.GRAY)))
                        .append(Component.literal(String.valueOf(asyncCount.get()))
                                .withStyle(style -> style.withColor(ChatFormatting.AQUA)))
                        .append(Component.literal(" async)")
                                .withStyle(style -> style.withColor(ChatFormatting.GRAY)));
            });

            message.append(Component.literal("\nTotal Entities: ")
                            .withStyle(style -> style.withColor(ChatFormatting.WHITE)))
                    .append(Component.literal(String.valueOf(totalEntities.get()))
                            .withStyle(style -> style.withColor(ChatFormatting.GOLD)))
                    .append(Component.literal(" (")
                            .withStyle(style -> style.withColor(ChatFormatting.GRAY)))
                    .append(Component.literal(String.valueOf(totalAsyncEntities.get()))
                            .withStyle(style -> style.withColor(ChatFormatting.AQUA)))
                    .append(Component.literal(" async)")
                            .withStyle(style -> style.withColor(ChatFormatting.GRAY)));

            if (topCount > 0) {
                List<Map.Entry<EntityType<?>, Integer>> sorted = new ArrayList<>(entityTypeCounts.entrySet());
                sorted.sort(Map.Entry.<EntityType<?>, Integer>comparingByValue().reversed());

                if (topCount < sorted.size()) {
                    sorted = sorted.subList(0, topCount);
                }

                if (!sorted.isEmpty()) {
                    message.append(Component.literal("\n\nTop " + sorted.size() + " Entity Types:")
                            .withStyle(style -> style.withColor(ChatFormatting.GOLD)));

                    int rank = 1;
                    for (Map.Entry<EntityType<?>, Integer> entry : sorted) {
                        EntityType<?> type = entry.getKey();
                        int count = entry.getValue();
                        int asyncCount = asyncEntityTypeCounts.getOrDefault(type, 0);

                        Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(type);

                        message.append(Component.literal("\n" + rank + ". ")
                                        .withStyle(style -> style.withColor(ChatFormatting.GRAY)))
                                .append(Component.literal(id.toLanguageKey())
                                        .withStyle(style -> style.withColor(ChatFormatting.YELLOW)))
                                .append(Component.literal(": ")
                                        .withStyle(style -> style.withColor(ChatFormatting.GRAY)))
                                .append(Component.literal(String.valueOf(count))
                                        .withStyle(style -> style.withColor(ChatFormatting.GREEN)))
                                .append(Component.literal(" (")
                                        .withStyle(style -> style.withColor(ChatFormatting.GRAY)))
                                .append(Component.literal(String.valueOf(asyncCount))
                                        .withStyle(style -> style.withColor(ChatFormatting.AQUA)))
                                .append(Component.literal(" async)")
                                        .withStyle(style -> style.withColor(ChatFormatting.GRAY)));

                        rank++;
                    }
                }
            }

            source.sendSuccess(() -> message, true);
        });
    }
}