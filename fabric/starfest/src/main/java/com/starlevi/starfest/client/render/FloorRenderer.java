package com.starlevi.starfest.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.starlevi.starfest.block.ShowBlockEntity;
import com.starlevi.starfest.client.Beat;
import com.starlevi.starfest.client.Palette;
import com.starlevi.starfest.client.Show;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * Piso LED: cada baldosa se ilumina segun la figura de la parte de la cancion: tablero que cambia con
 * cada tiempo, ondas que salen del centro, chispas con los agudos y barras de ecualizador. En la parte
 * fuerte todo el piso destella con el bombo. Sin musica respira despacio en violeta.
 */
public final class FloorRenderer implements BlockEntityRenderer<ShowBlockEntity, FloorRenderer.State> {
    public static final class State extends BlockEntityRenderState {
        final float[] rgb = new float[3];
        float alpha;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(ShowBlockEntity be, State s, float partial, Vec3 camera, ModelFeatureRenderer.CrumblingOverlay crumbling) {
        BlockEntityRenderer.super.extractRenderState(be, s, partial, camera, crumbling);
        BlockPos p = be.getBlockPos();
        s.alpha = tile(Show.beatAt(p), p, Show.centerAt(p), s.rgb);
    }

    @Override
    public void submit(State s, PoseStack ps, SubmitNodeCollector collector, CameraRenderState camera) {
        if (s.alpha <= 0.01f) {
            return;
        }
        float r = s.rgb[0], g = s.rgb[1], b = s.rgb[2], a = s.alpha;
        float in = 0.07f;
        collector.submitCustomGeometry(ps, Draw.LIGHT, (pose, vc) -> {
            Draw.glowRect(pose, vc, in, in, 1 - in, 1 - in, 1.003f, r, g, b, a);
            // un brillo mas suave en el borde, para que la baldosa no parezca un sticker
            Draw.glowRect(pose, vc, 0.02f, 0.02f, 0.98f, 0.98f, 1.002f, r, g, b, a * 0.25f);
        });
    }

    private static float tile(Beat b, BlockPos p, Vec3 center, float[] rgb) {
        int x = p.getX(), z = p.getZ();
        if (!b.playing()) {
            double t = System.nanoTime() / 1.0e9;
            float v = 0.07f + 0.05f * (float) Math.sin(t * 0.8 + (x + z) * 0.35);
            set(rgb, 0x6A2CFF);
            return v;
        }
        float beats = b.beats();
        float a;
        int c;
        switch (b.section() % 4) {
            case 0 -> { // tablero
                boolean on = ((x + z + b.beat()) & 1) == 0;
                c = Palette.main(b, on ? 0 : 1);
                a = on ? 0.35f + 0.65f * Math.max(b.kick(), b.bass() * 0.6f) : 0.12f;
            }
            case 1 -> { // ondas desde el centro
                double d = center == null ? Math.hypot(x % 16, z % 16) : Math.hypot(x + 0.5 - center.x, z + 0.5 - center.z);
                float v = (float) (d / 4.0 - beats * 0.5);
                float f = v - (float) Math.floor(v);
                float band = (float) Math.exp(-(f - 0.15) * (f - 0.15) / 0.012);
                c = Palette.main(b, (int) Math.floor(v));
                a = 0.08f + band * (0.4f + 0.6f * b.level());
            }
            case 2 -> { // chispas con los agudos
                int step = (int) Math.floor(beats * 2);
                int h = (x * 734287 ^ z * 912931 ^ step * 438289) & 0xFFFF;
                boolean lit = h % 100 < 12 + 40 * b.high();
                c = Palette.main(b, h % 4);
                a = lit ? 0.9f : 0.06f + 0.2f * b.kick();
            }
            default -> { // barras de ecualizador
                int col = Math.floorMod(x, 8);
                float val = col < 3 ? b.bass() : col < 6 ? b.mid() : b.high();
                float row = Math.floorMod(z, 8) / 8.0f;
                boolean lit = row < val;
                c = Palette.main(b, col / 3);
                a = lit ? 0.55f + 0.45f * (1 - row) : 0.05f;
            }
        }
        set(rgb, c);
        if (b.drop() && b.kick() > 0.5f) {
            // destello blanco con el bombo en la parte fuerte
            float w = (b.kick() - 0.5f) * 1.2f;
            rgb[0] += (1 - rgb[0]) * w;
            rgb[1] += (1 - rgb[1]) * w;
            rgb[2] += (1 - rgb[2]) * w;
            a = Math.max(a, w);
        }
        return Math.min(1, a);
    }

    private static void set(float[] rgb, int c) {
        rgb[0] = Palette.r(c);
        rgb[1] = Palette.g(c);
        rgb[2] = Palette.b(c);
    }

    @Override
    public int getViewDistance() {
        return 96;
    }
}
