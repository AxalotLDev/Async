package com.axalotl.async.common.platform;

import java.util.Set;

public interface ModPlatform {
    void saveConfig();

    void reloadConfig();

    boolean isModLoaded(String id);

    Set<String> getModIds();
}