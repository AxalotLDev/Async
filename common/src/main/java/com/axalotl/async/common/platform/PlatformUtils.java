package com.axalotl.async.common.platform;

import net.minecraft.commands.CommandSourceStack;

import java.util.ServiceLoader;

public class PlatformUtils {
    private static final ModPlatform MOD_PLATFORM = PlatformUtils.load(ModPlatform.class);
    private static MinecraftPlatform minecraftPlatform;

    public static void initialize() {
        PlatformUtils.minecraftPlatform = PlatformUtils.load(MinecraftPlatform.class);
    }

    public static boolean platformUsesRefmap() {
        return MOD_PLATFORM.platformUsesRefmap();
    }

    public static void saveConfig() {
        MOD_PLATFORM.saveConfig();
    }

    public static void reloadConfig() {
        MOD_PLATFORM.reloadConfig();
    }

    public static boolean isModLoaded(String modId) {
        return MOD_PLATFORM.isModLoaded(modId);
    }

    public static boolean hasPermission(CommandSourceStack source, String node, int level) {
        return minecraftPlatform.hasPermission(source, node, level);
    }


    private static <T> T load(Class<T> clazz) {
        return ServiceLoader.load(clazz, clazz.getClassLoader()).findFirst().orElseThrow(() -> new NullPointerException("Failed to load service for " + clazz.getName()));
    }
}
