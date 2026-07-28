package com.axalotl.async.common.mixin.entity.spawn;

import com.axalotl.async.common.config.AsyncConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.NaturalSpawner;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(NaturalSpawner.class)
public abstract class NaturalSpawnerMixin {

    @Unique
    private static final ThreadLocal<RandomSource> ASYNC_SPAWN_RANDOM = ThreadLocal.withInitial(RandomSource::createThreadLocalInstance);

    @Redirect(method = {"spawnCategoryForPosition(Lnet/minecraft/world/entity/MobCategory;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/NaturalSpawner$SpawnPredicate;Lnet/minecraft/world/level/NaturalSpawner$AfterSpawnCallback;)V", "isValidSpawnPostitionForType"}, at = @At(value = "FIELD", target = "Lnet/minecraft/server/level/ServerLevel;random:Lnet/minecraft/util/RandomSource;", opcode = Opcodes.GETFIELD))
    private static RandomSource asyncServerLevelRandom(ServerLevel level) {
        return spawnRandom(level);
    }

    @Redirect(method = "getRandomPosWithin", at = @At(value = "FIELD", target = "Lnet/minecraft/world/level/Level;random:Lnet/minecraft/util/RandomSource;", opcode = Opcodes.GETFIELD))
    private static RandomSource asyncLevelRandom(Level level) {
        return spawnRandom(level);
    }

    @Unique
    private static RandomSource spawnRandom(Level level) {
        if (AsyncConfig.disabled || !AsyncConfig.enableAsyncSpawn) {
            return level.getRandom();
        }
        return ASYNC_SPAWN_RANDOM.get();
    }
}