package com.axalotl.async.common.parallelised.utils;

import com.axalotl.async.common.ParallelProcessor;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.biome.MobSpawnSettings;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class ParallelSpawnHelper {

    private ParallelSpawnHelper() {}

    private static final int SPAWN_GRAIN = 256;

    public static final class SpawnResult {
        public final Object2IntOpenHashMap<MobCategory> mobCounts = new Object2IntOpenHashMap<>();
        public final ArrayList<ChargeEntry> charges = new ArrayList<>();
        public final ArrayList<MobCapEntry> mobCapEntries = new ArrayList<>();

        public void merge(SpawnResult other) {
            for (var entry : other.mobCounts.object2IntEntrySet()) {
                mobCounts.addTo(entry.getKey(), entry.getIntValue());
            }
            charges.addAll(other.charges);
            mobCapEntries.addAll(other.mobCapEntries);
        }
    }

    public record ChargeEntry(BlockPos pos, double charge) {}

    public record MobCapEntry(ChunkPos chunkPos, MobCategory category) {}

    public static SpawnResult collectSpawnData(Entity[] entities, NaturalSpawner.ChunkGetter chunkGetter) {
        int length = entities.length;

        if (length <= SPAWN_GRAIN || ParallelProcessor.tickPool == null) {
            return computeRange(entities, chunkGetter, 0, length);
        }

        int poolSize = ParallelProcessor.getPoolSize();
        int chunkSize = Math.max(SPAWN_GRAIN, length / poolSize);

        List<CompletableFuture<SpawnResult>> futures = new ArrayList<>();
        for (int i = 0; i < length; i += chunkSize) {
            int from = i;
            int to = Math.min(i + chunkSize, length);
            futures.add(CompletableFuture.supplyAsync(() -> computeRange(entities, chunkGetter, from, to), ParallelProcessor.tickPool));
        }

        SpawnResult merged = new SpawnResult();
        for (CompletableFuture<SpawnResult> future : futures) {
            merged.merge(future.join());
        }
        return merged;
    }

    private static SpawnResult computeRange(Entity[] entities, NaturalSpawner.ChunkGetter chunkGetter, int from, int to) {
        SpawnResult result = new SpawnResult();
        for (int i = from; i < to; i++) {
            processEntity(entities[i], chunkGetter, result);
        }
        return result;
    }

    private static void processEntity(Entity entity, NaturalSpawner.ChunkGetter chunkGetter, SpawnResult result) {
        if (entity instanceof Mob mob && (mob.isPersistenceRequired() || mob.requiresCustomPersistence())) {
            return;
        }

        MobCategory category = entity.getType().getCategory();
        if (category == MobCategory.MISC) {
            return;
        }

        BlockPos blockPos = entity.blockPosition();
        chunkGetter.query(ChunkPos.pack(blockPos), chunk -> {
            MobSpawnSettings.MobSpawnCost cost = NaturalSpawner.getRoughBiome(blockPos, chunk).getMobSettings().getMobSpawnCost(entity.getType());
            if (cost != null) {
                result.charges.add(new ChargeEntry(blockPos, cost.charge()));
            }

            result.mobCounts.addTo(category, 1);
            if (entity instanceof Mob) {
                result.mobCapEntries.add(new MobCapEntry(chunk.getPos(), category));
            }
        });
    }
}