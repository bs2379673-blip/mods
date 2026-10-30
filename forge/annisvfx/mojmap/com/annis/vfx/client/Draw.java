package com.annis.vfx.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Dibujo de un fotograma. Se dibuja como las entidades (capas de entidad del juego), asi funciona
 * igual con y sin Iris: "glow" suma luz (capa aditiva, como los ojos de las aranas) y "solid" es
 * translucido normal (hojas, petalos).
 */
public final class Draw {
    public static final int FULL_BRIGHT = LightTexture.FULL_BRIGHT;

    public final PoseStack matrices;
    public final MultiBufferSource consumers;
    public final Vec3 cam;
    public final Quaternionf camRot;
    /** Segundos desde que empezo el efecto (con fraccion de tick). */
    public float t;

    public Draw(PoseStack matrices, MultiBufferSource consumers, Vec3 cam, Quaternionf camRot) {
        this.matrices = matrices;
        this.consumers = consumers;
        this.cam = cam;
        this.camRot = camRot;
    }

    /** La ultima capa pedida suma luz (ver quad). */
    private boolean additive;

    public VertexConsumer glow(ResourceLocation tex) {
        this.additive = true;
        return this.consumers.getBuffer(RenderType.eyes(tex));
    }

    public VertexConsumer solid(ResourceLocation tex) {
        this.additive = false;
        return this.consumers.getBuffer(RenderType.entityTranslucent(tex));
    }

    /** Un vertice con todo lo que piden las capas de entidad. */
    private static void v(VertexConsumer vc, Matrix4f m, PoseStack.Pose e, float x, float y, float z,
                          float u, float w, int r, int g, int b, int a, Vector3f n) {
        vc.addVertex(m, x, y, z).setColor(r, g, b, a).setUv(u, w).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(FULL_BRIGHT).setNormal(e, n.x, n.y, n.z);
    }

    /**
     * Cuadrado con centro en (wx,wy,wz) del mundo, ejes ax y ay (ya escalados, en bloques),
     * por las dos caras. color = 0xRRGGBB, alpha 0..1.
     */
    public void quad(VertexConsumer vc, double wx, double wy, double wz, Vector3f ax, Vector3f ay, int color, float alpha) {
        if (alpha <= 0.003f) {
            return;
        }
        int r = (color >> 16) & 255, g = (color >> 8) & 255, b = color & 255;
        int a = Math.max(0, Math.min(255, Math.round(alpha * 255)));
        if (this.additive) {
            // la capa de luz suma: la transparencia va en el brillo del color
            float k = Math.min(1, alpha);
            r = Math.round(r * k);
            g = Math.round(g * k);
            b = Math.round(b * k);
            a = 255;
        }
        this.matrices.pushPose();
        this.matrices.translate(wx - this.cam.x, wy - this.cam.y, wz - this.cam.z);
        PoseStack.Pose e = this.matrices.last();
        Matrix4f m = e.pose();
        Vector3f n = new Vector3f(ax).cross(ay);
        if (n.lengthSquared() < 1.0e-8f) {
            n.set(0, 1, 0);
        }
        n.normalize();
        float x0 = -ax.x - ay.x, y0 = -ax.y - ay.y, z0 = -ax.z - ay.z;
        float x1 = ax.x - ay.x, y1 = ax.y - ay.y, z1 = ax.z - ay.z;
        float x2 = ax.x + ay.x, y2 = ax.y + ay.y, z2 = ax.z + ay.z;
        float x3 = -ax.x + ay.x, y3 = -ax.y + ay.y, z3 = -ax.z + ay.z;
        // cara de delante
        v(vc, m, e, x0, y0, z0, 0, 1, r, g, b, a, n);
        v(vc, m, e, x1, y1, z1, 1, 1, r, g, b, a, n);
        v(vc, m, e, x2, y2, z2, 1, 0, r, g, b, a, n);
        v(vc, m, e, x3, y3, z3, 0, 0, r, g, b, a, n);
        // cara de atras
        Vector3f nb = new Vector3f(n).negate();
        v(vc, m, e, x3, y3, z3, 0, 0, r, g, b, a, nb);
        v(vc, m, e, x2, y2, z2, 1, 0, r, g, b, a, nb);
        v(vc, m, e, x1, y1, z1, 1, 1, r, g, b, a, nb);
        v(vc, m, e, x0, y0, z0, 0, 1, r, g, b, a, nb);
        this.matrices.popPose();
    }

    /** Cuadrado que siempre mira a la camara (size = medio lado). */
    public void billboard(VertexConsumer vc, double wx, double wy, double wz, float size, float spin, int color, float alpha) {
        Vector3f ax = new Vector3f(size, 0, 0).rotateZ(spin).rotate(this.camRot);
        Vector3f ay = new Vector3f(0, size, 0).rotateZ(spin).rotate(this.camRot);
        quad(vc, wx, wy, wz, ax, ay, color, alpha);
    }

    /** Cuadrado tumbado en el piso (half = medio lado), girado "spin" sobre la vertical. */
    public void flat(VertexConsumer vc, double wx, double wy, double wz, float half, float spin, int color, float alpha) {
        Vector3f ax = new Vector3f(half, 0, 0).rotateY(spin);
        Vector3f ay = new Vector3f(0, 0, half).rotateY(spin);
        quad(vc, wx, wy, wz, ax, ay, color, alpha);
    }

    /** Haz vertical que gira sobre su eje para mirar a la camara (base en wy, alto h, ancho w/2). */
    public void beam(VertexConsumer vc, double wx, double wy, double wz, float halfW, float h, int color, float alpha) {
        double dx = this.cam.x - wx, dz = this.cam.z - wz;
        float yaw = (float) Math.atan2(dx, dz);
        Vector3f ax = new Vector3f(halfW, 0, 0).rotateY(yaw);
        Vector3f ay = new Vector3f(0, h / 2, 0);
        quad(vc, wx, wy + h / 2, wz, ax, ay, color, alpha);
    }

    public static float clamp01(float v) {
        return Math.max(0, Math.min(1, v));
    }

    public static float smooth(float x) {
        x = clamp01(x);
        return x * x * (3 - 2 * x);
    }

    /** Sube entre a y b, baja entre c y d. */
    public static float env(float t, float a, float b, float c, float d) {
        if (t <= a || t >= d) {
            return 0;
        }
        if (t < b) {
            return smooth((t - a) / (b - a));
        }
        if (t <= c) {
            return 1;
        }
        return 1 - smooth((t - c) / (d - c));
    }

    public static int lerpColor(int c1, int c2, float w) {
        w = clamp01(w);
        int r = (int) (((c1 >> 16) & 255) * (1 - w) + ((c2 >> 16) & 255) * w);
        int g = (int) (((c1 >> 8) & 255) * (1 - w) + ((c2 >> 8) & 255) * w);
        int b = (int) ((c1 & 255) * (1 - w) + (c2 & 255) * w);
        return (r << 16) | (g << 8) | b;
    }
}
