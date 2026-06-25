package com.axalotl.async.common.platform;

public interface ModPlatform {
    void saveConfig();

    void reloadConfig();

    boolean isModLoaded(String id);
}