package com.starlevi.starframes.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.starlevi.starframes.block.ScreenBlock;
import com.starlevi.starframes.block.ScreenBlockEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/**
 * Dibuja el video de la pantalla: un rectangulo del tamaño configurado, delante de la placa, con el
 * video adentro sin deformarlo (bordes negros si la proporcion no coincide).
 */
public class ScreenRenderer implements BlockEntityRenderer<ScreenBlockEntity, ScreenRenderer.State> {
    private static final Identifier BLACK = Identifier.withDefaultNamespace("textures/block/black_concrete.png");

    public static class State extends BlockEntityRenderState {
        Direction facing = Direction.NORTH;
        float width, height, offX, offY;
        boolean bright;
        Identifier video;
        float videoAspect;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(ScreenBlockEntity be, State s, float partial, Vec3 camera, ModelFeatureRenderer.CrumblingOverlay crumbling) {
        BlockEntityRenderer.super.extractRenderState(be, s, partial, camera, crumbling);
        ScreenBlockEntity.CLIENT.add(be);
        s.facing = be.getBlockState().hasProperty(ScreenBlock.FACING) ? be.getBlockState().getValue(ScreenBlock.FACING) : Direction.NORTH;
        s.width = be.width();
        s.height = be.height();
        s.offX = be.offsetX();
        s.offY = be.offsetY();
        s.bright = be.bright();
        s.video = null;
        if (!be.channel().isEmpty()) {
            int[] t = ClientChannels.texture(be.channel());
            if (t != null) {
                s.video = ClientChannels.textureId(be.channel());
                s.videoAspect = t[0] / (float) Math.max(1, t[1]);
            }
        }
    }

    @Override
    public void submit(State s, PoseStack ps, SubmitNodeCollector collector, CameraRenderState camera) {
        ps.pushPose();
        ps.translate(0.5f, 0f, 0.5f);
        float yaw = switch (s.facing) {
            case EAST -> 90;
            case NORTH -> 180;
            case WEST -> 270;
            default -> 0;
        };
        ps.mulPose(Axis.YP.rotationDegrees(yaw));
        // delante de la placa (que ocupa el pixel de atras)
        ps.translate(0, 0, -0.5f + 1 / 16f + 0.002f);
        int light = s.bright ? LightTexture.FULL_BRIGHT : s.lightCoords;
        float x0 = -s.width / 2 + s.offX, x1 = s.width / 2 + s.offX, y0 = s.offY, y1 = s.offY + s.height;
        // fondo negro (lo que no cubre el video, o todo si no hay nada)
        collector.submitCustomGeometry(ps, RenderTypes.entitySolid(BLACK), (pose, vc) -> quad(pose, vc, x0, y0, x1, y1, 0, 0, 1, 1, light, 0xFF101010));
        if (s.video != null) {
            // el video adentro, sin deformar
            float boxAspect = s.width / Math.max(0.01f, s.height);
            float vx0 = x0, vx1 = x1, vy0 = y0, vy1 = y1;
            if (s.videoAspect > boxAspect) {
                float h = s.width / s.videoAspect, mid = (y0 + y1) / 2;
                vy0 = mid - h / 2;
                vy1 = mid + h / 2;
            } else {
                float w = s.height * s.videoAspect, mid = (x0 + x1) / 2;
                vx0 = mid - w / 2;
                vx1 = mid + w / 2;
            }
            final float a = vx0, b = vy0, c = vx1, d = vy1;
            ps.translate(0, 0, 0.002f);
            collector.submitCustomGeometry(ps, RenderTypes.entityCutoutNoCull(s.video), (pose, vc) -> quad(pose, vc, a, b, c, d, 0, 1, 1, 0, light, 0xFFFFFFFF));
        }
        ps.popPose();
    }

    /** Un rectangulo mirando hacia +z (hacia quien mira la pantalla); u0,v0 abajo a la izquierda. */
    private static void quad(PoseStack.Pose pose, VertexConsumer vc, float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1,
                             int light, int color) {
        vc.addVertex(pose, x0, y0, 0).setColor(color).setUv(u0, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 0, 1);
        vc.addVertex(pose, x1, y0, 0).setColor(color).setUv(u1, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 0, 1);
        vc.addVertex(pose, x1, y1, 0).setColor(color).setUv(u1, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 0, 1);
        vc.addVertex(pose, x0, y1, 0).setColor(color).setUv(u0, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 0, 1);
    }

    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 160;
    }
}
