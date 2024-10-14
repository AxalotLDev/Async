package com.axalotl.async.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import me.shedaniel.autoconfig.AutoConfig;
import com.axalotl.async.Async;
import com.axalotl.async.ParallelProcessor;
import com.axalotl.async.config.BlockEntityLists;
import com.axalotl.async.config.GeneralConfig;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.BlockEntityTickInvoker;

import java.util.Objects;

import static net.minecraft.server.command.CommandManager.literal;

public class ConfigCommand {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        LiteralArgumentBuilder<ServerCommandSource> config = literal("async");
        config = config.then(registerConfig(literal("config")));
        config = StatsCommand.registerStatus(config);
        dispatcher.register(config);
    }

    public static ArgumentBuilder<ServerCommandSource, ?> registerConfig(LiteralArgumentBuilder<ServerCommandSource> root) {
        GeneralConfig config = Async.config;
        return root.then(literal("toggle").requires(cmdSrc -> cmdSrc.hasPermissionLevel(2)).executes(cmdCtx -> {
                            config.disabled = !config.disabled;
                            MutableText message = Text.literal(
                                    "Async is now " + (config.disabled ? "disabled" : "enabled"));
                            cmdCtx.getSource().sendFeedback(() -> message, true);
                            return 1;
                        })).then(literal("entity").executes(cmdCtx -> {
                            config.disableEntity = !config.disableEntity;
                            MutableText message = Text.literal(
                                    "Async's entity threading is now " + (config.disableEntity ? "disabled" : "enabled"));
                            cmdCtx.getSource().sendFeedback(() -> message, true);
                            return 1;
                        }))
                        .then(literal("opTracing").executes(cmdCtx -> {
                            config.disableEntity = !config.disableEntity;
                            MutableText message = Text.literal(
                                    "Async's entity and task count stats is now " + (config.disableEntity ? "disabled" : "enabled"));
                            cmdCtx.getSource().sendFeedback(() -> message, true);
                            return 1;
                        })).then(literal("tnt").executes(cmdCtx -> {
                            config.disableTNT = !config.disableTNT;
                            MutableText message = Text.literal(
                                    "Async's tnt entity threading is now " + (config.disableEntity ? "disabled" : "enabled"));
                            cmdCtx.getSource().sendFeedback(() -> message, true);
                            return 1;
                        }).then(literal("ops").executes(cmdCtx -> {
                            config.opsTracing = !config.opsTracing;
                            MutableText message = Text.literal(
                                    "Async's ops tracing is now " + (!config.opsTracing ? "disabled" : "enabled"));
                            cmdCtx.getSource().sendFeedback(() -> message, true);
                            return 1;
                        }))
                )
                .then(literal("state").executes(cmdCtx -> {
                    MutableText message = Text.literal("Async is currently " + (config.disabled ? "disabled" : "enabled"));
                    cmdCtx.getSource().sendFeedback(() -> message, true);
                    return 1;
                }))
                .then(literal("save").requires(cmdSrc -> cmdSrc.hasPermissionLevel(2)).executes(cmdCtx -> {
                    MutableText message = Text.literal("Saving Async config to disk...");
                    cmdCtx.getSource().sendFeedback(() -> message, true);
                    AutoConfig.getConfigHolder(GeneralConfig.class).save();
                    cmdCtx.getSource().sendFeedback(() -> Text.literal("Done!"), true);
                    return 1;
                }))
                .then(literal("temanage").requires(cmdSrc -> cmdSrc.hasPermissionLevel(2)).then(literal("list")
                                .executes(cmdCtx -> {
                                    MutableText message = Text.literal("NYI");
                                    cmdCtx.getSource().sendFeedback(() -> message, true);
                                    return 1;
                                })).then(literal("target")
                                .requires(cmdSrc -> {
                                    if (cmdSrc.getPlayer() != null) {
                                        return true;
                                    }
                                    MutableText message = Text.literal("Only runnable by player!");
                                    cmdSrc.sendError(message);
                                    return false;
                                }).then(literal("whitelist").executes(cmdCtx -> {
                                    MutableText message;
                                    HitResult htr = Objects.requireNonNull(cmdCtx.getSource().getPlayer()).raycast(20, 0.0F, false);
                                    if (htr.getType() == HitResult.Type.BLOCK) {
                                        BlockPos bp = ((BlockHitResult) htr).getBlockPos();
                                        BlockEntity te = cmdCtx.getSource().getWorld().getBlockEntity(bp);
                                        if (te != null && isTickableBe(te)) {
                                            if (config.teWhiteListString.contains(te.getClass().getName())) {
                                                message = Text.literal("Class " + te.getClass().getName() + " already exists in TE Whitelist");
                                                cmdCtx.getSource().sendFeedback(() -> message, true);
                                                return 0;
                                            }
                                            BlockEntityLists.teWhiteList.add(te.getClass());
                                            config.teWhiteListString.add(te.getClass().getName());
                                            BlockEntityLists.teBlackList.remove(te.getClass());
                                            config.teBlackListString.remove(te.getClass().getName());
                                            message = Text.literal("Added " + te.getClass().getName() + " to TE Whitelist");
                                            cmdCtx.getSource().sendFeedback(() -> message, true);
                                            return 1;
                                        }
                                        message = Text.literal("That block doesn't contain a tickable TE!");
                                        cmdCtx.getSource().sendError(message);
                                        return 0;
                                    }
                                    message = Text.literal("Only runable by player!");
                                    cmdCtx.getSource().sendError(message);
                                    return 0;
                                })).then(literal("blacklist").executes(cmdCtx -> {
                                    MutableText message;
                                    HitResult htr = Objects.requireNonNull(cmdCtx.getSource().getPlayer()).raycast(20, 0.0F, false);
                                    if (htr.getType() == HitResult.Type.BLOCK) {
                                        BlockPos bp = ((BlockHitResult) htr).getBlockPos();
                                        BlockEntity te = cmdCtx.getSource().getWorld().getBlockEntity(bp);
                                        if (te != null && isTickableBe(te)) {
                                            if (config.teBlackListString.contains(te.getClass().getName())) {
                                                message = Text.literal("Class " + te.getClass().getName() + " already exists in TE Blacklist");
                                                cmdCtx.getSource().sendFeedback(() -> message, true);
                                                return 0;
                                            }
                                            BlockEntityLists.teBlackList.add(te.getClass());
                                            config.teBlackListString.add(te.getClass().getName());
                                            BlockEntityLists.teWhiteList.remove(te.getClass());
                                            config.teWhiteListString.remove(te.getClass().getName());
                                            message = Text.literal("Added " + te.getClass().getName() + " to TE Blacklist");
                                            cmdCtx.getSource().sendFeedback(() -> message, true);
                                            return 1;
                                        }
                                        message = Text.literal("That block doesn't contain a tickable TE!");
                                        cmdCtx.getSource().sendError(message);
                                        return 0;
                                    }
                                    message = Text.literal("Only runnable by player!");
                                    cmdCtx.getSource().sendError(message);
                                    return 0;
                                })).then(literal("remove").executes(cmdCtx -> {
                                    MutableText message;
                                    HitResult htr = Objects.requireNonNull(cmdCtx.getSource().getPlayer()).raycast(20, 0.0F, false);
                                    if (htr.getType() == HitResult.Type.BLOCK) {
                                        BlockPos bp = ((BlockHitResult) htr).getBlockPos();
                                        BlockEntity te = cmdCtx.getSource().getWorld().getBlockEntity(bp);
                                        if (te != null && isTickableBe(te)) {
                                            BlockEntityLists.teBlackList.remove(te.getClass());
                                            config.teBlackListString.remove(te.getClass().getName());
                                            BlockEntityLists.teWhiteList.remove(te.getClass());
                                            config.teWhiteListString.remove(te.getClass().getName());
                                            message = Text.literal("Removed " + te.getClass().getName() + " from TE classlists");
                                            cmdCtx.getSource().sendFeedback(() -> message, true);
                                            return 1;
                                        }
                                        message = Text.literal("That block doesn't contain a tickable TE!");
                                        cmdCtx.getSource().sendError(message);
                                        return 0;
                                    }
                                    message = Text.literal("Only runable by player!");
                                    cmdCtx.getSource().sendError(message);
                                    return 0;
                                })).then(literal("willtick").executes(cmdCtx -> {
                                    MutableText message;
                                    HitResult htr = Objects.requireNonNull(cmdCtx.getSource().getPlayer()).raycast(20, 0.0F, false);
                                    if (htr.getType() == HitResult.Type.BLOCK) {
                                        BlockPos bp = ((BlockHitResult) htr).getBlockPos();
                                        BlockEntity te = cmdCtx.getSource().getWorld().getBlockEntity(bp);
                                        if (te != null && isTickableBe(te)) {
                                            boolean willSerial = ParallelProcessor.filterTE((BlockEntityTickInvoker) te);
                                            message = Text.literal("That TE " + (!willSerial ? "will" : "will not") + " tick fully parallelised");
                                            cmdCtx.getSource().sendFeedback(() -> message, true);
                                            return 1;
                                        }
                                        message = Text.literal("That block doesn't contain a tickable TE!");
                                        cmdCtx.getSource().sendError(message);
                                        return 0;
                                    }
                                    message = Text.literal("Only runable by player!");
                                    cmdCtx.getSource().sendError(message);
                                    return 0;
                                }))
                        )
                );
    }

    public static boolean isTickableBe(BlockEntity be) {
        BlockEntityTicker<?> blockEntityTicker = be.getCachedState().getBlockEntityTicker(be.getWorld(), be.getType());
        return blockEntityTicker != null;
    }
}
