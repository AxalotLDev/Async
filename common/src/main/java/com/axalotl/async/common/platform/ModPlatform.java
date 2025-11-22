package com.axalotl.async.common.platform;

public interface ModPlatform {
    void saveConfig();

    boolean isModLoaded(String id);

    boolean platformUsesRefmap();
}