package com.axalotl.async.common.platform;

import net.minecraft.commands.CommandSourceStack;

import java.util.function.Predicate;

public class PlatformPermission {

    public static boolean check(CommandSourceStack source, String node, int level) {
        return PlatformUtils.hasPermission(source, node, level);
    }

    public static Predicate<CommandSourceStack> require(String node, int level) {
        return source -> check(source, node, level);
    }
}