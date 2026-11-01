package com.axalotl.async.common.commands;

import com.axalotl.async.common.ParallelProcessor;
import com.axalotl.async.common.config.AsyncConfig;
import com.axalotl.async.common.platform.PlatformPermission;
import com.axalotl.async.common.utils.SynchronizationReason;
import com.axalotl.async.common.utils.SynchronizationStats;
import com.axalotl.async.common.utils.TickStats;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EntityType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.atomic.AtomicInteger;

import static com.axalotl.async.common.ParallelProcessor.getPoolSize;
import static com.axalotl.async.common.commands.AsyncCommand.prefix;
import static com.axalotl.async.common.utils.TickStats.resetEntityTickStats;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class StatsCommand {

    public static LiteralArgumentBuilder<CommandSourceStack> registerStatus(LiteralArgumentBuilder<CommandSourceStack> root) {
        return root.then(literal("stats").requires(PlatformPermission.require("command.statistics", 0))
                .executes(cmdCtx -> {
                    showGeneralStats(cmdCtx.getSource());
                    return 1;
                })
                .then(literal("entity")
                        .executes(cmdCtx -> {
                            showEntityStats(cmdCtx.getSource(), 0, false, 0);
                            return 1;
                        })
                        .then(argument("count", IntegerArgumentType.integer(1, 100))
                                .executes(cmdCtx -> {
                                    int count = IntegerArgumentType.getInteger(cmdCtx, "count");
                                    showEntityStats(cmdCtx.getSource(), count, false, 0);
                                    return 1;
                                })
                                .then(argument("ticks", IntegerArgumentType.integer())
                                        .executes(cmdCtx -> {
                                            int count = IntegerArgumentType.getInteger(cmdCtx, "count");
                                            int ticks = IntegerArgumentType.getInteger(cmdCtx, "ticks");
                                            startRecordingAndShow(cmdCtx.getSource(), count, ticks);
                                            return 1;
                                        }))))
                .then(literal("synced")
                        .executes(cmdCtx -> {
                            showSynchronizedEntityStats(cmdCtx.getSource());
                            return 1;
                        })));
    }

    private static void startRecordingAndShow(CommandSourceStack source, int topCount, int ticks) {
        source.sendSuccess(() -> prefix.copy().append(Component.literal("Recording entity ticks for " + ticks + " ticks...").withStyle(ChatFormatting.YELLOW)), false);
        TickStats.startRecording(ticks, () -> showEntityStats(source, topCount, true, ticks));
    }

    private static void showGeneralStats(CommandSourceStack source) {
        MinecraftServer server = source.getServer();

        int totalEntities = 0;
        for (var world : server.getAllLevels()) {
            for (var entity : world.getAllEntities()) {
                if (entity.isAlive()) {
                    totalEntities++;
                }
            }
        }

        int threads = getPoolSize();

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

                .append(Component.literal("\nEntities: ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(String.valueOf(totalEntities)).withStyle(ChatFormatting.GREEN))

                .append(Component.literal("\nMax Threads: ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(String.valueOf(threads)).withStyle(ChatFormatting.YELLOW));

        source.sendSuccess(() -> message, false);
    }

    private static void showSynchronizedEntityStats(CommandSourceStack source) {
        source.sendSuccess(
                () -> prefix.copy().append(Component.literal(
                        "Recording synchronized entities for the next server tick..."
                ).withStyle(ChatFormatting.YELLOW)),
                false
        );
        SynchronizationStats.captureNextTick(
                snapshot -> sendSynchronizedEntityStats(source, snapshot)
        );
    }

    private static void sendSynchronizedEntityStats(
            CommandSourceStack source,
            SynchronizationStats.Snapshot snapshot
    ) {
        int uniqueEntityTypeCount = snapshot.synchronizedEntityTypes().size();

        MutableComponent message = prefix.copy()
                .append(Component.literal("Synchronized Entity Statistics")
                        .withStyle(ChatFormatting.GOLD))
                .append(Component.literal("\nSynchronized Entities: ")
                        .withStyle(ChatFormatting.WHITE))
                .append(Component.literal(String.valueOf(snapshot.synchronizedEntityCount()))
                        .withStyle(ChatFormatting.GREEN))
                .append(Component.literal("\nUnique Entity Types: ")
                        .withStyle(ChatFormatting.WHITE))
                .append(Component.literal(String.valueOf(uniqueEntityTypeCount))
                        .withStyle(ChatFormatting.YELLOW));

        if (uniqueEntityTypeCount == 0) {
            message.append(Component.literal("\nNo entities were synchronized during the tick.")
                    .withStyle(ChatFormatting.RED));
            source.sendSuccess(() -> message, false);
            return;
        }

        List<Map.Entry<ResourceLocation, SynchronizationStats.EntityTypeStats>> entityTypes =
                new ArrayList<>(snapshot.entityTypes().entrySet());
        entityTypes.sort(Comparator.comparing(entry -> entry.getKey().toString()));

        for (Map.Entry<ResourceLocation, SynchronizationStats.EntityTypeStats> entry
                : entityTypes) {
            SynchronizationStats.EntityTypeStats entityTypeStats = entry.getValue();
            StringJoiner reasonDescriptions = new StringJoiner(", ");
            for (SynchronizationReason reason : SynchronizationReason.values()) {
                if (entityTypeStats.reasons().contains(reason)) {
                    reasonDescriptions.add(reason.description());
                }
            }

            message.append(Component.literal("\n- ").withStyle(ChatFormatting.GREEN))
                    .append(Component.literal(entry.getKey().toString())
                            .withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal(" x" + entityTypeStats.entityCount())
                            .withStyle(ChatFormatting.GREEN))
                    .append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(reasonDescriptions.toString())
                            .withStyle(ChatFormatting.AQUA));
        }

        source.sendSuccess(() -> message, false);
    }

    private static void showEntityStats(CommandSourceStack source, int topCount, boolean showTickStats, int ticks) {
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

                            ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
                            String name = id.getPath();

                            message.append(Component.literal("\n" + rank[0] + ". ").withStyle(ChatFormatting.GRAY))
                                    .append(Component.literal(name).withStyle(ChatFormatting.YELLOW))
                                    .append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
                                    .append(Component.literal(String.valueOf(count)).withStyle(ChatFormatting.GREEN))
                                    .append(Component.literal(" [").withStyle(ChatFormatting.DARK_GRAY))
                                    .append(Component.literal(isAsync ? "async" : "sync")
                                            .withStyle(isAsync ? ChatFormatting.AQUA : ChatFormatting.RED))
                                    .append(Component.literal("]").withStyle(ChatFormatting.DARK_GRAY));

                            if (showTickStats && ticks > 0) {
                                double mspt = TickStats.getMSPTForType(type, ticks);
                                message.append(Component.literal(" "))
                                        .append(Component.literal(String.format("%.3fms avg", mspt)).withStyle(ChatFormatting.GREEN));
                            }

                            rank[0]++;
                        });
            }
            resetEntityTickStats();
            source.sendSuccess(() -> message, false);
        });
    }
}
