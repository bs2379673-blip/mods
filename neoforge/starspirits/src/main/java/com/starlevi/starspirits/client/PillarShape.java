package com.starlevi.starspirits.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Forma del Pilar Runico (sin nada de Minecraft, para poder probarla sola): un menhir tallado a mano,
 * un poco aplanado y torcido, con la punta rota en diagonal, piedras con musgo alrededor de la base y
 * las runas pegadas a la superficie. Cada cara tiene su normal propia, asi se ve cincelada.
 * <p>
 * Textura: atlas de 256x128 — mitad izquierda piedra, mitad derecha la misma piedra con musgo.
 */
public final class PillarShape {
    /** Datos de cada quad: 4 vertices de x, y, z, u, v, nx, ny, nz. */
    public static final int STRIDE = 32;
    public static final float BOTTOM = -0.35f, HEIGHT = 6.9f;
    static final float EX = 1.14f, EZ = 0.80f;
    static final int SIDES = 20, RINGS = 18;
    /** Ondas de la superficie (enteras en el angulo, asi cierra la vuelta). */
    private static final float[][] WAVES;
    /** Toda la piedra: menhir, punta rota y piedras de la base. */
    public static final float[] STONE;
    /** Trazos de la runa grande (x en la cara, y en bloques). */
    public static final float[][] RUNE = {
            {0, 2.2f, 0, 4.3f}, {0, 4.3f, -0.34f, 4.75f}, {-0.34f, 4.75f, 0, 5.2f}, {0, 5.2f, 0.34f, 4.75f}, {0.34f, 4.75f, 0, 4.3f},
            {0, 3.4f, -0.5f, 4.0f}, {0, 3.4f, 0.5f, 4.0f}, {-0.5f, 4.0f, -0.56f, 3.0f}, {0.5f, 4.0f, 0.56f, 3.0f},
            {0, 2.2f, -0.45f, 1.7f}, {0, 2.2f, 0.45f, 1.7f}, {-0.22f, 2.85f, 0.22f, 2.85f}};
    /** Glifos chicos de la franja de abajo (en unidades de un glifo de 1x1). */
    public static final float[][][] GLYPHS = {
            {{0, 0, 0, 1}, {0, 1, 0.6f, 0.6f}, {0, 0.5f, 0.6f, 0.2f}},
            {{0, 0, 0.3f, 1}, {0.3f, 1, 0.6f, 0}, {0.1f, 0.4f, 0.5f, 0.4f}},
            {{0.3f, 0, 0.3f, 1}, {0, 0.7f, 0.6f, 0.3f}},
            {{0, 0, 0, 1}, {0.6f, 0, 0.6f, 1}, {0, 0.5f, 0.6f, 0.5f}},
            {{0, 1, 0.6f, 0}, {0, 0, 0.6f, 1}},
            {{0.3f, 0, 0.3f, 1}, {0, 1, 0.3f, 0.7f}, {0.6f, 1, 0.3f, 0.7f}, {0, 0.3f, 0.3f, 0}, {0.6f, 0.3f, 0.3f, 0}},
            {{0, 0, 0, 1}, {0, 1, 0.6f, 0.75f}, {0.6f, 0.75f, 0, 0.5f}, {0, 0.5f, 0.6f, 0}},
            {{0, 0.5f, 0.3f, 1}, {0.3f, 1, 0.6f, 0.5f}, {0.6f, 0.5f, 0.3f, 0}, {0.3f, 0, 0, 0.5f}}};
    /** Altura de la franja de glifos y cuantos hay alrededor. */
    public static final float BAND_Y = 0.95f;
    public static final int BAND_N = 10;

    static {
        Random r = new Random(77);
        WAVES = new float[9][];
        for (int i = 0; i < WAVES.length; i++) {
            int k = 2 + r.nextInt(6);
            WAVES[i] = new float[]{k, r.nextFloat() * 6.28f, 0.6f + r.nextFloat() * 1.6f, (0.025f + r.nextFloat() * 0.03f) * (k < 4 ? 1.6f : 1)};
        }
        List<float[]> q = new ArrayList<>();
        menhir(q);
        rocks(q, r);
        STONE = new float[q.size() * STRIDE];
        for (int i = 0; i < q.size(); i++) {
            System.arraycopy(q.get(i), 0, STONE, i * STRIDE, STRIDE);
        }
    }

    private PillarShape() {
    }

    /** Ancho del menhir segun la altura (base ancha, cintura, se afina arriba). */
    static float profile(float y) {
        float[] ys = {BOTTOM, 0.3f, 1.2f, 2.6f, 4.0f, 5.3f, 6.3f, 7.2f};
        float[] rs = {1.32f, 1.22f, 1.08f, 1.0f, 0.98f, 0.9f, 0.76f, 0.62f};
        for (int i = 0; i < ys.length - 1; i++) {
            if (y <= ys[i + 1]) {
                float k = (y - ys[i]) / (ys[i + 1] - ys[i]);
                k = k * k * (3 - 2 * k);
                return rs[i] + (rs[i + 1] - rs[i]) * Math.max(0, k);
            }
        }
        return rs[rs.length - 1];
    }

    /** Altura de la rotura de arriba en ese angulo: cae en diagonal, con dientes. */
    static float top(float a) {
        return HEIGHT + 0.42f * (float) Math.sin(a + 0.9f) + 0.16f * (float) Math.sin(3 * a + 2.1f) + 0.08f * (float) Math.sin(7 * a + 0.4f);
    }

    /** Punto de la superficie en el angulo a (0 = frente, +z) y la altura y. */
    static float[] surface(float a, float y, float out) {
        float rad = profile(y);
        for (float[] w : WAVES) {
            rad += w[3] * (float) Math.sin(w[0] * a + w[1] + y * w[2]);
        }
        // en el frente y atras es mas liso (ahi va la runa)
        rad += out;
        float twist = 0.18f * y / HEIGHT;
        float lean = 0.24f * (y / HEIGHT) * (y / HEIGHT);
        float x = (float) Math.sin(a) * rad * EX, z = (float) Math.cos(a) * rad * EZ;
        float c = (float) Math.cos(twist), s = (float) Math.sin(twist);
        return new float[]{x * c + z * s + lean, y, -x * s + z * c};
    }

    /** Punto de la cara del frente (side 0) o de atras (side 1) a una distancia x del centro de la cara. */
    public static float[] onFace(int side, float x, float y, float out) {
        float a = 0;
        float base = side == 0 ? 0 : (float) Math.PI;
        // busca el angulo que cae en esa x (dos pasadas alcanzan)
        for (int i = 0; i < 3; i++) {
            float rad = profile(y) * EX;
            a = (float) Math.asin(Math.max(-0.95f, Math.min(0.95f, x / rad)));
        }
        return surface(base + (side == 0 ? a : -a), y, out);
    }

    /** Punto de la superficie en un angulo cualquiera (para los glifos de la franja). */
    public static float[] at(float a, float y, float out) {
        return surface(a, y, out);
    }

    private static void menhir(List<float[]> q) {
        float[][][] v = new float[RINGS + 1][SIDES + 1][];
        for (int i = 0; i <= RINGS; i++) {
            for (int j = 0; j <= SIDES; j++) {
                float a = (j % SIDES) * (float) Math.PI * 2 / SIDES;
                float k = i / (float) RINGS;
                // las filas se aprietan un poco abajo; la ultima sigue la rotura
                float y = BOTTOM + (top(a) - BOTTOM) * (float) Math.pow(k, 1.08);
                float jitter = (i == 0 || i == RINGS) ? 0 : 0.05f * (float) Math.sin(j * 12.9898f + i * 78.233f);
                v[i][j] = surface(a, y + jitter, 0);
            }
        }
        for (int i = 0; i < RINGS; i++) {
            for (int j = 0; j < SIDES; j++) {
                float u0 = j / (float) SIDES, u1 = (j + 1) / (float) SIDES;
                float[] a = v[i][j], b = v[i][j + 1], c = v[i + 1][j + 1], d = v[i + 1][j];
                float cy = (a[1] + c[1]) * 0.5f;
                float ang = (j + 0.5f) * (float) Math.PI * 2 / SIDES;
                // musgo abajo (con borde irregular) y en la cara de atras un poco mas arriba
                float line = 0.55f + 0.45f * (float) Math.sin(ang * 3 + 1) + 0.6f * (float) Math.max(0, -Math.cos(ang));
                boolean moss = cy < line;
                quad(q, a, d, c, b, u0, u1, moss, true);
            }
        }
        // la rotura de arriba: dos anillos que se cierran hacia adentro, con la superficie quebrada
        float[][] in1 = new float[SIDES + 1][], in2 = new float[SIDES + 1][];
        float sum = 0;
        for (int j = 0; j <= SIDES; j++) {
            float a = (j % SIDES) * (float) Math.PI * 2 / SIDES;
            float[] o = v[RINGS][j];
            float[] c = surface(0, top(0), 0);
            float cx = 0.16f, cz = 0;
            float n1 = 0.09f * (float) Math.sin(j * 4.1f), n2 = 0.07f * (float) Math.sin(j * 2.7f + 1);
            in1[j] = new float[]{cx + (o[0] - cx) * 0.66f, o[1] + 0.06f + n1, cz + (o[2] - cz) * 0.66f};
            in2[j] = new float[]{cx + (o[0] - cx) * 0.3f, o[1] + 0.02f + n2, cz + (o[2] - cz) * 0.3f};
            if (j < SIDES) {
                sum += o[1];
            }
        }
        float[] mid = {0.16f, sum / SIDES + 0.05f, 0};
        for (int j = 0; j < SIDES; j++) {
            capQuad(q, v[RINGS][j], in1[j], in1[j + 1], v[RINGS][j + 1]);
            capQuad(q, in1[j], in2[j], in2[j + 1], in1[j + 1]);
            capQuad(q, in2[j], mid, mid, in2[j + 1]);
        }
    }

    /** Piedras alrededor de la base, hundidas y con musgo arriba. */
    private static void rocks(List<float[]> q, Random r) {
        int n = 9;
        for (int i = 0; i < n; i++) {
            float a = i * (float) Math.PI * 2 / n + (r.nextFloat() - 0.5f) * 0.5f;
            float dist = 1.45f + r.nextFloat() * 0.75f;
            float size = 0.28f + r.nextFloat() * 0.38f;
            if (i % 3 == 0) {
                size *= 0.6f;
                dist += 0.5f;
            }
            float cx = (float) Math.sin(a) * dist * EX, cz = (float) Math.cos(a) * dist * EZ;
            rock(q, r, cx, -0.12f * size / 0.5f, cz, size, r.nextFloat() * 6.28f, i);
        }
        // una losa plana delante, como si fuera un escalon de ofrendas
        rock(q, r, 0.1f, -0.3f, 1.95f, 0.75f, 0.3f, 99);
    }

    private static void rock(List<float[]> q, Random r, float cx, float cy, float cz, float size, float rot, int seed) {
        int lon = 8, lat = 5;
        float flat = seed == 99 ? 0.32f : 0.6f + r.nextFloat() * 0.2f;
        float sx = size * (seed == 99 ? 1.25f : 1f + r.nextFloat() * 0.3f), sz = size * (seed == 99 ? 0.8f : 1);
        float[][][] v = new float[lat + 1][lon + 1][];
        float[] bumps = new float[lat * lon + lon + 1];
        for (int i = 0; i < bumps.length; i++) {
            bumps[i] = 1 + (r.nextFloat() - 0.5f) * 0.3f;
        }
        for (int i = 0; i <= lat; i++) {
            float phi = (float) Math.PI * (i / (float) lat - 0.5f);
            for (int j = 0; j <= lon; j++) {
                float th = (j % lon) * (float) Math.PI * 2 / lon + rot;
                float b = (i == 0 || i == lat) ? 1 : bumps[i * lon + (j % lon)];
                float x = (float) (Math.cos(phi) * Math.sin(th)) * sx * b;
                float y = (float) Math.sin(phi) * size * flat * b;
                float z = (float) (Math.cos(phi) * Math.cos(th)) * sz * b;
                // achatada abajo (apoyada)
                if (y < 0) {
                    y *= 0.4f;
                }
                v[i][j] = new float[]{cx + x, cy + y + size * flat * 0.25f, cz + z};
            }
        }
        float u0 = (seed % 4) * 0.25f, v0 = (seed % 3) * 0.3f;
        for (int i = 0; i < lat; i++) {
            for (int j = 0; j < lon; j++) {
                float[] a = v[i][j], b = v[i][j + 1], c = v[i + 1][j + 1], d = v[i + 1][j];
                float ua = u0 + j / (float) lon * 0.25f, ub = u0 + (j + 1) / (float) lon * 0.25f;
                float va = v0 + (1 - i / (float) lat) * 0.3f, vb = v0 + (1 - (i + 1) / (float) lat) * 0.3f;
                boolean moss = i >= lat / 2;
                emit(q, a, d, c, b, new float[]{ua, va, ua, vb, ub, vb, ub, va}, moss);
            }
        }
    }

    private static void quad(List<float[]> q, float[] a, float[] d, float[] c, float[] b, float u0, float u1, boolean moss, boolean side) {
        float va = vOf(a[1]), vb = vOf(b[1]), vc = vOf(c[1]), vd = vOf(d[1]);
        emit(q, a, d, c, b, new float[]{u0, va, u0, vd, u1, vc, u1, vb}, moss);
    }

    /** La rotura de arriba: textura proyectada desde arriba, con musgo en las partes chatas. */
    private static void capQuad(List<float[]> q, float[] a, float[] b, float[] c, float[] d) {
        float[] uv = new float[8];
        float[][] p = {a, b, c, d};
        for (int i = 0; i < 4; i++) {
            uv[i * 2] = 0.5f + p[i][0] * 0.16f;
            uv[i * 2 + 1] = 0.1f + (p[i][2] + 1) * 0.12f;
        }
        float[] n = normal(a, b, c, d);
        emit(q, a, b, c, d, uv, n[1] > 0.8f);
    }

    private static float vOf(float y) {
        return 1 - (y - BOTTOM) / 8f;
    }

    /** Arma el quad; el musgo usa la mitad derecha del atlas. Normal plana (se ve tallada). */
    private static void emit(List<float[]> q, float[] a, float[] b, float[] c, float[] d, float[] uv, boolean moss) {
        float[] n = normal(a, b, c, d);
        float[] out = new float[STRIDE];
        float[][] p = {a, b, c, d};
        for (int i = 0; i < 4; i++) {
            int o = i * 8;
            out[o] = p[i][0];
            out[o + 1] = p[i][1];
            out[o + 2] = p[i][2];
            out[o + 3] = Math.min(0.999f, Math.max(0.001f, uv[i * 2])) * 0.5f + (moss ? 0.5f : 0);
            out[o + 4] = Math.min(0.999f, Math.max(0.001f, uv[i * 2 + 1]));
            out[o + 5] = n[0];
            out[o + 6] = n[1];
            out[o + 7] = n[2];
        }
        q.add(out);
    }

    static float[] normal(float[] a, float[] b, float[] c, float[] d) {
        float ux = c[0] - a[0], uy = c[1] - a[1], uz = c[2] - a[2];
        float vx = d[0] - b[0], vy = d[1] - b[1], vz = d[2] - b[2];
        float nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
        float l = (float) Math.sqrt(nx * nx + ny * ny + nz * nz) + 1e-6f;
        return new float[]{-nx / l, -ny / l, -nz / l};
    }

    /**
     * Trazos de las runas pegados a la piedra: la grande adelante y atras, y la franja de glifos de abajo.
     * Devuelve quads de 4 puntos (x, y, z) seguidos. {@code band} elige la franja en vez de la runa grande.
     */
    public static float[] strokes(float width, float out, boolean band) {
        List<float[]> segs = new ArrayList<>();
        if (!band) {
            for (int side = 0; side < 2; side++) {
                for (float[] s : RUNE) {
                    segs.add(new float[]{side, s[0] * 0.62f, s[1], s[2] * 0.62f, s[3]});
                }
            }
        } else {
            for (int i = 0; i < BAND_N; i++) {
                float a = (i + 0.5f) * (float) Math.PI * 2 / BAND_N;
                float[][] g = GLYPHS[(i * 5 + 3) % GLYPHS.length];
                for (float[] s : g) {
                    // glifo de 0.3 x 0.42 bloques centrado en el angulo a
                    segs.add(new float[]{2, a + (s[0] - 0.3f) * 0.3f / 1.15f, BAND_Y + s[1] * 0.42f, a + (s[2] - 0.3f) * 0.3f / 1.15f,
                            BAND_Y + s[3] * 0.42f});
                }
            }
        }
        List<float[]> q = new ArrayList<>();
        for (float[] s : segs) {
            int side = (int) s[0];
            float len = (float) Math.hypot(s[3] - s[1], s[4] - s[2]);
            int n = Math.max(1, (int) Math.ceil(len / 0.2f));
            for (int k = 0; k < n; k++) {
                float t0 = k / (float) n, t1 = (k + 1) / (float) n;
                float x0 = s[1] + (s[3] - s[1]) * t0, y0 = s[2] + (s[4] - s[2]) * t0;
                float x1 = s[1] + (s[3] - s[1]) * t1, y1 = s[2] + (s[4] - s[2]) * t1;
                float[] p0 = side == 2 ? surface(x0, y0, out) : onFace(side, x0, y0, out);
                float[] p1 = side == 2 ? surface(x1, y1, out) : onFace(side, x1, y1, out);
                // ancho: perpendicular al trazo, sobre la superficie (normal aproximada = radial)
                float dx = p1[0] - p0[0], dy = p1[1] - p0[1], dz = p1[2] - p0[2];
                float rx = (p0[0] + p1[0]) * 0.5f, rz = (p0[2] + p1[2]) * 0.5f;
                float rl = (float) Math.sqrt(rx * rx + rz * rz) + 1e-5f;
                rx /= rl;
                rz /= rl;
                // w = trazo x radial
                float wx = dy * rz, wy = dz * rx - dx * rz, wz = -dy * rx;
                float wl = (float) Math.sqrt(wx * wx + wy * wy + wz * wz) + 1e-6f;
                float h = width * 0.5f;
                wx = wx / wl * h;
                wy = wy / wl * h;
                wz = wz / wl * h;
                // alarga un poquito cada tramo para que no queden huecos en las uniones
                float ex = dx * 0.08f, ey = dy * 0.08f, ez = dz * 0.08f;
                q.add(new float[]{p0[0] - wx - ex, p0[1] - wy - ey, p0[2] - wz - ez, p1[0] - wx + ex, p1[1] - wy + ey, p1[2] - wz + ez,
                        p1[0] + wx + ex, p1[1] + wy + ey, p1[2] + wz + ez, p0[0] + wx - ex, p0[1] + wy - ey, p0[2] + wz - ez});
            }
        }
        float[] r = new float[q.size() * 12];
        for (int i = 0; i < q.size(); i++) {
            System.arraycopy(q.get(i), 0, r, i * 12, 12);
        }
        return r;
    }

    /** Distancia de la cara del frente a esa altura (para acercar al espiritu del ritual). */
    public static float frontZ(float y) {
        return surface(0, y, 0)[2];
    }
}
