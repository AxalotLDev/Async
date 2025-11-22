package com.axalotl.async.fabric.platform;

import com.axalotl.async.common.platform.ModPlatform;
import com.axalotl.async.fabric.config.AsyncConfig;
import net.fabricmc.loader.api.FabricLoader;

public class FabricModPlatform implements ModPlatform {

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