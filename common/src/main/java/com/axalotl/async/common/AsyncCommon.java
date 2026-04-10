package com.axalotl.async.common;

import com.axalotl.async.common.platform.PlatformUtils;

public abstract class AsyncCommon {
    public static final String MODID = "async";

    public final void initialize() {
        PlatformUtils.initialize();
    }
}