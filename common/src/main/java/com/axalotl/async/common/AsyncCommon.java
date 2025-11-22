package com.axalotl.async.common;

import com.axalotl.async.common.platform.PlatformUtils;

public abstract class AsyncCommon {
    public static final String MODID = "async";
    public static boolean LITHIUM = PlatformUtils.isModLoaded("lithium");

    public final void initialize() {
        PlatformUtils.initialize();
    }
}