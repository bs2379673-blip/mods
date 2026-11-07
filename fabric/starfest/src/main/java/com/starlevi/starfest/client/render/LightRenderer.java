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
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Foco movil (cabezal que gira sobre una horquilla) y reflector al cielo. El cabezal sigue una
 * coreografia que cambia cada 8 compases (barrido, abanico, circulo, cruce), va al tiempo de la
 * cancion y se acelera en la parte fuerte; el haz cambia de color con la paleta y late con los golpes.
 */
public final class LightRenderer implements BlockEntityRenderer<ShowBlockEntity, LightRenderer.State> {
    private static final Identifier METAL = Identifier.fromNamespaceAndPath(StarFest.MOD_ID, "textures/entity/metal.png");
    private static final Identifier METAL_DARK = Identifier.fromNamespaceAndPath(StarFest.MOD_ID, "textures/entity/metal_oscuro.png");
    private final boolean searchlight;

    public static final class State extends BlockEntityRenderState {
        Direction facing = Direction.UP;
        float pan, tilt, bright;
        int color;
        boolean playing;
    }

    public LightRenderer(boolean searchlight) {
        this.searchlight = searchlight;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(ShowBlockEntity be, State s, float partial, Vec3 camera, ModelFeatureRenderer.CrumblingOverlay crumbling) {
        BlockEntityRenderer.super.extractRenderState(be, s, partial, camera, crumbling);
        Beat b = Show.beatAt(be.getBlockPos());
        int slot = Palette.slot(be.getBlockPos());
        long now = System.nanoTime();
        float dt = be.lastFrame == 0 ? 0.05f : Math.min(0.1f, (now - be.lastFrame) / 1.0e9f);
        be.lastFrame = now;
        float[] target = this.searchlight ? searchTarget(b, slot) : headTarget(b, slot);
        float k = 1 - (float) Math.exp(-dt * (b.playing() ? (b.drop() ? 10 : 6) : 1.5f));
        if (!be.init) {
            be.pan = target[0];
            be.tilt = target[1];
            be.init = true;
        }
        be.pan += Mth.wrapDegrees((target[0] - be.pan) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD * k;
        be.tilt += (target[1] - be.tilt) * k;
        s.facing = be.getBlockState().getValue(ShowBlock.FACING);
        s.pan = be.pan;
        s.tilt = be.tilt;
        s.playing = b.playing();
        s.color = Palette.color(b, this.searchlight ? slot % 2 : slot % 4);
        s.bright = brightness(b, slot);
    }

    @Override
    public void submit(State s, PoseStack ps, SubmitNodeCollector collector, CameraRenderState camera) {
        int light = s.lightCoords;
        ps.pushPose();
        ps.translate(0.5, 0.5, 0.5);
        ps.mulPose(s.facing.getRotation());
        ps.translate(0, -0.5, 0);
        ps.mulPose(Axis.YP.rotation(s.pan));
        float sc = this.searchlight ? 1.35f : 1.0f;
        RenderType metal = RenderTypes.entityCutout(METAL);
        // horquilla
        collector.submitCustomGeometry(ps, metal, (pose, vc) -> {
            Draw.box(pose, vc, -4 * sc, 2, -1.5f * sc, 4 * sc, 3.5f, 1.5f * sc, light, 0.9f);
            Draw.box(pose, vc, -4.2f * sc, 3.5f, -1.2f * sc, -3.2f * sc, 9.5f, 1.2f * sc, light, 0.85f);
            Draw.box(pose, vc, 3.2f * sc, 3.5f, -1.2f * sc, 4.2f * sc, 9.5f, 1.2f * sc, light, 0.85f);
        });
        // cabezal
        ps.pushPose();
        ps.translate(0, 8.0 / 16, 0);
        ps.mulPose(Axis.XP.rotation(s.tilt));
        float hw = this.searchlight ? 3.6f : 2.7f;
        float hl = this.searchlight ? 4.2f : 3.6f;
        collector.submitCustomGeometry(ps, metal, (pose, vc) -> Draw.box(pose, vc, -hw - 0.3f, hl - 0.8f, -hw - 0.3f, hw + 0.3f, hl + 0.2f, hw + 0.3f, light, 1.0f));
        collector.submitCustomGeometry(ps, RenderTypes.entityCutout(METAL_DARK), (pose, vc) -> Draw.box(pose, vc, -hw, -hl, -hw, hw, hl - 0.8f, hw, light, 1.0f));
        // --- luz
        int c = s.color;
        float bright = s.bright;
        float lens = (hw - 0.4f) / 16;
        ps.translate(0, (hl + 0.25f) / 16, 0);
        boolean playing = s.playing;
        boolean search = this.searchlight;
        collector.submitCustomGeometry(ps, Draw.light(), (pose, vc) -> {
            Draw.glowDisc(pose, vc, 0, lens, Palette.r(c), Palette.g(c), Palette.b(c), playing ? 0.35f + 0.65f * bright : 0.12f);
            if (playing && bright > 0.02f) {
                if (search) {
                    Draw.beam(pose, vc, 110, lens * 0.9f, 4.5f, Palette.r(c), Palette.g(c), Palette.b(c), 0.42f * bright);
                } else {
                    Draw.beam(pose, vc, 26, lens * 0.8f, 1.7f, Palette.r(c), Palette.g(c), Palette.b(c), 0.55f * bright);
                }
            }
        });
        ps.popPose();
        ps.popPose();
    }

    /** Brillo del haz: sigue el volumen y los golpes; en la parte fuerte hay estrobo con los agudos. */
    private static float brightness(Beat b, int slot) {
        if (!b.playing()) {
            return 0;
        }
        float v = 0.35f + 0.65f * Math.max(b.level(), b.pulse());
        if (b.drop() && b.high() > 0.55f) {
            boolean on = ((int) (b.beats() * 4) + slot) % 2 == 0;
            v *= on ? 1.0f : 0.15f;
        }
        return Math.min(1.0f, v);
    }

    /** {giro, inclinacion} del foco segun la figura de la parte actual de la cancion. */
    private static float[] headTarget(Beat b, int slot) {
        if (!b.playing()) {
            return new float[]{0, 0};
        }
        float t = b.beats() * (b.drop() ? 2 : 1);
        float pi = Mth.PI;
        int idx = slot % 8;
        int side = slot % 2 == 0 ? 1 : -1;
        float pan, tilt;
        switch (b.section() % 4) {
            case 0 -> { // barrido de lado a lado, en espejo
                pan = side * Mth.sin(t * pi / 4) * 1.1f;
                tilt = 0.55f + 0.25f * Mth.sin(t * pi / 2);
            }
            case 1 -> { // abanico que se abre y se cierra
                pan = (idx - 3.5f) * 0.28f + 0.15f * Mth.sin(t * pi / 2);
                tilt = 0.25f + 0.55f * Math.abs(Mth.sin(t * pi / 4));
            }
            case 2 -> { // circulos
                pan = t * pi / 4 + idx * pi / 4;
                tilt = 0.6f + 0.1f * Mth.sin(t * pi);
            }
            default -> { // cruce: unos van y otros vienen
                pan = side * 0.85f * Mth.sin(t * pi / 4);
                tilt = 0.4f + 0.4f * Mth.sin(t * pi / 2 + (side > 0 ? 0 : pi));
            }
        }
        tilt += b.kick() * 0.08f;
        return new float[]{pan, tilt};
    }

    /** {giro, inclinacion} del reflector: barre el cielo despacio, cruzandose con los demas. */
    private static float[] searchTarget(Beat b, int slot) {
        if (!b.playing()) {
            return new float[]{0, 0};
        }
        float t = b.beats() * (b.drop() ? 1.0f : 0.5f);
        float pi = Mth.PI;
        int side = slot % 2 == 0 ? 1 : -1;
        float pan = (slot % 4) * pi / 2 + side * t * pi / 16;
        float tilt = 0.22f + 0.18f * Mth.sin(t * pi / 8 + slot % 3);
        return new float[]{pan, tilt};
    }

    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }

    @Override
    public int getViewDistance() {
        return this.searchlight ? 256 : 128;
    }
}
