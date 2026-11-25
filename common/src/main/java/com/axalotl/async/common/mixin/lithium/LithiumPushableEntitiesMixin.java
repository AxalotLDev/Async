package com.axalotl.async.common.mixin.lithium;

import com.bawnorton.mixinsquared.TargetHandler;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.caffeinemc.mods.lithium.common.entity.pushable.EntityPushablePredicate;
import net.caffeinemc.mods.lithium.common.entity.pushable.FeetBlockCachingEntity;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.ArrayList;

@Mixin(value = EntitySection.class, priority = 1500)
public class LithiumPushableEntitiesMixin {
    @Unique
    private static final String LITHIUM_MIXIN = "net.caffeinemc.mods.lithium.mixin.entity.collisions.unpushable_cramming.EntitySectionMixin";

    @WrapMethod(method = "@MixinSquared:Handler")
    @TargetHandler(mixin = LITHIUM_MIXIN, name = "lithium$collectPushableEntities")
    private AbortableIterationConsumer.Continuation collectPushableEntities(Level level, Entity entity, AABB box, EntityPushablePredicate<? super Entity> predicate, ArrayList<Entity> result, Operation<AbortableIterationConsumer.Continuation> original) {
        synchronized (this) {
            return original.call(level, entity, box, predicate, result);
        }
    }

    @WrapMethod(method = "@MixinSquared:Handler")
    @TargetHandler(mixin = LITHIUM_MIXIN, name = "lithium$onEntityModifiedCachedBlock")
    private void onEntityModifiedCachedBlock(FeetBlockCachingEntity entity, BlockState state, Operation<Void> original) {
        synchronized (this) {
            original.call(entity, state);
        }
    }
}
