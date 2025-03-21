package com.axalotl.async.commands;

import com.axalotl.async.config.AsyncConfig;
import com.axalotl.async.ParallelProcessor;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.entity.EntityType;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import static com.axalotl.async.commands.AsyncCommand.prefix;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class StatsCommand {
    private static final Logger LOGGER = LogManager.getLogger(StatsCommand.class);
    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#,##0.##");
    private static final int MAX_SAMPLES = 100;
    private static final long SAMPLING_INTERVAL_MS = 10;

    private static final Queue<Integer> threadSamples = new ConcurrentLinkedQueue<>();
    private static volatile boolean isRunning = true;
    private static Thread statsThread;

    public static LiteralArgumentBuilder<ServerCommandSource> registerStatus(LiteralArgumentBuilder<ServerCommandSource> root) {
        return root.then(literal("stats").requires(cmdSrc -> cmdSrc.hasPermissionLevel(4))
                .executes(cmdCtx -> {
                    showGeneralStats(cmdCtx.getSource());
                    return 1;
                })
                .then(literal("entity")
                        .requires(cmdSrc -> cmdSrc.hasPermissionLevel(4))
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

    private static void showGeneralStats(ServerCommandSource source) {
        int availableProcessors = AsyncConfig.getParallelism();
        double avgThreads = calculateAverageThreads();
        double threadUtilization = (avgThreads / availableProcessors) * 100.0;

        MutableText message = prefix.copy()
                .append(Text.literal("Performance Statistics ").styled(style -> style.withColor(Formatting.GOLD)))
                .append(Text.literal("\nActive Processing Threads: ").styled(style -> style.withColor(Formatting.WHITE)))
                .append(Text.literal(DECIMAL_FORMAT.format(avgThreads)).styled(style -> style.withColor(Formatting.GREEN)))
                .append(Text.literal(" / " + availableProcessors).styled(style -> style.withColor(Formatting.GRAY)))
                .append(Text.literal("\nThread Utilization: ").styled(style -> style.withColor(Formatting.WHITE)))
                .append(Text.literal(DECIMAL_FORMAT.format(threadUtilization) + "%").styled(style -> style.withColor(Formatting.GREEN)))
                .append(Text.literal("\nAsync Status: ").styled(style -> style.withColor(Formatting.WHITE)))
                .append(Text.literal(AsyncConfig.disabled ? "Disabled" : "Enabled").styled(style ->
                        style.withColor(AsyncConfig.disabled ? Formatting.RED : Formatting.GREEN)));

        source.sendFeedback(() -> message, true);
    }

    private static void showEntityStats(ServerCommandSource source, int topCount) {
        MinecraftServer server = source.getServer();
        server.execute(() -> {
            Map<EntityType<?>, Integer> entityTypeCounts = new HashMap<>();
            Map<EntityType<?>, Integer> asyncEntityTypeCounts = new HashMap<>();
            AtomicInteger totalEntities = new AtomicInteger(0);
            AtomicInteger totalAsyncEntities = new AtomicInteger(0);

            MutableText message = prefix.copy()
                    .append(Text.literal("Entity Statistics ").styled(style -> style.withColor(Formatting.GOLD)));

            server.getWorlds().forEach(world -> {
                String worldName = world.getRegistryKey().getValue().toString();
                AtomicInteger worldCount = new AtomicInteger(0);
                AtomicInteger asyncCount = new AtomicInteger(0);

                world.entityList.forEach(entity -> {
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

                message.append(Text.literal("\n" + worldName + ": ").styled(style -> style.withColor(Formatting.YELLOW)))
                        .append(Text.literal(String.valueOf(worldCount.get())).styled(style -> style.withColor(Formatting.GREEN)))
                        .append(Text.literal(" entities (").styled(style -> style.withColor(Formatting.GRAY)))
                        .append(Text.literal(String.valueOf(asyncCount.get())).styled(style -> style.withColor(Formatting.AQUA)))
                        .append(Text.literal(" async)").styled(style -> style.withColor(Formatting.GRAY)));
            });

            message.append(Text.literal("\nTotal Entities: ").styled(style -> style.withColor(Formatting.WHITE)))
                    .append(Text.literal(String.valueOf(totalEntities.get())).styled(style -> style.withColor(Formatting.GOLD)))
                    .append(Text.literal(" (").styled(style -> style.withColor(Formatting.GRAY)))
                    .append(Text.literal(String.valueOf(totalAsyncEntities.get())).styled(style -> style.withColor(Formatting.AQUA)))
                    .append(Text.literal(" async)").styled(style -> style.withColor(Formatting.GRAY)));

            if (topCount > 0) {
                List<Map.Entry<EntityType<?>, Integer>> sortedEntities = new ArrayList<>(entityTypeCounts.entrySet());
                sortedEntities.sort(Map.Entry.<EntityType<?>, Integer>comparingByValue().reversed());

                if (topCount < sortedEntities.size()) {
                    sortedEntities = sortedEntities.subList(0, topCount);
                }

                if (!sortedEntities.isEmpty()) {
                    message.append(Text.literal("\n\nTop " + sortedEntities.size() + " Entity Types:").styled(style -> style.withColor(Formatting.GOLD)));

                    int rank = 1;
                    for (Map.Entry<EntityType<?>, Integer> entry : sortedEntities) {
                        EntityType<?> type = entry.getKey();
                        int count = entry.getValue();
                        int asyncCount = asyncEntityTypeCounts.getOrDefault(type, 0);

                        Identifier typeId = Registries.ENTITY_TYPE.getId(type);
                        String name = typeId.toString();

                        message.append(Text.literal("\n" + rank + ". ").styled(style -> style.withColor(Formatting.GRAY)))
                                .append(Text.literal(name).styled(style -> style.withColor(Formatting.YELLOW)))
                                .append(Text.literal(": ").styled(style -> style.withColor(Formatting.GRAY)))
                                .append(Text.literal(String.valueOf(count)).styled(style -> style.withColor(Formatting.GREEN)))
                                .append(Text.literal(" (").styled(style -> style.withColor(Formatting.GRAY)))
                                .append(Text.literal(String.valueOf(asyncCount)).styled(style -> style.withColor(Formatting.AQUA)))
                                .append(Text.literal(" async)").styled(style -> style.withColor(Formatting.GRAY)));

                        rank++;
                    }
                }
            }
            source.sendFeedback(() -> message, true);
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