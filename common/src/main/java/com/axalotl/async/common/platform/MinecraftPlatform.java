package com.axalotl.async.common.platform;

import net.minecraft.commands.CommandSourceStack;

public interface MinecraftPlatform {
    boolean hasPermission(CommandSourceStack source, String node, int level);
}
