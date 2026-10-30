package com.annis.npc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;

/** Capa invisible en el render del jugador: cuando se graba, lee la pose final de cada hueso. */
public class RecorderFeature extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    public RecorderFeature(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> context) {
        super(context);
    }

    @Override
    public void render(PoseStack matrices, MultiBufferSource vertexConsumers, int light,
                       AbstractClientPlayer entity, float limbAngle, float limbDistance, float tickDelta,
                       float animationProgress, float headYaw, float headPitch) {
        if (MoveRecorder.capturing()) {
            MoveRecorder.capture(entity, this.getParentModel());
        }
    }
}
