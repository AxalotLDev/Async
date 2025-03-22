package com.axalotl.async.mixin.entity;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityTickList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.function.Consumer;

@Mixin(EntityTickList.class)
public abstract class EntityListMixin {

    @Shadow
    private Int2ObjectMap<Entity> active;
    @Shadow
    private Int2ObjectMap<Entity> passive;
    @Shadow
    private Int2ObjectMap<Entity> iterated;

    @Unique
    private boolean async$ensuringActiveIsNotIterated;
    /**
     * @author Alchemy
     * @reason experimental
     */
    @Overwrite
    private void ensureActiveIsNotIterated() {
        if (async$ensuringActiveIsNotIterated) {
            return;
        }
        async$ensuringActiveIsNotIterated = true;
        if (this.iterated == this.active) {
            this.passive.clear();

            for (Int2ObjectMap.Entry<Entity> entry : Int2ObjectMaps.fastIterable(this.active)) {
                this.passive.put(entry.getIntKey(), entry.getValue());
            }

            Int2ObjectMap<Entity> temp = this.active;
            this.active = this.passive;
            this.passive = temp;
        }
        async$ensuringActiveIsNotIterated = false;
    }

    /**
     * @author Alchemy
     * @reason experimental
     */
    @Overwrite
    public void forEach(Consumer<Entity> pEntity) {
        if (this.iterated != null) {
            throw new UnsupportedOperationException("Only one concurrent iteration supported");
        } else {
            this.iterated = this.active;

            try {
                if (this.active.values() == null) {
                    return;
                }
                ObjectIterator var2 = this.active.values().iterator();

                while(var2.hasNext()) {
                    Entity $$1 = (Entity)var2.next();
                    if ($$1 == null){
                        return;
                    }
                    pEntity.accept($$1);
                }
            } finally {
                this.iterated = null;
            }

        }
    }
}