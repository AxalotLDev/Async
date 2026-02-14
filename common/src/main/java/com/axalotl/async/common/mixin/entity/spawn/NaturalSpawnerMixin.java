package com.axalotl.async.common.mixin.entity.spawn;

import com.axalotl.async.common.ParallelProcessor;
import com.axalotl.async.common.config.AsyncConfig;
import com.axalotl.async.common.parallelised.spawn.ParallelSpawnHelper.ChargeEntry;
import com.axalotl.async.common.parallelised.spawn.ParallelSpawnHelper.MobCapEntry;
import com.axalotl.async.common.parallelised.spawn.ParallelSpawnHelper.SpawnDataCollector;
import com.axalotl.async.common.parallelised.spawn.ParallelSpawnHelper.SpawnResult;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.ArrayList;
import java.util.List;
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
        cir.setReturnValue(
            async$createStateParallel(
                spawnableChunkCount,
                entities,
                chunkGetter,
                localMobCapCalculator
            )
        );
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

        Entity[] entityArray = entityList.toArray(new Entity[0]);

        // Tree-merge parallel collection: each leaf accumulates locally,
        // results merge up the fork-join tree. No shared mutable state.
        SpawnResult result = ParallelProcessor.tickPool.invoke(
            new SpawnDataCollector(
                entityArray,
                chunkGetter,
                0,
                entityArray.length
            )
        );

        // Apply results sequentially on the main thread.
        // PotentialCalculator and LocalMobCapCalculator are not thread-safe.
        PotentialCalculator potentialCalculator = new PotentialCalculator();
        for (int i = 0, n = result.charges.size(); i < n; i++) {
            ChargeEntry c = result.charges.get(i);
            potentialCalculator.addCharge(c.pos(), c.charge());
        }

        for (int i = 0, n = result.mobCapEntries.size(); i < n; i++) {
            MobCapEntry m = result.mobCapEntries.get(i);
            localMobCapCalculator.addMob(m.chunkPos(), m.category());
        }

        return new NaturalSpawner.SpawnState(
            spawnableChunkCount,
            result.mobCounts,
            potentialCalculator,
            localMobCapCalculator
        );
    }
}
