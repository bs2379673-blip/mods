package com.starlevi.starfest.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.starlevi.starfest.StarFest;
import com.starlevi.starfest.block.ShowBlock;
import com.starlevi.starfest.block.ShowBlockEntity;
import com.starlevi.starfest.client.Beat;
import com.starlevi.starfest.client.Palette;
import com.starlevi.starfest.client.Show;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/**
 * Cabina de DJ (decorativa): los dos platos giran cuando hay musica, el mezclador tiene vumetros de
 * graves, medios y agudos, y una franja de luz al frente late con el bombo.
 */
public final class BoothRenderer implements BlockEntityRenderer<ShowBlockEntity, BoothRenderer.State> {
    private static final Identifier VINYL = Identifier.fromNamespaceAndPath(StarFest.MOD_ID, "textures/entity/vinilo.png");

    public static final class State extends BlockEntityRenderState {
        Direction facing = Direction.NORTH;
        Beat beat = Beat.SILENCE;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(ShowBlockEntity be, State s, float partial, Vec3 camera, ModelFeatureRenderer.CrumblingOverlay crumbling) {
        BlockEntityRenderer.super.extractRenderState(be, s, partial, camera, crumbling);
        s.beat = Show.beatAt(be.getBlockPos());
        s.facing = be.getBlockState().getValue(ShowBlock.FACING);
    }

    @Override
    public void submit(State s, PoseStack ps, SubmitNodeCollector collector, CameraRenderState camera) {
        Beat b = s.beat;
        int light = s.lightCoords;
        ps.pushPose();
        ps.translate(0.5, 0, 0.5);
        // el modelo mira al norte; se gira hacia donde mira la cabina
        ps.mulPose(Axis.YP.rotationDegrees(-s.facing.toYRot() + 180));
        ps.translate(-0.5, 0, -0.5);
        float spin = b.playing() ? (float) (System.nanoTime() / 1.0e9 * 33.3 / 60 * Math.PI * 2) : 0;
        collector.submitCustomGeometry(ps, RenderTypes.entityCutout(VINYL), (pose, vc) -> {
            Draw.disc(pose, vc, 4, 13.6f, 9, 3.2f, spin, light);
            Draw.disc(pose, vc, 12, 13.6f, 9, 3.2f, -spin * 0.97f, light);
        });
        collector.submitCustomGeometry(ps, Draw.light(), (pose, glow) -> {
            // vumetros (3 columnas de 6 luces)
            float[] vals = {b.bass(), b.mid(), b.high()};
            for (int col = 0; col < 3; col++) {
                int c = Palette.main(b, col);
                for (int i = 0; i < 6; i++) {
                    boolean on = b.playing() && vals[col] > i / 6.0f;
                    float x0 = (6.9f + col * 0.8f) / 16, z0 = (12.0f - i * 0.9f) / 16;
                    float red = i >= 5 ? 1 : Palette.r(c), green = i >= 5 ? 0.2f : Palette.g(c), blue = i >= 5 ? 0.2f : Palette.b(c);
                    Draw.glowRect(pose, glow, x0, z0 - 0.6f / 16, x0 + 0.55f / 16, z0, 13.55f / 16, red, green, blue, on ? 0.9f : 0.08f);
                }
            }
            // franja de luz al frente (lado norte del modelo)
            int fc = Palette.main(b, 0);
            float fa = b.playing() ? 0.25f + 0.75f * Math.max(b.kick(), b.level() * 0.5f) : 0.08f;
            Draw.glowRectV(pose, glow, 0.06f, 4.0f / 16, 0.94f, 5.2f / 16, 1.9f / 16, Palette.r(fc), Palette.g(fc), Palette.b(fc), fa);
            Draw.glowRectV(pose, glow, 0.06f, 9.0f / 16, 0.94f, 9.6f / 16, 1.9f / 16, Palette.r(fc), Palette.g(fc), Palette.b(fc), fa * 0.6f);
        });
        ps.popPose();
    }
}
