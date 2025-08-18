package com.axalotl.async.common.mixin.plugin;

import java.util.Set;
import java.util.HashSet;
import java.util.Arrays;

public class MixinLists {
    public static final Set<String> SERVER_MIXINS = new HashSet<>(Arrays.asList(
        "com.axalotl.async.common.mixin.server.ServerWatchdogMixin",
        "com.axalotl.async.common.mixin.vmp.VMPChunkMapMixin"
    ));

    public static final Set<String> CLIENT_MIXINS = new HashSet<>(Arrays.asList(
        "com.axalotl.async.fabric.mixin.client.LevelRendererMixin",
        "com.axalotl.async.neoforge.mixin.client.LevelRendererMixin"
    ));

    public static final Set<String> SODIUM_MIXINS = new HashSet<>(Arrays.asList(
        "com.axalotl.async.fabric.mixin.sodium.SodiumWorldRendererMixin",
        "com.axalotl.async.fabric.mixin.sodium.AsyncSodiumWorldRendererMixin",
        "com.axalotl.async.neoforge.mixin.sodium.SodiumWorldRendererMixin",
        "com.axalotl.async.neoforge.mixin.sodium.AsyncSodiumWorldRendererMixin"
    ));
}
