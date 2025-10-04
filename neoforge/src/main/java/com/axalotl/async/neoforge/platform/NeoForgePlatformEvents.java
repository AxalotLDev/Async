package com.axalotl.async.neoforge.platform;

import com.axalotl.async.common.platform.PlatformEvents;
import com.axalotl.async.neoforge.config.AsyncConfig;
import net.neoforged.fml.loading.FMLLoader;

public class NeoForgePlatformEvents implements PlatformEvents {

    @Override
    public void saveConfig() {
        AsyncConfig.saveConfig();
    }

    @Override
    public boolean isModLoaded(String id) {
        return FMLLoader.getCurrent().getLoadingModList().getModFileById(id) != null;
    }

    @Override
    public boolean platformUsesRefmap() {
        return false;
    }
}