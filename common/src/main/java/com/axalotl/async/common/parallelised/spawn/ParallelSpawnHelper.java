package com.axalotl.async.common.parallelised.spawn;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.biome.MobSpawnSettings;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.RecursiveAction;

/**
 * ForkJoinPool-native helpers for parallel createState() mob cap counting.
 */
public final class ParallelSpawnHelper {

    private ParallelSpawnHelper() {}

    private static final int SPAWN_GRAIN = 256;

    public record SpawnEntry(BlockPos pos, MobCategory category, double charge, boolean isMob, ChunkPos chunkPos) {}

    /**
     * RecursiveAction that splits entity array for work-stealing.
     * Each leaf processes entities, calls chunkGetter.query() (O(1) via fullChunks map),
     * and adds results to lock-free ConcurrentLinkedQueue.
     */
    public static final class SpawnDataCollector extends RecursiveAction {
        private final Entity[] entities;
        private final NaturalSpawner.ChunkGetter chunkGetter;
        private final ConcurrentLinkedQueue<SpawnEntry> results;
        private final int from;
        private final int to;

        public SpawnDataCollector(Entity[] entities, NaturalSpawner.ChunkGetter chunkGetter,
                                  ConcurrentLinkedQueue<SpawnEntry> results, int from, int to) {
            this.entities = entities;
            this.chunkGetter = chunkGetter;
            this.results = results;
            this.from = from;
            this.to = to;
        }

        @Override
        protected void compute() {
            int size = to - from;
            if (size <= SPAWN_GRAIN) {
                for (int i = from; i < to; i++) {
                    processEntity(entities[i]);
                }
            } else {
                int mid = (from + to) >>> 1;
                invokeAll(
                        new SpawnDataCollector(entities, chunkGetter, results, from, mid),
                        new SpawnDataCollector(entities, chunkGetter, results, mid, to)
                );
            }
        }

        private void processEntity(Entity entity) {
            if (entity instanceof Mob mob && (mob.isPersistenceRequired() || mob.requiresCustomPersistence())) {
                return;
            }

            MobCategory category = entity.getType().getCategory();
            if (category == MobCategory.MISC) {
                return;
            }

            BlockPos blockPos = entity.blockPosition();
            chunkGetter.query(ChunkPos.asLong(blockPos), chunk -> {
                MobSpawnSettings.MobSpawnCost cost = NaturalSpawner.getRoughBiome(blockPos, chunk)
                        .getMobSettings()
                        .getMobSpawnCost(entity.getType());

                results.add(new SpawnEntry(
                        blockPos,
                        category,
                        cost != null ? cost.charge() : 0.0,
                        entity instanceof Mob,
                        chunk.getPos()
                ));
            });
        }
    }
}