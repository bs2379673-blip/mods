package com.annis.npc.mixin;

import com.annis.npc.client.ClientLinks;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Entidades con "aspecto Annis" (aldeanos de MCA convertidos, etc.) se dibujan como NPC Annis. */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"), cancellable = true)
    private void annisnpc$renderAsNpc(LivingEntity entity, float yaw, float tickDelta, PoseStack matrices,
                                       MultiBufferSource vcp, int light, CallbackInfo ci) {
        if (ClientLinks.hasLook(entity) && ClientLinks.renderAsNpc(entity, yaw, tickDelta, matrices, vcp, light)) {
            ci.cancel();
        }
    }

    /** Pose de cuerpo entero de las animaciones (tumbarse, ir en brazos...) en jugadores y aldeanos. */
    @Inject(method = "setupRotations", at = @At("TAIL"))
    private void annisnpc$rootPose(LivingEntity entity, PoseStack matrices, float animationProgress, float bodyYaw,
                                   float tickDelta, CallbackInfo ci) {
        com.annis.npc.client.NpcRenderer.applyRoot(matrices, com.annis.npc.client.AnimOverlay.root(entity, tickDelta));
    }
}
