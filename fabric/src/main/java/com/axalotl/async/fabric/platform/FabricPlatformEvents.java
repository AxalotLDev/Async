package com.axalotl.async.fabric.platform;

import com.axalotl.async.common.platform.PlatformEvents;
import com.axalotl.async.fabric.config.AsyncConfig;
import net.fabricmc.loader.api.FabricLoader;

public class FabricPlatformEvents implements PlatformEvents {

    @Override
    public void saveConfig() {
        AsyncConfig.saveConfig();
    }

    @Override
    public boolean isModLoaded(String id) {
        return FabricLoader.getInstance().isModLoaded(id);
    }

    @Override
    public boolean platformUsesRefmap() {
        return true;
    }
}