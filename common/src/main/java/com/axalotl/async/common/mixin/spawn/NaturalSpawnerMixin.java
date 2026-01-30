package com.axalotl.async.common.mixin.spawn;

import com.axalotl.async.common.config.AsyncConfig;
import com.axalotl.async.common.parallelised.utils.EntitySpawnData;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LocalMobCapCalculator;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.PotentialCalculator;
import net.minecraft.world.level.biome.MobSpawnSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

@Mixin(NaturalSpawner.class)
public abstract class NaturalSpawnerMixin {

    /**
     * @author Axalotl
     * @reason Lock-free parallel mob counting for spawn state creation
     */
    @Overwrite
    public static NaturalSpawner.SpawnState createState(
            int spawnableChunkCount,
            Iterable<Entity> entities,
            NaturalSpawner.ChunkGetter chunkGetter,
            LocalMobCapCalculator localMobCapCalculator
    ) {
        if (AsyncConfig.disabled || !AsyncConfig.enableAsyncSpawn) {
            return async$createStateVanilla(spawnableChunkCount, entities, chunkGetter, localMobCapCalculator);
        }

        return async$createStateParallel(spawnableChunkCount, entities, chunkGetter, localMobCapCalculator);
    }

    @Unique
    private static NaturalSpawner.SpawnState async$createStateVanilla(
            int spawnableChunkCount,
            Iterable<Entity> entities,
            NaturalSpawner.ChunkGetter chunkGetter,
            LocalMobCapCalculator localMobCapCalculator
    ) {
        PotentialCalculator potentialCalculator = new PotentialCalculator();
        Object2IntOpenHashMap<MobCategory> mobCounts = new Object2IntOpenHashMap<>();

        for (Entity entity : entities) {
            if (entity instanceof Mob mob && (mob.isPersistenceRequired() || mob.requiresCustomPersistence())) {
                continue;
            }

            MobCategory category = entity.getType().getCategory();
            if (category != MobCategory.MISC) {
                BlockPos blockPos = entity.blockPosition();
                chunkGetter.query(ChunkPos.asLong(blockPos), chunk -> {
                    MobSpawnSettings.MobSpawnCost cost = NaturalSpawner.getRoughBiome(blockPos, chunk)
                            .getMobSettings()
                            .getMobSpawnCost(entity.getType());
                    if (cost != null) {
                        potentialCalculator.addCharge(blockPos, cost.charge());
                    }

                    if (entity instanceof Mob) {
                        localMobCapCalculator.addMob(chunk.getPos(), category);
                    }

                    mobCounts.addTo(category, 1);
                });
            }
        }

        return new NaturalSpawner.SpawnState(spawnableChunkCount, mobCounts, potentialCalculator, localMobCapCalculator);
    }

    @Unique
    private static NaturalSpawner.SpawnState async$createStateParallel(
            int spawnableChunkCount,
            Iterable<Entity> entities,
            NaturalSpawner.ChunkGetter chunkGetter,
            LocalMobCapCalculator localMobCapCalculator
    ) {
        // Быстрее чем forEach
        List<Entity> entityList;
        if (entities instanceof List<Entity> list) {
            entityList = list;
        } else {
            entityList = new ArrayList<>();
            entities.forEach(entityList::add);
        }

        if (entityList.isEmpty()) {
            return new NaturalSpawner.SpawnState(
                    spawnableChunkCount,
                    new Object2IntOpenHashMap<>(),
                    new PotentialCalculator(),
                    localMobCapCalculator
            );
        }

        ConcurrentLinkedQueue<EntitySpawnData> results = new ConcurrentLinkedQueue<>();

        entityList.parallelStream().forEach(entity -> {
            if (entity instanceof Mob mob && (mob.isPersistenceRequired() || mob.requiresCustomPersistence())) {
                return;
            }

            MobCategory category = entity.getType().getCategory();
            if (category != MobCategory.MISC) {
                BlockPos blockPos = entity.blockPosition();

                chunkGetter.query(ChunkPos.asLong(blockPos), chunk -> {
                    MobSpawnSettings.MobSpawnCost cost = NaturalSpawner.getRoughBiome(blockPos, chunk)
                            .getMobSettings()
                            .getMobSpawnCost(entity.getType());

                    results.add(new EntitySpawnData(
                            blockPos,
                            category,
                            cost != null ? cost.charge() : 0.0,
                            entity instanceof Mob,
                            chunk.getPos()
                    ));
                });
            }
        });

        PotentialCalculator potentialCalculator = new PotentialCalculator();
        Object2IntOpenHashMap<MobCategory> mobCounts = new Object2IntOpenHashMap<>();

        for (EntitySpawnData data : results) {
            potentialCalculator.addCharge(data.pos(), data.charge());
            if (data.isMob()) {
                localMobCapCalculator.addMob(data.chunkPos(), data.category());
            }
            mobCounts.addTo(data.category(), 1);
        }

        return new NaturalSpawner.SpawnState(spawnableChunkCount, mobCounts, potentialCalculator, localMobCapCalculator);
    }
}