package com.leon.saintsdragons.client.renderer.block;

import com.leon.saintsdragons.client.model.block.DraconianNucleusAnimations;
import com.leon.saintsdragons.client.model.block.DraconianNucleusModel;
import com.leon.saintsdragons.common.SaintsDragonsCommon;
import com.leon.saintsdragons.common.block.DraconianNucleusBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/** Port 1.21.11: se anota la animacion al juntar y se posa y dibuja al momento de dibujar. */
public class DraconianNucleusRenderer implements BlockEntityRenderer<DraconianNucleusBlockEntity, DraconianNucleusRenderer.State> {
    private static final Identifier TEXTURE =
            SaintsDragonsCommon.rl("textures/block/draconic_nucleus.png");
    private final DraconianNucleusModel model;

    public static class State extends BlockEntityRenderState {
        AnimationDefinition animation;
        long time;
    }

    public DraconianNucleusRenderer(BlockEntityRendererProvider.Context context) {
        this.model = new DraconianNucleusModel(context.bakeLayer(DraconianNucleusModel.LAYER_LOCATION));
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(DraconianNucleusBlockEntity nucleus, State s, float partialTick, Vec3 camera,
                                   ModelFeatureRenderer.CrumblingOverlay crumbling) {
        BlockEntityRenderer.super.extractRenderState(nucleus, s, partialTick, camera, crumbling);
        s.animation = nucleus.hasActiveEncounter() ? DraconianNucleusAnimations.SPAWN : DraconianNucleusAnimations.IDLE;
        s.time = nucleus.getAnimationTimeMillis(partialTick);
    }

    @Override
    public void submit(State s, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        poseStack.pushPose();
        poseStack.translate(0.5D, 1.5D, 0.5D);
        poseStack.scale(1.0F, -1.0F, -1.0F);
        int light = s.lightCoords;
        collector.submitCustomGeometry(poseStack, net.minecraft.client.renderer.rendertype.RenderTypes.entityTranslucent(TEXTURE), (pose, vc) -> {
            this.model.animate(s.animation, s.time);
            PoseStack ps = new PoseStack();
            ps.last().set(pose);
            this.model.renderToBuffer(ps, vc, light, OverlayTexture.NO_OVERLAY, -1);
        });
        poseStack.popPose();
    }
}
