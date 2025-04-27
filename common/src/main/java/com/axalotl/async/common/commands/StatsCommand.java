package com.axalotl.async.common.commands;

import com.axalotl.async.common.ParallelProcessor;
import com.axalotl.async.common.config.AsyncConfig;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;

import java.text.DecimalFormat;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import static com.axalotl.async.common.commands.AsyncCommand.prefix;
import static net.minecraft.commands.Commands.literal;

public class StatsCommand {
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
                            showEntityStats(cmdCtx.getSource());
                            return 1;
                        })));
    }

    private static void showGeneralStats(CommandSourceStack source) {
        int availableProcessors = AsyncConfig.getParallelism();
        double avgThreads = calculateAverageThreads();
        double threadUtilization = (avgThreads / availableProcessors) * 100.0;

        MutableComponent message = prefix.copy()
                .append(Component.literal("Performance Statistics ").withStyle(style -> style.withColor(ChatFormatting.GOLD)))
                .append(Component.literal("\nActive Processing Threads: ").withStyle(style -> style.withColor(ChatFormatting.WHITE)))
                .append(Component.literal(DECIMAL_FORMAT.format(avgThreads)).withStyle(style -> style.withColor(ChatFormatting.GREEN)))
                .append(Component.literal(" / " + availableProcessors).withStyle(style -> style.withColor(ChatFormatting.GRAY)))
                .append(Component.literal("\nThread Utilization: ").withStyle(style -> style.withColor(ChatFormatting.WHITE)))
                .append(Component.literal(DECIMAL_FORMAT.format(threadUtilization) + "%").withStyle(style -> style.withColor(ChatFormatting.GREEN)))
                .append(Component.literal("\nAsync Status: ").withStyle(style -> style.withColor(ChatFormatting.WHITE)))
                .append(Component.literal(AsyncConfig.disabled.getValue() ? "Disabled" : "Enabled").withStyle(style ->
                        style.withColor(AsyncConfig.disabled.getValue() ? ChatFormatting.RED : ChatFormatting.GREEN)));

        source.sendSuccess(() -> message, true);
    }

    private static void showEntityStats(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        MutableComponent message = prefix.copy()
                .append(Component.literal("Entity Statistics ").withStyle(style -> style.withColor(ChatFormatting.GOLD)));

        AtomicInteger totalEntities = new AtomicInteger(0);
        AtomicInteger totalAsyncEntities = new AtomicInteger(0);

        server.getAllLevels().forEach(world -> {
            String worldName = world.dimension().location().toString();
            AtomicInteger worldCount = new AtomicInteger(0);
            AtomicInteger asyncCount = new AtomicInteger(0);

            world.entityTickList.forEach(entity -> {
                if (entity.isAlive()) {
                    worldCount.incrementAndGet();
                    totalEntities.incrementAndGet();
                    if (!ParallelProcessor.shouldTickSynchronously(entity)) {
                        asyncCount.incrementAndGet();
                        totalAsyncEntities.incrementAndGet();
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

        source.sendSuccess(() -> message, true);
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
                    e.printStackTrace();
                }
            }
        }, "Async-Stats-Thread");

        statsThread.setDaemon(true);
        statsThread.start();
    }

    private static void updateStats() {
        if (AsyncConfig.disabled.getValue()) {
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
