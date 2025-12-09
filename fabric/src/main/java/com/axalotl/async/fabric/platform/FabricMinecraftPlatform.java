package com.axalotl.async.fabric.platform;

import com.axalotl.async.common.AsyncCommon;
import com.axalotl.async.common.platform.MinecraftPlatform;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.permissions.PermissionLevel;

public class FabricMinecraftPlatform implements MinecraftPlatform {

    @Override
    public boolean hasPermission(CommandSourceStack source, String node, int level) {
        String permission = String.format("%s.%s", AsyncCommon.MODID, node);
        return Permissions.check(source, permission, PermissionLevel.byId(level));
    }
}