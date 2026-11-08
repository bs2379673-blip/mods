package com.leon.saintsdragons.client.renderer.block;

import com.leon.saintsdragons.client.model.block.DraconicCrucibleAnimations;
import com.leon.saintsdragons.client.model.block.DraconicCrucibleEntity;
import com.leon.saintsdragons.common.SaintsDragonsCommon;
import com.leon.saintsdragons.common.block.DraconicCrucibleBlock;
import com.leon.saintsdragons.common.block.DraconicCrucibleBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/** Port 1.21.11: se anota la animacion al juntar y se posa y dibuja al momento de dibujar. */
public class DraconicCrucibleRenderer implements BlockEntityRenderer<DraconicCrucibleBlockEntity, DraconicCrucibleRenderer.State> {
    private static final Identifier INACTIVE_TEXTURE =
            SaintsDragonsCommon.rl("textures/block/draconic_crucible.png");
    private static final Identifier ACTIVE_TEXTURE =
            SaintsDragonsCommon.rl("textures/block/draconic_crucible_active.png");
    private final DraconicCrucibleEntity model;

    public static class State extends BlockEntityRenderState {
        AnimationDefinition animation;
        long time;
        boolean active;
        Direction facing = Direction.NORTH;
    }

    public DraconicCrucibleRenderer(BlockEntityRendererProvider.Context context) {
        this.model = new DraconicCrucibleEntity(context.bakeLayer(DraconicCrucibleEntity.LAYER_LOCATION));
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(DraconicCrucibleBlockEntity crucible, State s, float partialTick, Vec3 camera,
                                   ModelFeatureRenderer.CrumblingOverlay crumbling) {
        BlockEntityRenderer.super.extractRenderState(crucible, s, partialTick, camera, crumbling);
        s.active = crucible.getBlockState().getValue(DraconicCrucibleBlock.LIT);
        boolean open = crucible.hasAnimationState() ? crucible.isOpen() : !s.active;
        s.time = crucible.hasAnimationState() ? crucible.getAnimationTimeMillis(partialTick) : 1_000L;
        s.animation = open ? DraconicCrucibleAnimations.OPEN : DraconicCrucibleAnimations.CLOSE;
        s.facing = crucible.getBlockState().getValue(DraconicCrucibleBlock.FACING);
    }

    @Override
    public void submit(State s, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        poseStack.pushPose();
        poseStack.translate(0.5D, 1.5D, 0.5D);
        poseStack.mulPose(Axis.YP.rotationDegrees(-s.facing.toYRot()));
        poseStack.scale(1.0F, -1.0F, -1.0F);
        Identifier texture = s.active ? ACTIVE_TEXTURE : INACTIVE_TEXTURE;
        int light = s.lightCoords;
        collector.submitCustomGeometry(poseStack, net.minecraft.client.renderer.rendertype.RenderTypes.entityCutoutNoCull(texture), (pose, vc) -> {
            this.model.animate(s.animation, s.time);
            PoseStack ps = new PoseStack();
            ps.last().set(pose);
            this.model.renderToBuffer(ps, vc, light, OverlayTexture.NO_OVERLAY, -1);
        });
        poseStack.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }
}
