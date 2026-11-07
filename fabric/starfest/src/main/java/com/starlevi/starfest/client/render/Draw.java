package com.starlevi.starfest.client.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * Dibujo de StarFest. Las luces usan el programa de los rayos del juego (rendertype_lightning) con
 * mezcla aditiva y sin escribir profundidad: sin shaders propios, asi se ven en cualquier placa de
 * video y tambien con Iris y paquetes de shaders.
 */
public final class Draw {
    /** El programa de los rayos, sin escribir profundidad y sin cara oculta (los haces se suman y no se tapan). */
    private static final RenderPipeline LIGHT_PIPELINE = RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("starfest", "pipeline/luz"))
            .withVertexShader("core/rendertype_lightning")
            .withFragmentShader("core/rendertype_lightning")
            .withBlend(BlendFunction.LIGHTNING)
            .withDepthWrite(false)
            .withCull(false)
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
            .build();
    /** Luz aditiva. */
    private static final RenderType LIGHT = RenderType.create("starfest_light",
            RenderSetup.builder(LIGHT_PIPELINE).setOutputTarget(OutputTarget.WEATHER_TARGET).sortOnUpload().bufferSize(1 << 16).createRenderSetup());

    static {
        com.starlevi.starfest.client.IrisCompat.assign(LIGHT_PIPELINE, "BASIC");
    }

    /**
     * La luz: sin shaders, la nuestra (sin profundidad); con un paquete de shaders, la de los rayos del juego,
     * que todos los paquetes saben dibujar.
     */
    public static RenderType light() {
        return com.starlevi.starfest.client.IrisCompat.shaders() ? net.minecraft.client.renderer.rendertype.RenderTypes.lightning() : LIGHT;
    }

    private Draw() {
    }

    // ------------------------------------------------------------------ piezas solidas (texturadas)

    /** Caja con textura (la imagen entera en cada cara). Medidas en pixeles (1/16 de bloque). */
    public static void box(PoseStack.Pose pose, VertexConsumer vc, float x0, float y0, float z0, float x1, float y1, float z1,
                           int light, float shade) {
        x0 /= 16;
        y0 /= 16;
        z0 /= 16;
        x1 /= 16;
        y1 /= 16;
        z1 /= 16;
        int c = (int) (255 * shade);
        // arriba, abajo, norte, sur, oeste, este
        face(pose, vc, light, c, 0, 1, 0, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0);
        face(pose, vc, light, c, 0, -1, 0, x0, y0, z1, x0, y0, z0, x1, y0, z0, x1, y0, z1);
        face(pose, vc, light, c, 0, 0, -1, x1, y1, z0, x1, y0, z0, x0, y0, z0, x0, y1, z0);
        face(pose, vc, light, c, 0, 0, 1, x0, y1, z1, x0, y0, z1, x1, y0, z1, x1, y1, z1);
        face(pose, vc, light, c, -1, 0, 0, x0, y1, z0, x0, y0, z0, x0, y0, z1, x0, y1, z1);
        face(pose, vc, light, c, 1, 0, 0, x1, y1, z1, x1, y0, z1, x1, y0, z0, x1, y1, z0);
    }

    private static void face(PoseStack.Pose pose, VertexConsumer vc, int light, int c, float nx, float ny, float nz,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz) {
        vert(pose, vc, ax, ay, az, 0, 0, light, c, nx, ny, nz);
        vert(pose, vc, bx, by, bz, 0, 1, light, c, nx, ny, nz);
        vert(pose, vc, cx, cy, cz, 1, 1, light, c, nx, ny, nz);
        vert(pose, vc, dx, dy, dz, 1, 0, light, c, nx, ny, nz);
    }

    public static void vert(PoseStack.Pose pose, VertexConsumer vc, float x, float y, float z, float u, float v, int light, int c,
                            float nx, float ny, float nz) {
        vc.addVertex(pose, x, y, z).setColor(c, c, c, 255).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                .setNormal(pose, nx, ny, nz);
    }

    /** Cuadrado texturado horizontal (disco de vinilo...), centro y radio en pixeles, girado "angle". */
    public static void disc(PoseStack.Pose pose, VertexConsumer vc, float cx, float y, float cz, float r, float angle, int light) {
        float x0 = cx / 16, z0 = cz / 16, rr = r / 16, yy = y / 16;
        float[][] k = {{-1, -1, 0, 0}, {-1, 1, 0, 1}, {1, 1, 1, 1}, {1, -1, 1, 0}};
        float s = Mth.sin(angle), co = Mth.cos(angle);
        for (float[] p : k) {
            float lx = p[0] * rr, lz = p[1] * rr;
            vert(pose, vc, x0 + lx * co - lz * s, yy, z0 + lx * s + lz * co, p[2], p[3], light, 255, 0, 1, 0);
        }
    }

    // ------------------------------------------------------------------ luz (aditiva)

    private static void lv(PoseStack.Pose pose, VertexConsumer vc, float x, float y, float z, float r, float g, float b, float a) {
        vc.addVertex(pose, x, y, z).setColor(r, g, b, Math.max(0, Math.min(1, a)));
    }

    /**
     * Haz de luz: cono a lo largo de +Y desde y=0 hasta "length" (bloques), radio r0 -> r1, que se
     * apaga hacia la punta. Dos capas: un nucleo brillante y un halo ancho y suave.
     */
    public static void beam(PoseStack.Pose pose, VertexConsumer vc, float length, float r0, float r1, float r, float g, float b,
                            float alpha) {
        cone(pose, vc, length, r0 * 0.45f, r1 * 0.45f, r, g, b, alpha, alpha * 0.05f, 12);
        cone(pose, vc, length, r0, r1, r, g, b, alpha * 0.35f, 0.0f, 16);
    }

    private static void cone(PoseStack.Pose pose, VertexConsumer vc, float length, float r0, float r1, float r, float g, float b,
                             float a0, float a1, int seg) {
        // en tramos, para que el brillo se apague gradualmente a lo largo del haz
        int parts = 6;
        for (int p = 0; p < parts; p++) {
            float t0 = p / (float) parts, t1 = (p + 1) / (float) parts;
            float y0 = length * t0 * t0, y1 = length * t1 * t1;
            float ra = r0 + (r1 - r0) * (y0 / length), rb = r0 + (r1 - r0) * (y1 / length);
            float aa = a0 + (a1 - a0) * (float) Math.sqrt(y0 / length), ab = a0 + (a1 - a0) * (float) Math.sqrt(y1 / length);
            for (int i = 0; i < seg; i++) {
                float an0 = (float) (2 * Math.PI * i / seg), an1 = (float) (2 * Math.PI * (i + 1) / seg);
                float c0 = Mth.cos(an0), s0 = Mth.sin(an0), c1 = Mth.cos(an1), s1 = Mth.sin(an1);
                lv(pose, vc, ra * c0, y0, ra * s0, r, g, b, aa);
                lv(pose, vc, ra * c1, y0, ra * s1, r, g, b, aa);
                lv(pose, vc, rb * c1, y1, rb * s1, r, g, b, ab);
                lv(pose, vc, rb * c0, y1, rb * s0, r, g, b, ab);
            }
        }
    }

    /** Disco brillante (lente) en el plano XZ, mirando a +Y. Radio en bloques. */
    public static void glowDisc(PoseStack.Pose pose, VertexConsumer vc, float y, float radius, float r, float g, float b, float a) {
        int seg = 14;
        for (int i = 0; i < seg; i++) {
            float an0 = (float) (2 * Math.PI * i / seg), an1 = (float) (2 * Math.PI * (i + 1) / seg);
            lv(pose, vc, 0, y, 0, r, g, b, a);
            lv(pose, vc, radius * Mth.cos(an0), y, radius * Mth.sin(an0), r, g, b, a * 0.6f);
            lv(pose, vc, radius * Mth.cos(an1), y, radius * Mth.sin(an1), r, g, b, a * 0.6f);
            lv(pose, vc, 0, y, 0, r, g, b, a);
        }
    }

    /** Rectangulo de luz en un plano horizontal (piso LED, luces de la cabina). Medidas en bloques. */
    public static void glowRect(PoseStack.Pose pose, VertexConsumer vc, float x0, float z0, float x1, float z1, float y,
                                float r, float g, float b, float a) {
        lv(pose, vc, x0, y, z0, r, g, b, a);
        lv(pose, vc, x0, y, z1, r, g, b, a);
        lv(pose, vc, x1, y, z1, r, g, b, a);
        lv(pose, vc, x1, y, z0, r, g, b, a);
    }

    /** Rectangulo de luz vertical en el plano XY (z fijo). */
    public static void glowRectV(PoseStack.Pose pose, VertexConsumer vc, float x0, float y0, float x1, float y1, float z,
                                 float r, float g, float b, float a) {
        lv(pose, vc, x0, y0, z, r, g, b, a);
        lv(pose, vc, x0, y1, z, r, g, b, a);
        lv(pose, vc, x1, y1, z, r, g, b, a);
        lv(pose, vc, x1, y0, z, r, g, b, a);
    }
}
