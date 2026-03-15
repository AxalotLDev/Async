package com.axalotl.async.common.mixin.entity.spawn;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LocalMobCapCalculator;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.PotentialCalculator;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.atomic.AtomicIntegerArray;

@Mixin(NaturalSpawner.SpawnState.class)
public class SpawnStateMixin {

    @Shadow @Final private int spawnableChunkCount;
    @Shadow @Final private PotentialCalculator spawnPotential;
    @Shadow @Final private LocalMobCapCalculator localMobCapCalculator;
    @Shadow @Final private Object2IntOpenHashMap<MobCategory> mobCategoryCounts;

    @Unique
    private final AtomicIntegerArray async$atomicMobCounts = new AtomicIntegerArray(MobCategory.values().length);

    @Inject(method = "<init>", at = @At("TAIL"))
    private void async$initAtomicCounts(int spawnableChunkCount, Object2IntOpenHashMap<MobCategory> mobCategoryCounts, PotentialCalculator spawnPotential, LocalMobCapCalculator localMobCapCalculator, CallbackInfo ci) {
        for (MobCategory cat : MobCategory.values()) {
            async$atomicMobCounts.set(cat.ordinal(), this.mobCategoryCounts.getInt(cat));
        }
    }

    @WrapMethod(method = "afterSpawn")
    private void async$afterSpawn(Mob mob, ChunkAccess chunk, Operation<Void> original) {
        EntityType<?> type = mob.getType();
        BlockPos pos = mob.blockPosition();


        Biome biome = chunk.getNoiseBiome(QuartPos.fromBlock(pos.getX()), QuartPos.fromBlock(pos.getY()), QuartPos.fromBlock(pos.getZ())).value();
        MobSpawnSettings.MobSpawnCost cost = biome.getMobSettings().getMobSpawnCost(type);
        double charge = cost != null ? cost.charge() : 0.0;

        this.spawnPotential.addCharge(pos, charge);
        MobCategory category = type.getCategory();
        async$atomicMobCounts.incrementAndGet(category.ordinal());
        this.localMobCapCalculator.addMob(new ChunkPos(pos), category);
    }

    @WrapMethod(method = "canSpawnForCategoryGlobal")
    private boolean async$canSpawnForCategoryGlobal(MobCategory mobCategory, Operation<Boolean> original) {
        int magicNumber = (2 * NaturalSpawner.SPAWN_DISTANCE_CHUNK + 1) * (2 * NaturalSpawner.SPAWN_DISTANCE_CHUNK + 1);
        int maxMobCount = mobCategory.getMaxInstancesPerChunk() * this.spawnableChunkCount / magicNumber;
        return async$atomicMobCounts.get(mobCategory.ordinal()) < maxMobCount;
    }

    @WrapMethod(method = "getMobCategoryCounts")
    private Object2IntMap<MobCategory> async$getMobCategoryCounts(Operation<Object2IntMap<MobCategory>> original) {
        Object2IntOpenHashMap<MobCategory> result = new Object2IntOpenHashMap<>();
        for (MobCategory cat : MobCategory.values()) {
            int count = async$atomicMobCounts.get(cat.ordinal());
            if (count > 0) {
                result.put(cat, count);
            }
        }
        return Object2IntMaps.unmodifiable(result);
    }
}