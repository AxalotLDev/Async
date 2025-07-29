package com.axalotl.async.common;

import static com.axalotl.async.common.platform.PlatformEventBus.isModLoaded;

public class AsyncCommon {
    public static boolean LITHIUM = isModLoaded("lithium");
    public static String platform = "neoforge";
}
