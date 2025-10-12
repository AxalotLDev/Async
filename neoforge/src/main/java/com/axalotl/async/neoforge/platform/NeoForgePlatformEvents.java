package com.axalotl.async.neoforge.platform;

import com.axalotl.async.common.platform.PlatformEvents;
import com.axalotl.async.neoforge.config.AsyncConfig;
import net.neoforged.fml.loading.LoadingModList;

public class NeoForgePlatformEvents implements PlatformEvents {

    @Override
    public void saveConfig() {
        AsyncConfig.saveConfig();
    }

    @Override
    public boolean isModLoaded(String id) {
        return LoadingModList.get().getModFileById(id) != null;
    }

    @Override
    public boolean platformUsesRefmap() {
        return false;
    }
}