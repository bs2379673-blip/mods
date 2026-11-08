package com.leon.saintsdragons.fabric.mixin.fabric;

import com.leon.saintsdragons.client.renderer.DragonRiderAttachments;
import com.leon.saintsdragons.client.renderer.EntityPreviewRenderContext;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Port 1.21.11: el jugador que va en un dragon sigue el asiento del modelo. */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V",
            at = @At("HEAD"))
    private void saintsdragons$transformRiderOnDragon(LivingEntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
                                                       CameraRenderState camera, CallbackInfo ci) {
        if (!(state instanceof AvatarRenderState avatar) || EntityPreviewRenderContext.isRendering() || Minecraft.getInstance().level == null) {
            return;
        }
        Entity player = Minecraft.getInstance().level.getEntity(avatar.id);
        if (player != null) {
            float partial = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);
            DragonRiderAttachments.transformRider(player, partial, poseStack, ((LivingEntityRenderer) (Object) this).getRenderOffset(state));
        }
    }
}
