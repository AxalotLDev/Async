package com.axalotl.async.common.platform;

public class PlatformEventBus {
    private static PlatformEvents impl;

    public static void register(PlatformEvents platformEvents) {
        impl = platformEvents;
    }

    public static void saveConfig() {
        if (impl != null) {
            impl.saveConfig();
        }
    }
}
