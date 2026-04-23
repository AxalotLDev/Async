package com.axalotl.async.common.parallelised.utils;

import com.axalotl.async.common.ParallelProcessor;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LocalMobCapCalculator;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.PotentialCalculator;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.chunk.ChunkAccess;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class ParallelSpawnHelper {

    private static final int SPAWN_GRAIN = 256;

    private ParallelSpawnHelper() {}

    public static final class BatchResult {
        public final Object2IntOpenHashMap<MobCategory> counts = new Object2IntOpenHashMap<>();
        public final List<ChargeEntry> charges = new ArrayList<>();
        public final List<MobCapEntry> mobCaps = new ArrayList<>();

        void absorb(BatchResult other) {
            for (var e : other.counts.object2IntEntrySet()) {
                this.counts.addTo(e.getKey(), e.getIntValue());
            }
            this.charges.addAll(other.charges);
            this.mobCaps.addAll(other.mobCaps);
        }
    }

    public record ChargeEntry(BlockPos pos, double charge) {}
    public record MobCapEntry(ChunkPos chunkPos, MobCategory category) {}

    public static NaturalSpawner.SpawnState buildState(int spawnableChunkCount, Iterable<Entity> entities, NaturalSpawner.ChunkGetter chunkGetter, LocalMobCapCalculator localMobCap) {
        Entity[] arr = toArray(entities);
        BatchResult merged = collect(arr, chunkGetter);

        PotentialCalculator potential = new PotentialCalculator();
        for (int i = 0, n = merged.charges.size(); i < n; i++) {
            ChargeEntry c = merged.charges.get(i);
            potential.addCharge(c.pos(), c.charge());
        }
        for (int i = 0, n = merged.mobCaps.size(); i < n; i++) {
            MobCapEntry m = merged.mobCaps.get(i);
            localMobCap.addMob(m.chunkPos(), m.category());
        }
        return new NaturalSpawner.SpawnState(spawnableChunkCount, merged.counts, potential, localMobCap);
    }

    private static Entity[] toArray(Iterable<Entity> entities) {
        if (entities instanceof java.util.Collection<Entity> c) {
            return c.toArray(new Entity[0]);
        }
        List<Entity> list = new ArrayList<>();
        entities.forEach(list::add);
        return list.toArray(new Entity[0]);
    }

    private static BatchResult collect(Entity[] entities, NaturalSpawner.ChunkGetter chunkGetter) {
        int n = entities.length;
        if (n == 0) return new BatchResult();
        if (n <= SPAWN_GRAIN || ParallelProcessor.executor == null) {
            BatchResult r = new BatchResult();
            computeRange(entities, chunkGetter, 0, n, r);
            return r;
        }
        int pool = Math.max(1, ParallelProcessor.getPoolSize());
        int batchSize = Math.max(SPAWN_GRAIN, (n + pool - 1) / pool);
        int batchCount = (n + batchSize - 1) / batchSize;
        BatchResult[] parts = new BatchResult[batchCount];
        @SuppressWarnings("unchecked")
        CompletableFuture<Void>[] futures = new CompletableFuture[batchCount];
        for (int b = 0, i = 0; i < n; i += batchSize, b++) {
            int from = i;
            int to = Math.min(i + batchSize, n);
            parts[b] = new BatchResult();
            BatchResult out = parts[b];
            futures[b] = CompletableFuture.runAsync(() -> computeRange(entities, chunkGetter, from, to, out), ParallelProcessor.executor);
        }
        CompletableFuture.allOf(futures).join();
        BatchResult merged = new BatchResult();
        for (BatchResult p : parts) merged.absorb(p);
        return merged;
    }

    private static void computeRange(Entity[] entities, NaturalSpawner.ChunkGetter chunkGetter, int from, int to, BatchResult out) {
        for (int i = from; i < to; i++) {
            Entity e = entities[i];
            if (e instanceof Mob mob && (mob.isPersistenceRequired() || mob.requiresCustomPersistence())) continue;
            MobCategory cat = e.getType().getCategory();
            if (cat == MobCategory.MISC) continue;
            BlockPos pos = e.blockPosition();
            chunkGetter.query(ChunkPos.pack(pos), chunk -> {
                MobSpawnSettings.MobSpawnCost cost = getRoughBiome(pos, chunk).getMobSettings().getMobSpawnCost(e.getType());
                if (cost != null) out.charges.add(new ChargeEntry(pos, cost.charge()));
                if (e instanceof Mob) out.mobCaps.add(new MobCapEntry(chunk.getPos(), cat));
                out.counts.addTo(cat, 1);
            });
        }
    }

    private static Biome getRoughBiome(BlockPos pos, ChunkAccess chunk) {
        return chunk.getNoiseBiome(
                QuartPos.fromBlock(pos.getX()),
                QuartPos.fromBlock(pos.getY()),
                QuartPos.fromBlock(pos.getZ())).value();
    }
}
