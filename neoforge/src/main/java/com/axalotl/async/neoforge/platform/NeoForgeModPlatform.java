package com.axalotl.async.neoforge.platform;

import com.axalotl.async.common.platform.ModPlatform;
import com.axalotl.async.neoforge.config.AsyncConfig;
import net.neoforged.fml.loading.FMLLoader;

public class NeoForgeModPlatform implements ModPlatform {

    @Override
    public void saveConfig() {
        AsyncConfig.saveConfig();
    }

    @Override
    public void reloadConfig() {
        AsyncConfig.loadConfig();
        com.axalotl.async.common.config.AsyncConfig.onConfigLoaded();
    }

    @Override
    public boolean isModLoaded(String id) {
        return FMLLoader.getCurrent().getLoadingModList().getModFileById(id) != null;
    }
}