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
    private static Entity[] entityBuf = new Entity[1024];

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
        int n = fillBuffer(entities);
        Entity[] arr = entityBuf;
        try {
            BatchResult merged = collect(arr, n, chunkGetter);

            PotentialCalculator potential = new PotentialCalculator();
            for (int i = 0, m = merged.charges.size(); i < m; i++) {
                ChargeEntry c = merged.charges.get(i);
                potential.addCharge(c.pos(), c.charge());
            }
            for (int i = 0, m = merged.mobCaps.size(); i < m; i++) {
                MobCapEntry m2 = merged.mobCaps.get(i);
                localMobCap.addMob(m2.chunkPos(), m2.category());
            }
            return new NaturalSpawner.SpawnState(spawnableChunkCount, merged.counts, potential, localMobCap);
        } finally {
            for (int i = 0; i < n; i++) arr[i] = null;
        }
    }

    private static int fillBuffer(Iterable<Entity> entities) {
        if (entities instanceof java.util.Collection<Entity> c) {
            int size = c.size();
            if (entityBuf.length < size) {
                entityBuf = new Entity[Math.max(size, entityBuf.length * 2)];
            }
            Entity[] result = c.toArray(entityBuf);
            if (result != entityBuf) entityBuf = result;
            return size;
        }
        Entity[] buf = entityBuf;
        int idx = 0;
        for (Entity e : entities) {
            if (idx >= buf.length) {
                Entity[] grown = new Entity[buf.length * 2];
                System.arraycopy(buf, 0, grown, 0, idx);
                buf = grown;
                entityBuf = buf;
            }
            buf[idx++] = e;
        }
        return idx;
    }

    private static BatchResult collect(Entity[] entities, int n, NaturalSpawner.ChunkGetter chunkGetter) {
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
