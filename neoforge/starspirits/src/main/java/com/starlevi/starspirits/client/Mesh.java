package com.starlevi.starspirits.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Mallas suaves (no cubos) para los espiritus: bulbos, cupulas, petalos curvos, hojas, tallos.
 * Medidas en pixeles del modelo (y hacia abajo, como las piezas del juego). La textura se reparte
 * en zonas (ver {@link Uv}); las coordenadas de textura van de 0 a 1.
 */
public final class Mesh {
    /** Zonas de la textura de 256x256 (en pixeles). Las mismas usa tools/texturas.py. */
    public static final class Uv {
        public static final float[] BODY = {0, 0, 256, 112};
        public static final float[] LIMB = {0, 112, 64, 144};
        public static final float[] LID = {64, 112, 96, 128};
        public static final float[] STEM = {96, 112, 128, 144};
        public static final float[] PETAL = {128, 112, 192, 144};
        public static final float[] LEAF = {192, 112, 256, 144};
        public static final float[] CAP = {0, 144, 256, 224};
        public static final float[] UNDER = {0, 224, 64, 256};
        public static final float[] CENTER = {64, 224, 128, 256};
        public static final float[] EXTRA = {128, 224, 256, 256};
        public static final float SIZE = 256;

        private Uv() {
        }
    }

    /** Un vertice: posicion (pixeles), textura (0..1 de la imagen entera) y normal. */
    private final List<float[]> quads = new ArrayList<>();

    private static float u(float[] r, float t) {
        return (r[0] + (r[2] - r[0]) * t) / Uv.SIZE;
    }

    private static float v(float[] r, float t) {
        return (r[1] + (r[3] - r[1]) * t) / Uv.SIZE;
    }

    private void quad(float[] a, float[] b, float[] c, float[] d) {
        float[] q = new float[32];
        float[][] vs = {a, b, c, d};
        for (int i = 0; i < 4; i++) {
            System.arraycopy(vs[i], 0, q, i * 8, 8);
        }
        this.quads.add(q);
    }

    private static float[] vert(float x, float y, float z, float uu, float vv, float nx, float ny, float nz) {
        float l = Mth.sqrt(nx * nx + ny * ny + nz * nz);
        if (l < 1e-6f) {
            l = 1;
        }
        return new float[]{x, y, z, uu, vv, nx / l, ny / l, nz / l};
    }

    /**
     * Elipsoide (o parte): latitud de lat0 a lat1 (0 = arriba, PI = abajo). La textura se envuelve
     * en la zona: u = vuelta (0.5 = el frente, hacia -z), v = latitud.
     */
    public Mesh ellipsoid(float cx, float cy, float cz, float rx, float ry, float rz, float lat0, float lat1,
                          int seg, int rings, float[] uv) {
        for (int i = 0; i < rings; i++) {
            float t0 = i / (float) rings, t1 = (i + 1) / (float) rings;
            float th0 = lat0 + (lat1 - lat0) * t0, th1 = lat0 + (lat1 - lat0) * t1;
            for (int j = 0; j < seg; j++) {
                float s0 = j / (float) seg, s1 = (j + 1) / (float) seg;
                quad(ell(cx, cy, cz, rx, ry, rz, th0, s0, uv, t0), ell(cx, cy, cz, rx, ry, rz, th1, s0, uv, t1),
                        ell(cx, cy, cz, rx, ry, rz, th1, s1, uv, t1), ell(cx, cy, cz, rx, ry, rz, th0, s1, uv, t0));
            }
        }
        return this;
    }

    private static float[] ell(float cx, float cy, float cz, float rx, float ry, float rz, float th, float s, float[] uv, float tv) {
        float ph = (s - 0.5f) * Mth.TWO_PI;
        float sx = Mth.sin(th) * Mth.sin(ph), sy = -Mth.cos(th), sz = -Mth.sin(th) * Mth.cos(ph);
        return vert(cx + rx * sx, cy + ry * sy, cz + rz * sz, u(uv, s), v(uv, tv), sx / rx, sy / ry, sz / rz);
    }

    /** Punto sobre la superficie de un elipsoide segun la textura (para poner parpados sobre los ojos). */
    public static float[] onEllipsoid(float cx, float cy, float cz, float rx, float ry, float rz, float s, float t, float lat0, float lat1) {
        float th = lat0 + (lat1 - lat0) * t;
        float ph = (s - 0.5f) * Mth.TWO_PI;
        float sx = Mth.sin(th) * Mth.sin(ph), sy = -Mth.cos(th), sz = -Mth.sin(th) * Mth.cos(ph);
        return new float[]{cx + rx * sx, cy + ry * sy, cz + rz * sz, sx / rx, sy / ry, sz / rz};
    }

    /** Disco horizontal (debajo del sombrero): textura polar dentro de la zona. Mira hacia abajo (+y). */
    public Mesh disc(float cx, float cy, float cz, float r, int seg, float[] uv) {
        for (int j = 0; j < seg; j++) {
            float a0 = j * Mth.TWO_PI / seg, a1 = (j + 1) * Mth.TWO_PI / seg;
            float[] c = vert(cx, cy, cz, u(uv, 0.5f), v(uv, 0.5f), 0, 1, 0);
            quad(c, vert(cx + Mth.cos(a0) * r, cy, cz + Mth.sin(a0) * r, u(uv, 0.5f + Mth.cos(a0) * 0.5f), v(uv, 0.5f + Mth.sin(a0) * 0.5f), 0, 1, 0),
                    vert(cx + Mth.cos(a1) * r, cy, cz + Mth.sin(a1) * r, u(uv, 0.5f + Mth.cos(a1) * 0.5f), v(uv, 0.5f + Mth.sin(a1) * 0.5f), 0, 1, 0),
                    c);
        }
        return this;
    }

    /**
     * Superficie de revolucion: perfil de (radio, y) de arriba hacia abajo. Textura: u = vuelta,
     * v = a lo largo del perfil.
     */
    public Mesh lathe(float cx, float cy, float cz, float[][] profile, int seg, float[] uv) {
        int n = profile.length;
        for (int i = 0; i < n - 1; i++) {
            float t0 = i / (float) (n - 1), t1 = (i + 1) / (float) (n - 1);
            float r0 = profile[i][0], y0 = profile[i][1], r1 = profile[i + 1][0], y1 = profile[i + 1][1];
            float dy = y1 - y0, dr = r1 - r0;
            for (int j = 0; j < seg; j++) {
                float s0 = j / (float) seg, s1 = (j + 1) / (float) seg;
                quad(lv(cx, cy, cz, r0, y0, s0, t0, uv, dy, dr), lv(cx, cy, cz, r1, y1, s0, t1, uv, dy, dr),
                        lv(cx, cy, cz, r1, y1, s1, t1, uv, dy, dr), lv(cx, cy, cz, r0, y0, s1, t0, uv, dy, dr));
            }
        }
        return this;
    }

    private static float[] lv(float cx, float cy, float cz, float r, float y, float s, float t, float[] uv, float dy, float dr) {
        float ph = (s - 0.5f) * Mth.TWO_PI;
        float sx = Mth.sin(ph), sz = -Mth.cos(ph);
        // normal del perfil: perpendicular a (dr, dy) hacia afuera
        float nr = dy, ny = -dr;
        return vert(cx + r * sx, cy + y, cz + r * sz, u(uv, s), v(uv, t), sx * nr, ny, sz * nr);
    }

    /**
     * Petalo u hoja curva que sale hacia -z desde la base: largo, ancho maximo, inclinacion hacia
     * arriba (radianes), curvatura (cuanto se enrula la punta) y copa (cuanto suben los bordes).
     * Textura: u = ancho, v = 0 en la base y 1 en la punta.
     */
    public Mesh petal(float start, float length, float width, float tilt, float curl, float cup, int steps, float[] uv) {
        float[][][] g = new float[steps + 1][3][];
        float px = 0, py = 0;
        float[] path = new float[(steps + 1) * 2];
        for (int i = 0; i <= steps; i++) {
            float t = i / (float) steps;
            float ang = tilt + curl * t;
            if (i > 0) {
                px += Mth.cos(ang) * length / steps;
                py += Mth.sin(ang) * length / steps;
            }
            path[i * 2] = start + px;
            path[i * 2 + 1] = py;
        }
        for (int i = 0; i <= steps; i++) {
            float t = i / (float) steps;
            float w = width * 0.5f * Mth.sin(Mth.PI * (0.18f + 0.82f * t)) * (t > 0.85f ? 1.4f - (t - 0.85f) * 2.5f : 1f);
            w = Math.max(0.05f, w);
            for (int k = 0; k < 3; k++) {
                float side = k - 1; // -1, 0, 1
                float up = cup * (1 - (side == 0 ? 0 : 0)) * Math.abs(side) * Mth.sin(Mth.PI * t);
                g[i][k] = new float[]{side * w, -path[i * 2 + 1] - up, -path[i * 2], k * 0.5f, t};
            }
        }
        for (int i = 0; i < steps; i++) {
            for (int k = 0; k < 2; k++) {
                float[] a = g[i][k], b = g[i + 1][k], c = g[i + 1][k + 1], d = g[i][k + 1];
                Vector3f n = new Vector3f(b[0] - a[0], b[1] - a[1], b[2] - a[2]).cross(d[0] - a[0], d[1] - a[1], d[2] - a[2]);
                quad(vert(a[0], a[1], a[2], u(uv, a[3]), v(uv, a[4]), n.x, n.y, n.z), vert(b[0], b[1], b[2], u(uv, b[3]), v(uv, b[4]), n.x, n.y, n.z),
                        vert(c[0], c[1], c[2], u(uv, c[3]), v(uv, c[4]), n.x, n.y, n.z), vert(d[0], d[1], d[2], u(uv, d[3]), v(uv, d[4]), n.x, n.y, n.z));
            }
        }
        return this;
    }

    /** Parche chico mirando hacia afuera (parpado), centrado en p con normal n. */
    public Mesh patch(float[] p, float size, float[] uv) {
        Vector3f n = new Vector3f(p[3], p[4], p[5]).normalize();
        Vector3f up = Math.abs(n.y) > 0.9f ? new Vector3f(0, 0, 1) : new Vector3f(0, 1, 0);
        Vector3f a = new Vector3f(up).cross(n).normalize().mul(size);
        Vector3f b = new Vector3f(n).cross(a).normalize().mul(size * 0.75f);
        float ox = p[0] + n.x * 0.12f, oy = p[1] + n.y * 0.12f, oz = p[2] + n.z * 0.12f;
        quad(vert(ox - a.x - b.x, oy - a.y - b.y, oz - a.z - b.z, u(uv, 0), v(uv, 0), n.x, n.y, n.z),
                vert(ox - a.x + b.x, oy - a.y + b.y, oz - a.z + b.z, u(uv, 0), v(uv, 1), n.x, n.y, n.z),
                vert(ox + a.x + b.x, oy + a.y + b.y, oz + a.z + b.z, u(uv, 1), v(uv, 1), n.x, n.y, n.z),
                vert(ox + a.x - b.x, oy + a.y - b.y, oz + a.z - b.z, u(uv, 1), v(uv, 0), n.x, n.y, n.z));
        return this;
    }

    /** Agrega otra malla girada "angle" en Y y corrida (dx, dy, dz). */
    public Mesh addRotatedY(Mesh other, float angle, float dx, float dy, float dz) {
        float c = Mth.cos(angle), s = Mth.sin(angle);
        for (float[] q : other.quads) {
            float[] r = q.clone();
            for (int i = 0; i < 4; i++) {
                int o = i * 8;
                float x = q[o], z = q[o + 2], nx = q[o + 5], nz = q[o + 7];
                r[o] = x * c + z * s + dx;
                r[o + 1] = q[o + 1] + dy;
                r[o + 2] = -x * s + z * c + dz;
                r[o + 5] = nx * c + nz * s;
                r[o + 7] = -nx * s + nz * c;
            }
            this.quads.add(r);
        }
        return this;
    }

    /** Triangulo con su propia textura (0..1 de la imagen entera): {x, y, z, u, v, nx, ny, nz}. */
    public Mesh triangle(float[] a, float[] b, float[] c) {
        float[] q = new float[32];
        float[][] vs = {a, b, c, c};
        for (int i = 0; i < 4; i++) {
            System.arraycopy(vs[i], 0, q, i * 8, 8);
        }
        this.quads.add(q);
        return this;
    }

    /** Pieza del juego que dibuja esta malla. */
    public ModelPart.Cube cube() {
        return new MeshCube(this.quads);
    }

    /** "Cubo" del juego que en vez de una caja dibuja la malla. */
    static final class MeshCube extends ModelPart.Cube {
        private final float[][] quads;

        MeshCube(List<float[]> quads) {
            super(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, false, 16, 16, Set.of(Direction.UP));
            this.quads = quads.toArray(new float[0][]);
        }

        @Override
        public void compile(PoseStack.Pose pose, VertexConsumer vc, int light, int overlay, int color) {
            for (float[] q : this.quads) {
                for (int i = 0; i < 4; i++) {
                    int o = i * 8;
                    vc.addVertex(pose, q[o] / 16f, q[o + 1] / 16f, q[o + 2] / 16f).setColor(color).setUv(q[o + 3], q[o + 4])
                            .setOverlay(overlay).setLight(light).setNormal(pose, q[o + 5], q[o + 6], q[o + 7]);
                }
            }
        }
    }
}
