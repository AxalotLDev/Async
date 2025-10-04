package com.axalotl.async.common.platform;

public interface PlatformEvents {

    PlatformEvents INSTANCE = Services.load(PlatformEvents.class);

    static PlatformEvents getInstance() {
        return INSTANCE;
    }

    void saveConfig();

    boolean isModLoaded(String id);

    boolean platformUsesRefmap();
}