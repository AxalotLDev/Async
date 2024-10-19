package com.axalotl.async.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerEntityManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.entity.EntityLike;
import net.minecraft.world.entity.EntityTrackingSection;
import net.minecraft.world.entity.EntityTrackingStatus;
import org.spongepowered.asm.mixin.*;

import static net.minecraft.world.entity.EntityChangeListener.NONE;

@Mixin(ServerEntityManager.Listener.class)
public abstract class ServerEntityManagerMixin<T extends EntityLike> implements AutoCloseable {
    @Shadow
    private EntityTrackingSection<T> section;

    @Shadow
    @Final
    private T entity;

    @Shadow
    private long sectionPos;

    @Final
    @Shadow
    ServerEntityManager<T> manager;

    @Shadow
    protected abstract void updateLoadStatus(EntityTrackingStatus oldStatus, EntityTrackingStatus newStatus);

    /**
     * @author AxalotL
     * @reason Remove warns and add null check
     */
    @Overwrite
    public void updateEntityPosition() {
        BlockPos blockPos = this.entity.getBlockPos();
        long l = ChunkSectionPos.toLong(blockPos);

        if (l != this.sectionPos) {
            EntityTrackingStatus oldStatus = this.section.getStatus();
            synchronized (this) {
                try {
                    this.section.remove(this.entity);
                    this.manager.entityLeftSection(this.sectionPos, this.section);

                    EntityTrackingSection<T> newSection = this.manager.cache.getTrackingSection(l);
                    if (newSection != null) {
                        newSection.add(this.entity);
                        this.section = newSection;
                        this.sectionPos = l;
                    }

                    this.updateLoadStatus(oldStatus, this.section.getStatus());
                } catch (NullPointerException ignored) {
                }
            }
        }
    }

    /**
     * @author AxalotL
     * @reason Remove warns and add null check
     */
    @Overwrite
    public void remove(Entity.RemovalReason reason) {
        EntityTrackingStatus entityTrackingStatus = ServerEntityManager.getNeededLoadStatus(this.entity, this.section.getStatus());

        synchronized (this) {
            try {
                this.section.remove(this.entity);
                if (entityTrackingStatus.shouldTick()) {
                    this.manager.stopTicking(this.entity);
                }
                if (entityTrackingStatus.shouldTrack()) {
                    this.manager.stopTracking(this.entity);
                }
                if (reason.shouldDestroy()) {
                    this.manager.handler.destroy(this.entity);
                }
                this.manager.entityUuids.remove(this.entity.getUuid());
                this.entity.setChangeListener(NONE);
                this.manager.entityLeftSection(this.sectionPos, this.section);
            } catch (NullPointerException ignored) {
            }
        }
    }
}
