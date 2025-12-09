package com.axalotl.async.fabric.platform;

import com.axalotl.async.common.platform.MinecraftPlatform;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;

public class FabricMinecraftPlatform implements MinecraftPlatform {

    @Override
    public boolean hasPermission(CommandSourceStack source, String node, int level) {
        return source.permissions().hasPermission(new Permission.HasCommandLevel(PermissionLevel.byId(level)));
    }
}