package com.axalotl.async.mixin.entity;

import com.axalotl.async.parallelised.ConcurrentCollections;
import net.minecraft.world.entity.ai.gossip.GossipContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Map;
import java.util.UUID;

@Mixin(GossipContainer.class)
public class VillagerGossipsMixin {
    @Shadow
    private final Map<UUID, GossipContainer.EntityGossips> gossips = ConcurrentCollections.newHashMap();
}
