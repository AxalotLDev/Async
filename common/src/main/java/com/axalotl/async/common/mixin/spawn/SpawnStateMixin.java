package com.axalotl.async.common.mixin.spawn;

import com.axalotl.async.common.config.AsyncConfig;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LocalMobCapCalculator;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.PotentialCalculator;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = NaturalSpawner.SpawnState.class, priority = 1500)
public abstract class SpawnStateMixin {

    @Shadow @Final private PotentialCalculator spawnPotential;
    @Shadow @Final private Object2IntOpenHashMap<MobCategory> mobCategoryCounts;
    @Shadow @Final private LocalMobCapCalculator localMobCapCalculator;
    @Shadow private BlockPos lastCheckedPos;
    @Shadow private EntityType<?> lastCheckedType;
    @Shadow private double lastCharge;

    @Unique
    private final Object async$lock = new Object();

    @Inject(method = "afterSpawn", at = @At("HEAD"), cancellable = true)
    private void async$afterSpawn(Mob mob, ChunkAccess chunk, CallbackInfo ci) {
        if (AsyncConfig.disabled || !AsyncConfig.enableAsyncSpawn) {
            return;
        }

        ci.cancel();

        EntityType<?> entityType = mob.getType();
        BlockPos blockPos = mob.blockPosition();

        double charge;
        if (blockPos.equals(this.lastCheckedPos) && entityType == this.lastCheckedType) {
            charge = this.lastCharge;
        } else {
            MobSpawnSettings.MobSpawnCost cost = NaturalSpawner.getRoughBiome(blockPos, chunk)
                    .getMobSettings()
                    .getMobSpawnCost(entityType);
            charge = cost != null ? cost.charge() : 0.0;
        }

        MobCategory category = entityType.getCategory();

        synchronized (async$lock) {
            this.spawnPotential.addCharge(blockPos, charge);
            this.mobCategoryCounts.addTo(category, 1);
            this.localMobCapCalculator.addMob(new ChunkPos(blockPos), category);
        }
    }
}