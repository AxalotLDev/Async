package com.axalotl.async.neoforge.mixin.client;

import com.axalotl.async.common.config.AsyncConfig;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LevelRenderer.class)
public class LevelRendererMixin {
    @Shadow
    private Frustum cullingFrustum;

    @WrapOperation(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;render(Lnet/minecraft/world/entity/Entity;DDDFFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"))
    private void render(EntityRenderDispatcher instance, Entity entity, double x, double y, double z, float yaw, float partialTicks, com.mojang.blaze3d.vertex.PoseStack matrixStack, MultiBufferSource buffer, int light, Operation<Void> original) {
        if (AsyncConfig.enableEntityCulling) {
            if (!entity.isCurrentlyGlowing() && this.cullingFrustum != null && !this.cullingFrustum.isVisible(entity.getBoundingBox())) {
                return;
            }
        }
        original.call(instance, entity, x, y, z, yaw, partialTicks, matrixStack, buffer, light);
    }
}
