package com.axalotl.async.common.mixin.entity.spawn;

import com.axalotl.async.common.ParallelProcessor;
import com.axalotl.async.common.config.AsyncConfig;
import com.axalotl.async.common.parallelised.spawn.ParallelSpawnHelper.SpawnDataCollector;
import com.axalotl.async.common.parallelised.spawn.ParallelSpawnHelper.SpawnEntry;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.LocalMobCapCalculator;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.PotentialCalculator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

@Mixin(value = NaturalSpawner.class, priority = 900)
public abstract class NaturalSpawnerMixin {

    @Inject(method = "createState", at = @At("HEAD"), cancellable = true)
    private static void async$createState(
            int spawnableChunkCount,
            Iterable<Entity> entities,
            NaturalSpawner.ChunkGetter chunkGetter,
            LocalMobCapCalculator localMobCapCalculator,
            CallbackInfoReturnable<NaturalSpawner.SpawnState> cir
    ) {
        if (AsyncConfig.disabled || !AsyncConfig.enableAsyncSpawn) {
            return;
        }
        if (ParallelProcessor.tickPool == null) {
            return;
        }
        cir.setReturnValue(async$createStateParallel(spawnableChunkCount, entities, chunkGetter, localMobCapCalculator));
    }

    @Unique
    private static NaturalSpawner.SpawnState async$createStateParallel(
            int spawnableChunkCount,
            Iterable<Entity> entities,
            NaturalSpawner.ChunkGetter chunkGetter,
            LocalMobCapCalculator localMobCapCalculator
    ) {
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

        ConcurrentLinkedQueue<SpawnEntry> results = new ConcurrentLinkedQueue<>();
        Entity[] entityArray = entityList.toArray(new Entity[0]);

        SpawnDataCollector task = new SpawnDataCollector(entityArray, chunkGetter, results, 0, entityArray.length);
        ParallelProcessor.tickPool.invoke(task);

        // Sequential merge
        PotentialCalculator potentialCalculator = new PotentialCalculator();
        Object2IntOpenHashMap<MobCategory> mobCounts = new Object2IntOpenHashMap<>();

        for (SpawnEntry data : results) {
            potentialCalculator.addCharge(data.pos(), data.charge());
            if (data.isMob()) {
                localMobCapCalculator.addMob(data.chunkPos(), data.category());
            }
            mobCounts.addTo(data.category(), 1);
        }

        return new NaturalSpawner.SpawnState(spawnableChunkCount, mobCounts, potentialCalculator, localMobCapCalculator);
    }
}