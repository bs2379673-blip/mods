package com.tunombre.concierto.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.ShaderInstance;

/**
 * Efectos extra del escenario (todo con el shader aditivo, sin imagenes):
 * helices de luz que giran, anillos que suben, luces que corren por el borde del piso, pilares en las esquinas y confeti.
 */
public final class StageFx {
    private StageFx() { }

    private static void v(BufferBuilder bb, double x, double y, double z, float[] c, float a, float u, float w) {
        bb.vertex(x, y, z).color(c[0], c[1], c[2], a).uv(u, w).endVertex();
    }

    private static void strip(BufferBuilder bb, double ox, double oy, double oz, double ex, double ey, double ez, double w0, double w1, float[] c, float a0, float a1) {
        double dx = ex - ox, dy = ey - oy, dz = ez - oz;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-4) return;
        dx /= len; dy /= len; dz /= len;
        double mx = (ox + ex) * 0.5, my = (oy + ey) * 0.5, mz = (oz + ez) * 0.5;
        double vx = -mx, vy = -my, vz = -mz;
        double sx = dy * vz - dz * vy, sy = dz * vx - dx * vz, sz = dx * vy - dy * vx;
        double l = Math.sqrt(sx * sx + sy * sy + sz * sz);
        if (l < 1e-6) return;
        sx /= l; sy /= l; sz /= l;
        v(bb, ox - sx * w0, oy - sy * w0, oz - sz * w0, c, a0, -1F, 0F);
        v(bb, ox + sx * w0, oy + sy * w0, oz + sz * w0, c, a0, 1F, 0F);
        v(bb, ex + sx * w1, ey + sy * w1, ez + sz * w1, c, a1, 1F, 1F);
        v(bb, ex - sx * w1, ey - sy * w1, ez - sz * w1, c, a1, -1F, 1F);
    }

    private static void glow(BufferBuilder bb, double hx, double hy, double hz, float[] c, double half, float a) {
        double dist = Math.sqrt(hx * hx + hy * hy + hz * hz);
        if (dist < 1e-3) return;
        double nx = -hx / dist, ny = -hy / dist, nz = -hz / dist;
        double rxv = -nz, rzv = nx;
        double rl = Math.sqrt(rxv * rxv + rzv * rzv);
        if (rl < 1e-6) return;
        rxv /= rl; rzv /= rl;
        double uxv = ny * rzv, uyv = nz * rxv - nx * rzv, uzv = -ny * rxv;
        v(bb, hx - rxv * half - uxv * half, hy - uyv * half, hz - rzv * half - uzv * half, c, a, -1F, -1F);
        v(bb, hx + rxv * half - uxv * half, hy - uyv * half, hz + rzv * half - uzv * half, c, a, 1F, -1F);
        v(bb, hx + rxv * half + uxv * half, hy + uyv * half, hz + rzv * half + uzv * half, c, a, 1F, 1F);
        v(bb, hx - rxv * half + uxv * half, hy + uyv * half, hz - rzv * half + uzv * half, c, a, -1F, 1F);
    }

    private static float hash(float n) { float s = (float) Math.sin(n * 127.1F) * 43758.5453F; return s - (float) Math.floor(s); }

    private static void set(ShaderInstance sh, String n, float v) { sh.safeGetUniform(n).set(v); }

    private static void begin(BufferBuilder bb) { bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_TEX); }

    private static final float[] WHITE = {1F, 1F, 1F}, GOLD = {1F, 0.82F, 0.3F};

    /** posicion en el perimetro del cuadrado: s en [0,4) -> (derecha, adelante) en [-1,1] */
    private static double[] perim(double s) {
        s = ((s % 4.0) + 4.0) % 4.0;
        int side = (int) s;
        double t = s - side;
        return switch (side) {
            case 0 -> new double[]{-1 + 2 * t, -1};
            case 1 -> new double[]{1, -1 + 2 * t};
            case 2 -> new double[]{1 - 2 * t, 1};
            default -> new double[]{-1, 1 - 2 * t};
        };
    }

    /** gain = intensidad general (se apaga cuando el look esta apagado) */
    public static void draw(ShaderInstance sh, double camX, double camY, double camZ, Stage stage, float songT, float level, float kick, float[][] pal, float gain) {
        if (!stage.set || gain < 0.02F) return;
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        double u = stage.scale;
        double HS = stage.p[2] * u * 1.05;
        double cx = stage.x - camX, cy = stage.y - camY, cz = stage.z - camZ;
        double rx = stage.rx(), rz = stage.rz(), fx = stage.fx(), fz = stage.fz();
        float act = Math.min(1F, 0.35F + level * 1.1F);
        float g = gain * act;
        float[][] cols = {pal[0], pal[1], pal[2], GOLD, WHITE};

        set(sh, "Alpha", 1F);
        set(sh, "Prog", songT);

        // ---- helices de luz que giran alrededor del escenario
        set(sh, "Kind", 7F);
        begin(bb);
        int strands = 3, segs = 36;
        double R = 1.0 * HS, H = 14.0 * u;
        double spin = songT * (0.22 + 0.25 * level);
        for (int s = 0; s < strands; s++) {
            float[] c = cols[s % 3];
            double ph = spin + s * Math.PI * 2 / strands;
            double px = 0, py = 0, pz = 0;
            for (int i = 0; i <= segs; i++) {
                double h = (double) i / segs;
                double ang = ph + h * Math.PI * 7.0;
                double rad = R * (0.92 + 0.08 * Math.sin(h * Math.PI * 2.0)) * (1.0 + 0.04 * kick);
                double wx = cx + Math.cos(ang) * rad, wz = cz + Math.sin(ang) * rad, wy = cy + 0.3 + h * H;
                if (i > 0) {
                    float a = g * 0.55F * (1F - (float) h * 0.75F);
                    strip(bb, px, py, pz, wx, wy, wz, 0.22 * u, 0.22 * u, c, a, a);
                }
                px = wx; py = wy; pz = wz;
            }
        }
        BufferUploader.drawWithShader(bb.end());

        // ---- pilares de luz en las cuatro esquinas del piso
        begin(bb);
        for (int i = 0; i < 4; i++) {
            double sx = (i & 1) == 0 ? -1 : 1, sf = (i & 2) == 0 ? -1 : 1;
            double wx = cx + rx * sx * HS + fx * sf * HS, wz = cz + rz * sx * HS + fz * sf * HS;
            float[] c = cols[i % 3];
            float a = g * (0.35F + 0.55F * kick);
            strip(bb, wx, cy, wz, wx, cy + 48.0, wz, 0.28 * u, 0.5 * u, c, a, a * 0.05F);
        }
        BufferUploader.drawWithShader(bb.end());

        // ---- luces que corren por el borde del piso (con cola)
        set(sh, "Kind", 6F);
        begin(bb);
        int chasers = 28;
        double spd = 0.10 + 0.18 * level;
        for (int i = 0; i < chasers; i++) {
            float[] c = cols[i % 5];
            double s0 = i * 4.0 / chasers + songT * spd * (((i & 1) == 0) ? 1 : -1);
            for (int tl = 0; tl < 4; tl++) {
                double[] p = perim(s0 - tl * 0.012 * (((i & 1) == 0) ? 1 : -1));
                double wx = cx + rx * p[0] * HS + fx * p[1] * HS, wz = cz + rz * p[0] * HS + fz * p[1] * HS;
                glow(bb, wx, cy + 0.35, wz, c, (0.55 - tl * 0.1) * u * 0.5 + 0.3, g * (0.9F - tl * 0.2F));
            }
        }
        BufferUploader.drawWithShader(bb.end());

        // ---- anillos de luz que suben desde el escenario y se abren
        set(sh, "Kind", 8F);
        begin(bb);
        for (int i = 0; i < 5; i++) {
            float ph = (songT * 0.07F + i / 5F) % 1F;
            double h = ph * 22.0 * u;
            double r = HS * (1.05 + 0.55 * ph + 0.02 * kick);
            float[] c = cols[i % 3];
            float a = g * (1F - ph) * 0.85F;
            double y = cy + 0.5 + h;
            v(bb, cx - r, y, cz - r, c, a, -1F, -1F);
            v(bb, cx + r, y, cz - r, c, a, 1F, -1F);
            v(bb, cx + r, y, cz + r, c, a, 1F, 1F);
            v(bb, cx - r, y, cz + r, c, a, -1F, 1F);
        }
        BufferUploader.drawWithShader(bb.end());

        // ---- confeti que cae sobre el piso (mas cuando la musica sube)
        if (level > 0.12F || kick > 0.3F) {
            set(sh, "Kind", 6F);
            begin(bb);
            int n = 110;
            float ca = g * Math.min(1F, 0.3F + level + kick * 0.5F);
            for (int i = 0; i < n; i++) {
                float seed = hash(i * 1.37F);
                float fall = (songT * (0.10F + 0.06F * hash(i * 2.9F)) + seed) % 1F;
                double hh = (1F - fall) * 34.0 * u * 0.5;
                double lx = (hash(i * 4.1F) * 2 - 1) * HS * 0.95 + Math.sin(songT * (0.6 + seed) + i) * 0.8;
                double lf = (hash(i * 7.7F) * 2 - 1) * HS * 0.95 + Math.cos(songT * (0.5 + seed) + i * 1.3) * 0.8;
                double wx = cx + rx * lx + fx * lf, wz = cz + rz * lx + fz * lf;
                float tw = 0.5F + 0.5F * (float) Math.sin(songT * 6F + i * 2.1F);
                glow(bb, wx, cy + 0.2 + hh, wz, cols[i % 5], 0.22 + 0.12 * seed, ca * (0.35F + 0.65F * tw));
            }
            BufferUploader.drawWithShader(bb.end());
        }
    }
}
