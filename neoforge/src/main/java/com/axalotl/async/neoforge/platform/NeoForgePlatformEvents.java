package com.axalotl.async.neoforge.platform;

import com.axalotl.async.common.platform.PlatformEventBus;
import com.axalotl.async.common.platform.PlatformEvents;
import com.axalotl.async.neoforge.config.AsyncConfig;

public class NeoForgePlatformEvents implements PlatformEvents {

    public static void init() {
        PlatformEventBus.register(new NeoForgePlatformEvents());
    }

    @Override
    public void saveConfig() {
        AsyncConfig.saveConfig();
    }
}
