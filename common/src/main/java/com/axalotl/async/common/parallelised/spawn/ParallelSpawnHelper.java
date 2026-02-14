package com.axalotl.async.common.parallelised.spawn;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.ArrayList;
import java.util.concurrent.RecursiveTask;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.biome.MobSpawnSettings;

/**
 * ForkJoinPool-native helpers for parallel createState() mob cap counting.
 *
 * Uses a tree-merge pattern: each leaf task accumulates results locally
 * (no shared mutable state, no CAS), then parent tasks merge child results
 * up the fork-join tree. The final merged result is returned to the caller.
 */
public final class ParallelSpawnHelper {

    private ParallelSpawnHelper() {}

    private static final int SPAWN_GRAIN = 256;

    public static final class SpawnResult {

        public final Object2IntOpenHashMap<MobCategory> mobCounts =
            new Object2IntOpenHashMap<>();
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

    public static final class SpawnDataCollector
        extends RecursiveTask<SpawnResult>
    {

        private final Entity[] entities;
        private final NaturalSpawner.ChunkGetter chunkGetter;
        private final int from;
        private final int to;

        public SpawnDataCollector(
            Entity[] entities,
            NaturalSpawner.ChunkGetter chunkGetter,
            int from,
            int to
        ) {
            this.entities = entities;
            this.chunkGetter = chunkGetter;
            this.from = from;
            this.to = to;
        }

        @Override
        protected SpawnResult compute() {
            int size = to - from;
            if (size <= SPAWN_GRAIN) {
                return computeLeaf();
            }

            int mid = (from + to) >>> 1;
            SpawnDataCollector left = new SpawnDataCollector(
                entities,
                chunkGetter,
                from,
                mid
            );
            SpawnDataCollector right = new SpawnDataCollector(
                entities,
                chunkGetter,
                mid,
                to
            );
            left.fork();
            SpawnResult rightResult = right.compute();
            SpawnResult leftResult = left.join();
            leftResult.merge(rightResult);
            return leftResult;
        }

        private SpawnResult computeLeaf() {
            SpawnResult result = new SpawnResult();
            for (int i = from; i < to; i++) {
                processEntity(entities[i], result);
            }
            return result;
        }

        private void processEntity(Entity entity, SpawnResult result) {
            if (
                entity instanceof Mob mob &&
                (mob.isPersistenceRequired() || mob.requiresCustomPersistence())
            ) {
                return;
            }

            MobCategory category = entity.getType().getCategory();
            if (category == MobCategory.MISC) {
                return;
            }

            BlockPos blockPos = entity.blockPosition();
            chunkGetter.query(ChunkPos.asLong(blockPos), chunk -> {
                MobSpawnSettings.MobSpawnCost cost =
                    NaturalSpawner.getRoughBiome(blockPos, chunk)
                        .getMobSettings()
                        .getMobSpawnCost(entity.getType());

                if (cost != null) {
                    result.charges.add(
                        new ChargeEntry(blockPos, cost.charge())
                    );
                }

                result.mobCounts.addTo(category, 1);

                if (entity instanceof Mob) {
                    result.mobCapEntries.add(
                        new MobCapEntry(chunk.getPos(), category)
                    );
                }
            });
        }
    }
}
