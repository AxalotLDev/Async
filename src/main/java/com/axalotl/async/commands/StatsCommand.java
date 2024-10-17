package com.axalotl.async.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.axalotl.async.ParallelProcessor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

import static net.minecraft.server.command.CommandManager.literal;

public class StatsCommand {
    public static LiteralArgumentBuilder<ServerCommandSource> registerStatus(LiteralArgumentBuilder<ServerCommandSource> root) {
        return root.then(literal("stats").then(literal("reset").executes(cmdCtx -> {
            resetAll();
            return 1;
        })).executes(cmdCtx -> {
            if (!threadStats) {
                MutableText message = Text.literal("Stat calcs are disabled so stats are out of date");
                cmdCtx.getSource().sendFeedback(() -> message, true);
            }
            String messageString = "Current max threads: " + mean(maxThreads, liveValues);
            MutableText message = Text.literal(messageString);
            cmdCtx.getSource().sendFeedback(() -> message, true);
            return 1;
        }).then(literal("toggle").requires(cmdSrc -> cmdSrc.hasPermissionLevel(2)).executes(cmdCtx -> {
            threadStats = !threadStats;
            MutableText message = Text.literal("Stat calcs are " + (!threadStats ? "disabled" : "enabled") + "!");
            cmdCtx.getSource().sendFeedback(() -> message, true);
            return 1;
        })));
    }

    public static float mean(int[] data, int max) {
        float total = 0;
        for (int i = 0; i < max; i++) {
            total += data[i];
        }
        total /= max;
        return total;
    }

    static MinecraftServer mcs;

    public static void setServer(MinecraftServer nmcs) {
        mcs = nmcs;
    }

    static boolean resetThreadStats = false;
    static boolean threadStats = false;
    static final int samples = 100;
    static final int stepsPer = 35;
    static int[] maxThreads = new int[samples];
    static int[] maxEntities = new int[samples];
    static int currentSteps = 0;
    static int currentPos = 0;
    static int liveValues = 0;

    static Thread statsThread;

    public static void resetAll() {
        resetThreadStats = true;
    }

    public static void runDataThread() {
        statsThread = new Thread(() -> {
            try {
                while (true) {
                    Thread.sleep(10);

                    if (threadStats) {
                        if (resetThreadStats) {
                            maxThreads = new int[samples];
                            maxEntities = new int[samples];
                            currentSteps = 0;
                            currentPos = 0;
                            liveValues = 0;
                            resetThreadStats = false;
                        }

                        if (++currentSteps % stepsPer == 0) {
                            currentPos = (currentPos + 1) % samples;
                            liveValues = Math.min(liveValues + 1, samples);
                            maxEntities[currentPos] = 0;
                            maxThreads[currentPos] = 0;
                        }

                        int entities = ParallelProcessor.currentEnts.get();
                        maxEntities[currentPos] = Math.max(maxEntities[currentPos], entities);
                        maxThreads[currentPos] = Math.max(maxThreads[currentPos], entities);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        statsThread.setDaemon(true);
        statsThread.setName("Async Stats Thread");
        statsThread.start();
    }

}
