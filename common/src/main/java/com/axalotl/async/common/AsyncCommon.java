package com.axalotl.async.common;

import com.axalotl.async.common.platform.PlatformEvents;
import net.minecraft.network.chat.Component;

public class AsyncCommon {
    public static final String MODID = "async";
    public static boolean LITHIUM = PlatformEvents.getInstance().isModLoaded("lithium");
    public final static Component prefix = Component.literal("§8[§f\uD83C\uDF00§8]§7 ");
}