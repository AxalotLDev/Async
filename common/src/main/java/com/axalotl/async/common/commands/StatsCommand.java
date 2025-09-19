package com.axalotl.async.common.commands;

import com.axalotl.async.common.ParallelProcessor;
import com.axalotl.async.common.config.AsyncConfig;
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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import static com.axalotl.async.common.commands.AsyncCommand.prefix;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class StatsCommand {
    private static final Logger LOGGER = LogManager.getLogger(StatsCommand.class);
    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#,##0.##");
    private static final int MAX_SAMPLES = 100;
    private static final long SAMPLING_INTERVAL_MS = 10;

    private static final Queue<Integer> threadSamples = new ConcurrentLinkedQueue<>();
    private static volatile boolean isRunning = true;
    private static Thread statsThread;

    public static LiteralArgumentBuilder<CommandSourceStack> registerStatus(LiteralArgumentBuilder<CommandSourceStack> root) {
        return root.then(literal("stats")
                .requires(cmdSrc -> cmdSrc.hasPermission(4))
                .executes(cmdCtx -> {
                    showGeneralStats(cmdCtx.getSource());
                    return 1;
                })
                .then(literal("entity")
                        .requires(cmdSrc -> cmdSrc.hasPermission(4))
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
        double avgThreads = calculateAverageThreads();

        MutableComponent message = prefix.copy()
                .append(Component.literal("Performance Statistics ").withStyle(style -> style.withColor(ChatFormatting.GOLD)))
                .append(Component.literal("\nAverage Active Processing Threads: ").withStyle(style -> style.withColor(ChatFormatting.WHITE)))
                .append(Component.literal(DECIMAL_FORMAT.format(Math.ceil(avgThreads))).withStyle(style -> style.withColor(ChatFormatting.GREEN)))
                .append(Component.literal("\nAsync Status: ").withStyle(style -> style.withColor(ChatFormatting.WHITE)))
                .append(Component.literal(AsyncConfig.disabled ? "disabled" : "Enabled").withStyle(style ->
                        style.withColor(AsyncConfig.disabled ? ChatFormatting.RED : ChatFormatting.GREEN)));

        source.sendSuccess(() -> message, true);
    }

    private static void showEntityStats(CommandSourceStack source, int topCount) {
        MinecraftServer server = source.getServer();
        server.execute(() -> {
            Map<EntityType<?>, Integer> entityTypeCounts = new HashMap<>();
            Map<EntityType<?>, Integer> asyncEntityTypeCounts = new HashMap<>();
            AtomicInteger totalEntities = new AtomicInteger(0);
            AtomicInteger totalAsyncEntities = new AtomicInteger(0);

            MutableComponent message = prefix.copy()
                    .append(Component.literal("Entity Statistics ").withStyle(style -> style.withColor(ChatFormatting.GOLD)));

            server.getAllLevels().forEach(world -> {
                String worldName = world.dimensionTypeRegistration().getRegisteredName();
                AtomicInteger worldCount = new AtomicInteger(0);
                AtomicInteger asyncCount = new AtomicInteger(0);

                world.getAllEntities().forEach(entity -> {
                    if (entity != null && entity.isAlive()) {
                        EntityType<?> entityType = entity.getType();

                        worldCount.incrementAndGet();
                        totalEntities.incrementAndGet();
                        entityTypeCounts.merge(entityType, 1, Integer::sum);

                        if (!ParallelProcessor.shouldTickSynchronously(entity)) {
                            asyncCount.incrementAndGet();
                            totalAsyncEntities.incrementAndGet();
                            asyncEntityTypeCounts.merge(entityType, 1, Integer::sum);
                        }
                    }
                });

                message.append(Component.literal("\n" + worldName + ": ").withStyle(style -> style.withColor(ChatFormatting.YELLOW)))
                        .append(Component.literal(String.valueOf(worldCount.get())).withStyle(style -> style.withColor(ChatFormatting.GREEN)))
                        .append(Component.literal(" entities (").withStyle(style -> style.withColor(ChatFormatting.GRAY)))
                        .append(Component.literal(String.valueOf(asyncCount.get())).withStyle(style -> style.withColor(ChatFormatting.AQUA)))
                        .append(Component.literal(" async)").withStyle(style -> style.withColor(ChatFormatting.GRAY)));
            });

            message.append(Component.literal("\nTotal Entities: ").withStyle(style -> style.withColor(ChatFormatting.WHITE)))
                    .append(Component.literal(String.valueOf(totalEntities.get())).withStyle(style -> style.withColor(ChatFormatting.GOLD)))
                    .append(Component.literal(" (").withStyle(style -> style.withColor(ChatFormatting.GRAY)))
                    .append(Component.literal(String.valueOf(totalAsyncEntities.get())).withStyle(style -> style.withColor(ChatFormatting.AQUA)))
                    .append(Component.literal(" async)").withStyle(style -> style.withColor(ChatFormatting.GRAY)));

            if (topCount > 0) {
                List<Map.Entry<EntityType<?>, Integer>> sortedEntities = new ArrayList<>(entityTypeCounts.entrySet());
                sortedEntities.sort(Map.Entry.<EntityType<?>, Integer>comparingByValue().reversed());

                if (topCount < sortedEntities.size()) {
                    sortedEntities = sortedEntities.subList(0, topCount);
                }

                if (!sortedEntities.isEmpty()) {
                    message.append(Component.literal("\n\nTop " + sortedEntities.size() + " Entity Types:").withStyle(style -> style.withColor(ChatFormatting.GOLD)));

                    int rank = 1;
                    for (Map.Entry<EntityType<?>, Integer> entry : sortedEntities) {
                        EntityType<?> type = entry.getKey();
                        int count = entry.getValue();
                        int asyncCount = asyncEntityTypeCounts.getOrDefault(type, 0);

                        ResourceLocation nameID = BuiltInRegistries.ENTITY_TYPE.getKey(type);
                        String name = nameID.toLanguageKey();

                        message.append(Component.literal("\n" + rank + ". ").withStyle(style -> style.withColor(ChatFormatting.GRAY)))
                                .append(Component.literal(name).withStyle(style -> style.withColor(ChatFormatting.YELLOW)))
                                .append(Component.literal(": ").withStyle(style -> style.withColor(ChatFormatting.GRAY)))
                                .append(Component.literal(String.valueOf(count)).withStyle(style -> style.withColor(ChatFormatting.GREEN)))
                                .append(Component.literal(" (").withStyle(style -> style.withColor(ChatFormatting.GRAY)))
                                .append(Component.literal(String.valueOf(asyncCount)).withStyle(style -> style.withColor(ChatFormatting.AQUA)))
                                .append(Component.literal(" async)").withStyle(style -> style.withColor(ChatFormatting.GRAY)));

                        rank++;
                    }
                }
            }
            source.sendSuccess(() -> message, true);
        });
    }

    private static double calculateAverageThreads() {
        if (threadSamples.isEmpty()) {
            return 0.0;
        }
        double sum = threadSamples.stream().mapToDouble(Integer::doubleValue).sum();
        return sum / threadSamples.size();
    }

    public static void runStatsThread() {
        if (statsThread != null && statsThread.isAlive()) {
            return;
        }

        statsThread = new Thread(() -> {
            while (isRunning && !Thread.currentThread().isInterrupted()) {
                try {
                    updateStats();
                    Thread.sleep(SAMPLING_INTERVAL_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    LOGGER.error("Error in stats thread", e);
                }
            }
        }, "Async-Stats-Thread");

        statsThread.setDaemon(true);
        statsThread.start();
    }

    private static void updateStats() {
        if (AsyncConfig.disabled) {
            resetStats();
            return;
        }

        int currentThreads = ParallelProcessor.currentEntities.get();

        threadSamples.offer(currentThreads);

        while (threadSamples.size() > MAX_SAMPLES) {
            threadSamples.poll();
        }
    }

    private static void resetStats() {
        threadSamples.clear();
    }

    public static void shutdown() {
        isRunning = false;
        if (statsThread != null) {
            statsThread.interrupt();
        }
    }
}
