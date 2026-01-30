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

import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicInteger;

import static com.axalotl.async.common.commands.AsyncCommand.prefix;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class StatsCommand {
    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#,##0.##");

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
        MinecraftServer server = source.getServer();

        double mspt = server.getAverageTickTimeNanos() / 1_000_000.0;

        int totalEntities = 0;
        int asyncEntities = 0;
        for (var world : server.getAllLevels()) {
            for (var entity : world.getAllEntities()) {
                if (entity.isAlive()) {
                    totalEntities++;
                    if (!ParallelProcessor.shouldTickSynchronously(entity)) {
                        asyncEntities++;
                    }
                }
            }
        }

        double asyncRatio = totalEntities > 0 ? (asyncEntities * 100.0 / totalEntities) : 0;

        int threads = 0;
        if (ParallelProcessor.tickPool instanceof ForkJoinPool pool && !pool.isShutdown()) {
            threads = pool.getParallelism();
        }

        boolean enabled = !AsyncConfig.disabled;
        boolean asyncSpawn = AsyncConfig.enableAsyncSpawn;
        boolean asyncRandomTicks = AsyncConfig.enableAsyncRandomTicks;

        MutableComponent message = prefix.copy()
                .append(Component.literal("Performance Statistics").withStyle(ChatFormatting.GOLD))

                .append(Component.literal("\nStatus: ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(enabled ? "Enabled" : "Disabled")
                        .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.RED))

                .append(Component.literal("\nAsync Spawn: ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(asyncSpawn ? "Enabled" : "Disabled")
                        .withStyle(asyncSpawn ? ChatFormatting.GREEN : ChatFormatting.RED))

                .append(Component.literal("\nAsync Random Ticks: ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(asyncRandomTicks ? "Enabled" : "Disabled")
                        .withStyle(asyncRandomTicks ? ChatFormatting.GREEN : ChatFormatting.RED))

                .append(Component.literal("\nMSPT: ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(DECIMAL_FORMAT.format(mspt) + "ms").withStyle(getMsptColor(mspt)))

                .append(Component.literal("\nEntities: ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(String.valueOf(totalEntities)).withStyle(ChatFormatting.GREEN))
                .append(Component.literal(" (").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(DECIMAL_FORMAT.format(asyncRatio) + "%").withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" async)").withStyle(ChatFormatting.GRAY))

                .append(Component.literal("\nThreads: ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(String.valueOf(threads)).withStyle(ChatFormatting.YELLOW));

        source.sendSuccess(() -> message, false);
    }

    private static void showEntityStats(CommandSourceStack source, int topCount) {
        MinecraftServer server = source.getServer();
        server.execute(() -> {
            Map<EntityType<?>, Integer> entityTypeCounts = new HashMap<>();
            Map<EntityType<?>, Boolean> entityTypeAsync = new HashMap<>();
            AtomicInteger totalEntities = new AtomicInteger(0);
            AtomicInteger totalAsyncEntities = new AtomicInteger(0);

            MutableComponent message = prefix.copy()
                    .append(Component.literal("Entity Statistics").withStyle(ChatFormatting.GOLD));

            server.getAllLevels().forEach(world -> {
                String worldName = world.dimensionTypeRegistration().getRegisteredName();
                AtomicInteger worldCount = new AtomicInteger(0);
                AtomicInteger asyncCount = new AtomicInteger(0);

                world.getAllEntities().forEach(entity -> {
                    if (entity.isAlive()) {
                        EntityType<?> entityType = entity.getType();
                        worldCount.incrementAndGet();
                        totalEntities.incrementAndGet();
                        entityTypeCounts.merge(entityType, 1, Integer::sum);

                        boolean isAsync = !ParallelProcessor.shouldTickSynchronously(entity);
                        entityTypeAsync.put(entityType, isAsync);

                        if (isAsync) {
                            asyncCount.incrementAndGet();
                            totalAsyncEntities.incrementAndGet();
                        }
                    }
                });

                message.append(Component.literal("\n" + worldName + ": ").withStyle(ChatFormatting.YELLOW))
                        .append(Component.literal(String.valueOf(worldCount.get())).withStyle(ChatFormatting.GREEN))
                        .append(Component.literal(" entities (").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal(String.valueOf(asyncCount.get())).withStyle(ChatFormatting.AQUA))
                        .append(Component.literal(" async)").withStyle(ChatFormatting.GRAY));
            });

            message.append(Component.literal("\nTotal Entities: ").withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(String.valueOf(totalEntities.get())).withStyle(ChatFormatting.GOLD))
                    .append(Component.literal(" (").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(String.valueOf(totalAsyncEntities.get())).withStyle(ChatFormatting.AQUA))
                    .append(Component.literal(" async)").withStyle(ChatFormatting.GRAY));

            if (topCount > 0 && !entityTypeCounts.isEmpty()) {
                message.append(Component.literal("\n\nTop " + topCount + " Entity Types:").withStyle(ChatFormatting.GOLD));

                final int[] rank = {1};
                entityTypeCounts.entrySet().stream()
                        .sorted(Map.Entry.<EntityType<?>, Integer>comparingByValue().reversed())
                        .limit(topCount)
                        .forEach(entry -> {
                            EntityType<?> type = entry.getKey();
                            int count = entry.getValue();
                            boolean isAsync = entityTypeAsync.getOrDefault(type, false);

                            Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
                            String name = id.getPath();

                            message.append(Component.literal("\n" + rank[0] + ". ").withStyle(ChatFormatting.GRAY))
                                    .append(Component.literal(name).withStyle(ChatFormatting.YELLOW))
                                    .append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
                                    .append(Component.literal(String.valueOf(count)).withStyle(ChatFormatting.GREEN))
                                    .append(Component.literal(" [").withStyle(ChatFormatting.DARK_GRAY))
                                    .append(Component.literal(isAsync ? "async" : "sync")
                                            .withStyle(isAsync ? ChatFormatting.AQUA : ChatFormatting.RED))
                                    .append(Component.literal("]").withStyle(ChatFormatting.DARK_GRAY));

                            rank[0]++;
                        });
            }

            source.sendSuccess(() -> message, false);
        });
    }

    private static ChatFormatting getMsptColor(double mspt) {
        if (mspt <= 50) return ChatFormatting.GREEN;
        if (mspt <= 100) return ChatFormatting.YELLOW;
        return ChatFormatting.RED;
    }

    public static void runStatsThread() {
    }

    public static void shutdown() {
    }
}