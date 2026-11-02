package com.starlevi.starspirits.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;

/**
 * Luz aditiva (haz de extraccion, anillos, estrellita del espiritu activo). Usa el programa de los
 * rayos del juego: sin shaders propios, se ve igual con Iris y paquetes de shaders.
 */
public final class Glow extends RenderType {
    public static final RenderType LIGHT = create("starspirits_light", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS,
            8192, false, true, CompositeState.builder()
                    .setShaderState(RENDERTYPE_LIGHTNING_SHADER)
                    .setTransparencyState(LIGHTNING_TRANSPARENCY)
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .setOutputState(WEATHER_TARGET)
                    .createCompositeState(false));

    private Glow(String name, VertexFormat fmt, VertexFormat.Mode mode, int size, boolean crumbling, boolean sort,
                 Runnable setup, Runnable clear) {
        super(name, fmt, mode, size, crumbling, sort, setup, clear);
    }

    private static void v(PoseStack.Pose pose, VertexConsumer vc, float x, float y, float z, float r, float g, float b, float a) {
        vc.addVertex(pose, x, y, z).setColor(r, g, b, Math.max(0, Math.min(1, a)));
    }

    /** Haz vertical desde y=0 hasta "length", radio r0 -> r1, que se apaga hacia la punta. */
    public static void beam(PoseStack.Pose pose, VertexConsumer vc, float length, float r0, float r1, float r, float g, float b, float a) {
        // nucleo apenas mas claro que el color (si fuera blanco se perderia la rareza) y halo de color
        cone(pose, vc, length, r0 * 0.4f, r1 * 0.4f, Math.min(1, r * 0.75f + 0.2f), Math.min(1, g * 0.75f + 0.2f),
                Math.min(1, b * 0.75f + 0.2f), a * 0.6f, a * 0.04f, 12);
        cone(pose, vc, length, r0, r1, r, g, b, a * 0.45f, 0f, 16);
    }

    private static void cone(PoseStack.Pose pose, VertexConsumer vc, float length, float r0, float r1, float r, float g, float b,
                             float a0, float a1, int seg) {
        int parts = 6;
        for (int p = 0; p < parts; p++) {
            float t0 = p / (float) parts, t1 = (p + 1) / (float) parts;
            float y0 = length * t0 * t0, y1 = length * t1 * t1;
            float ra = r0 + (r1 - r0) * (y0 / length), rb = r0 + (r1 - r0) * (y1 / length);
            float aa = a0 + (a1 - a0) * Mth.sqrt(y0 / length), ab = a0 + (a1 - a0) * Mth.sqrt(y1 / length);
            for (int i = 0; i < seg; i++) {
                float an0 = (float) (2 * Math.PI * i / seg), an1 = (float) (2 * Math.PI * (i + 1) / seg);
                float c0 = Mth.cos(an0), s0 = Mth.sin(an0), c1 = Mth.cos(an1), s1 = Mth.sin(an1);
                v(pose, vc, ra * c0, y0, ra * s0, r, g, b, aa);
                v(pose, vc, ra * c1, y0, ra * s1, r, g, b, aa);
                v(pose, vc, rb * c1, y1, rb * s1, r, g, b, ab);
                v(pose, vc, rb * c0, y1, rb * s0, r, g, b, ab);
            }
        }
    }

    /** Anillo horizontal (en el plano XZ) de radio "radius" y ancho "width". */
    public static void ring(PoseStack.Pose pose, VertexConsumer vc, float y, float radius, float width, float r, float g, float b, float a) {
        int seg = 40;
        float ri = Math.max(0, radius - width / 2), ro = radius + width / 2;
        for (int i = 0; i < seg; i++) {
            float an0 = (float) (2 * Math.PI * i / seg), an1 = (float) (2 * Math.PI * (i + 1) / seg);
            float c0 = Mth.cos(an0), s0 = Mth.sin(an0), c1 = Mth.cos(an1), s1 = Mth.sin(an1);
            v(pose, vc, ri * c0, y, ri * s0, r, g, b, a * 0.2f);
            v(pose, vc, ro * c0, y, ro * s0, r, g, b, a);
            v(pose, vc, ro * c1, y, ro * s1, r, g, b, a);
            v(pose, vc, ri * c1, y, ri * s1, r, g, b, a * 0.2f);
        }
    }

    /** Disco brillante horizontal. */
    public static void disc(PoseStack.Pose pose, VertexConsumer vc, float y, float radius, float r, float g, float b, float a) {
        int seg = 20;
        for (int i = 0; i < seg; i++) {
            float an0 = (float) (2 * Math.PI * i / seg), an1 = (float) (2 * Math.PI * (i + 1) / seg);
            v(pose, vc, 0, y, 0, r, g, b, a);
            v(pose, vc, radius * Mth.cos(an0), y, radius * Mth.sin(an0), r, g, b, 0);
            v(pose, vc, radius * Mth.cos(an1), y, radius * Mth.sin(an1), r, g, b, 0);
            v(pose, vc, 0, y, 0, r, g, b, a);
        }
    }

    /** Estrella de 4 puntas plana (en el plano XY, mirando a la camara si se gira antes). */
    public static void star(PoseStack.Pose pose, VertexConsumer vc, float size, float r, float g, float b, float a) {
        float in = size * 0.28f;
        for (int k = 0; k < 4; k++) {
            float an = (float) (Math.PI / 2 * k), an1 = an + (float) Math.PI / 4, an2 = an - (float) Math.PI / 4;
            v(pose, vc, 0, 0, 0, r, g, b, a);
            v(pose, vc, Mth.cos(an2) * in, Mth.sin(an2) * in, 0, r, g, b, a * 0.6f);
            v(pose, vc, Mth.cos(an) * size, Mth.sin(an) * size, 0, r, g, b, a * 0.2f);
            v(pose, vc, Mth.cos(an1) * in, Mth.sin(an1) * in, 0, r, g, b, a * 0.6f);
        }
    }

    /** Orbe brillante: varias estrellas cruzadas (se ve como una semilla de luz desde cualquier lado). */
    public static void orb(PoseStack pose, VertexConsumer vc, float size, float spin, float r, float g, float b, float a) {
        for (int k = 0; k < 3; k++) {
            pose.pushPose();
            pose.mulPose(com.mojang.math.Axis.YP.rotation(spin + k * (float) Math.PI / 3));
            star(pose.last(), vc, size, r, g, b, a);
            pose.popPose();
        }
        pose.pushPose();
        pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(90));
        pose.mulPose(com.mojang.math.Axis.ZP.rotation(spin));
        star(pose.last(), vc, size * 0.8f, r, g, b, a * 0.8f);
        pose.popPose();
    }
}
