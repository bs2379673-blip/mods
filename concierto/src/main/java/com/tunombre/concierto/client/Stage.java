package com.tunombre.concierto.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** escenario en el mundo: piso de cuadricula, paneles ecualizador, rayos de luz, anillo y columnas de luz. Todo es geometria real (tapada por los bloques). */
public final class Stage {
    public boolean set;
    public double x, y, z;
    public float yaw, scale = 4F;
    public final float[] p = {-1F, 13F, 11F, 12F, -2F, 10F, 5F, 25F, 9F, -4F, 38F, 10F};

    /** columna de luz que cae del cielo */
    public static final class Column {
        public final Vec3 pos;
        public final float[] rgb;
        public final float size, secs;
        public float age;
        public boolean hit;
        public Column(Vec3 pos, int rgb, float size, float secs) {
            this.pos = pos;
            this.rgb = new float[]{((rgb >> 16) & 255) / 255F, ((rgb >> 8) & 255) / 255F, (rgb & 255) / 255F};
            this.size = size;
            this.secs = secs;
        }
    }

    public static final float FALL_TIME = 0.9F;
    public final List<Column> columns = new ArrayList<>();
    private float[] hts;
    private int hR, hcx, hcz;
    private long hTime;

    /** direccion en que mira el publico (hacia donde se van los rayos) y su derecha */
    public double fx() { return -Math.sin(Math.toRadians(yaw)); }
    public double fz() { return Math.cos(Math.toRadians(yaw)); }
    public double rx() { return Math.cos(Math.toRadians(yaw)); }
    public double rz() { return Math.sin(Math.toRadians(yaw)); }

    private static final class V {
        final BufferBuilder bb;
        V(BufferBuilder bb) { this.bb = bb; }
        void v(double x, double y, double z, float r, float g, float b, float a, float u, float v) {
            bb.vertex(x, y, z).color(r, g, b, a).uv(u, v).endVertex();
        }
    }

    private static void begin(BufferBuilder bb) { bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_TEX); }

    private static void flush(BufferBuilder bb) { BufferUploader.drawWithShader(bb.end()); }

    private static void set(ShaderInstance sh, String n, float v) { sh.safeGetUniform(n).set(v); }

    /** una tira orientada hacia la camara (para rayos y columnas). o = origen, d = direccion unitaria, coordenadas ya relativas a la camara */
    private static void strip(V b, double ox, double oy, double oz, double dx, double dy, double dz, double len, double w0, double w1, float[] c, float a) {
        double mx = ox + dx * len * 0.5, my = oy + dy * len * 0.5, mz = oz + dz * len * 0.5;
        // vector hacia la camara (que esta en el origen)
        double vx = -mx, vy = -my, vz = -mz;
        double sx = dy * vz - dz * vy, sy = dz * vx - dx * vz, sz = dx * vy - dy * vx;
        double l = Math.sqrt(sx * sx + sy * sy + sz * sz);
        if (l < 1e-6) return;
        sx /= l; sy /= l; sz /= l;
        double ex = ox + dx * len, ey = oy + dy * len, ez = oz + dz * len;
        b.v(ox - sx * w0, oy - sy * w0, oz - sz * w0, c[0], c[1], c[2], a, -1F, 0F);
        b.v(ox + sx * w0, oy + sy * w0, oz + sz * w0, c[0], c[1], c[2], a, 1F, 0F);
        b.v(ex + sx * w1, ey + sy * w1, ez + sz * w1, c[0], c[1], c[2], a, 1F, 1F);
        b.v(ex - sx * w1, ey - sy * w1, ez - sz * w1, c[0], c[1], c[2], a, -1F, 1F);
    }

    private static float hash(float n) { float s = (float) Math.sin(n * 127.1F) * 43758.5453F; return s - (float) Math.floor(s); }

    /** floorA, eqA, beamsA = cuanto se ve cada parte (0..1, con fundido) */
    public void draw(ShaderInstance sh, double camX, double camY, double camZ, boolean ring,
                     float floorA, float eqA, float ringA) {
        if (!set) return;
        float S = scale;
        double fx = fx(), fz = fz(), rx = rx(), rz = rz();
        double ox = x - camX, oy = y - camY + 0.02, oz = z - camZ;
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        V b = new V(bb);

        if (floorA > 0.003F) {
            set(sh, "Kind", 0F);
            set(sh, "Alpha", floorA);
            // piso redondo centrado en el escenario; radio = piso_ancho * escala * 0.75. Cada bloque va a la altura del suelo para que el terreno no lo tape.
            float R = p[2] * S * 0.75F;
            var lv = net.minecraft.client.Minecraft.getInstance().level;
            int cx0 = (int) Math.floor(x), cz0 = (int) Math.floor(z);
            int Ri = (int) Math.ceil(R) + 1;
            long now = System.currentTimeMillis();
            if (lv != null && (hts == null || hR != Ri || hcx != cx0 || hcz != cz0 || now - hTime > 1500)) {
                int n = 2 * Ri + 1;
                hts = new float[n * n];
                for (int i = 0; i < n; i++) {
                    for (int j = 0; j < n; j++) {
                        int wx = cx0 - Ri + i, wz = cz0 - Ri + j;
                        hts[i * n + j] = lv.hasChunk(wx >> 4, wz >> 4)
                                ? lv.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, wx, wz) : Float.NaN;
                    }
                }
                hR = Ri; hcx = cx0; hcz = cz0; hTime = now;
            }
            if (hts != null) {
                int n = 2 * Ri + 1;
                begin(bb);
                for (int i = 0; i < n; i++) {
                    for (int j = 0; j < n; j++) {
                        float h = hts[i * n + j];
                        if (Float.isNaN(h) || Math.abs(h - y) > 4.5) continue;
                        int wx = cx0 - Ri + i, wz = cz0 - Ri + j;
                        double dx = wx + 0.5 - x, dz = wz + 0.5 - z;
                        double lx = rx * dx + rz * dz, lf = fx * dx + fz * dz;
                        if (Math.sqrt(lx * lx + lf * lf) > R) continue;
                        double yy = h + 0.03 - camY;
                        for (int k = 0; k < 4; k++) {
                            double qx = wx + ((k == 1 || k == 2) ? 1 : 0), qz = wz + ((k >= 2) ? 1 : 0);
                            double ddx = qx - x, ddz = qz - z;
                            double ql = rx * ddx + rz * ddz, qf = fx * ddx + fz * ddz;
                            b.v(qx - camX, yy, qz - camZ, (float) (ql / R), (float) (qf / R), 0F, 1F, (float) ql + 0.5F, (float) qf + 0.5F);
                        }
                    }
                }
                flush(bb);
            }
        }
        if (eqA > 0.003F) {
            set(sh, "Kind", 1F);
            set(sh, "Alpha", eqA);
            float pw = p[5] * S, ph = p[6] * S;
            for (int sgn = -1; sgn <= 1; sgn += 2) {
                double cx = sgn * p[3] * S, cz = p[4] * S;
                double yaw2 = Math.toRadians(p[7]) * -sgn;
                // eje del panel en el plano local (lateral, adelante)
                double tl = Math.cos(yaw2), tf = Math.sin(yaw2);
                double ax = cx - tl * pw / 2, az = cz - tf * pw / 2, bx = cx + tl * pw / 2, bz = cz + tf * pw / 2;
                double y0 = 0.5 * S;
                begin(bb);
                b.v(ox + rx * ax + fx * az, oy + y0, oz + rz * ax + fz * az, 1F, 1F, 1F, 1F, 0F, 0F);
                b.v(ox + rx * bx + fx * bz, oy + y0, oz + rz * bx + fz * bz, 1F, 1F, 1F, 1F, 1F, 0F);
                b.v(ox + rx * bx + fx * bz, oy + y0 + ph, oz + rz * bx + fz * bz, 1F, 1F, 1F, 1F, 1F, 1F);
                b.v(ox + rx * ax + fx * az, oy + y0 + ph, oz + rz * ax + fz * az, 1F, 1F, 1F, 1F, 0F, 1F);
                flush(bb);
            }
        }
        if (ring && ringA > 0.003F) {
            set(sh, "Kind", 3F);
            set(sh, "Alpha", ringA);
            float R = 3.2F * S;
            begin(bb);
            b.v(ox - R, oy + 0.04, oz - R, 1F, 1F, 1F, 1F, -1F, -1F);
            b.v(ox + R, oy + 0.04, oz - R, 1F, 1F, 1F, 1F, 1F, -1F);
            b.v(ox + R, oy + 0.04, oz + R, 1F, 1F, 1F, 1F, 1F, 1F);
            b.v(ox - R, oy + 0.04, oz + R, 1F, 1F, 1F, 1F, -1F, 1F);
            flush(bb);
        }
        // rayos: aditivos, se dibujan con la mezcla ya cambiada por quien llama
    }

    /** rayos de luz sobre el publico */
    public void drawBeams(ShaderInstance sh, double camX, double camY, double camZ, float t, String mode, float a, float beat, float bass, float level, float[][] pal) {
        if (!set || a < 0.003F || mode.equals("off")) return;
        float S = scale;
        int n = Math.max(1, Math.min(24, Math.round(p[11])));
        double fx = fx(), fz = fz(), rx = rx(), rz = rz();
        double bx0 = x - camX, by0 = y - camY + 0.2, bz0 = z - camZ;
        set(sh, "Kind", 2F);
        set(sh, "Alpha", a);
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        V b = new V(bb);
        begin(bb);
        float[][] cols = {pal[0], pal[1], pal[2], {1F, 1F, 1F}};
        for (int i = 0; i < n; i++) {
            float u = n == 1 ? 0.5F : i / (float) (n - 1);
            double lat = (-p[8] + 2 * p[8] * u) * S;
            double back = p[9] * S;
            float side = lat < 0 ? -1F : 1F;
            double az, el;
            float inten = 0.85F;
            switch (mode) {
                case "sweep" -> { az = Math.sin(t * 1.2 + i * 0.35) * 0.9; el = Math.toRadians(58 + 14 * Math.sin(t * 0.9 + i)); }
                case "fan" -> { az = (u - 0.5) * (0.8 + 0.9 * bass); el = Math.toRadians(68 - 14 * level); inten = 0.8F + 0.4F * beat; }
                case "cross" -> { az = -side * (0.55 + 0.3 * Math.sin(t * 0.8)); el = Math.toRadians(60 + 10 * Math.sin(t + i)); inten = 0.85F + 0.3F * beat; }
                case "strobe" -> {
                    float k = (float) Math.floor(t * 2.0);
                    az = (hash(k * 3.1F + i) - 0.5F) * 1.6; el = Math.toRadians(45 + 35 * hash(k * 1.7F + i * 2.3F));
                    inten = 0.35F + 0.9F * beat;
                }
                default -> { az = Math.sin(t * 0.5 + i * 0.7) * 0.45 - side * 0.25; el = Math.toRadians(62 + 10 * Math.sin(t * 0.4 + i)); }
            }
            double dxl = Math.cos(el) * Math.sin(az), dyl = Math.sin(el), dzl = Math.cos(el) * Math.cos(az);
            double dx = rx * dxl + fx * dzl, dy = dyl, dz = rz * dxl + fz * dzl;
            double ox = bx0 + rx * lat + fx * back, oy = by0, oz = bz0 + rz * lat + fz * back;
            strip(b, ox, oy, oz, dx, dy, dz, p[10] * S, 0.22 * S, 1.1 * S, cols[i % 4], inten);
        }
        flush(bb);
    }

    /** columnas de luz que caen del cielo y anillos de impacto (aditivo). Devuelve true si todavia queda alguna viva. */
    public boolean drawColumns(ShaderInstance sh, double camX, double camY, double camZ) {
        if (columns.isEmpty()) return false;
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        V b = new V(bb);
        final float FALL = FALL_TIME;
        for (Column c : columns) {
            double ox = c.pos.x - camX, oy = c.pos.y - camY, oz = c.pos.z - camZ;
            float fall = Math.min(1F, c.age / FALL);
            float prog = 1F - (1F - fall) * (1F - fall);
            float fade = c.age > c.secs ? Math.max(0F, 1F - (c.age - c.secs) / 1.2F) : 1F;
            // columna
            set(sh, "Kind", 4F);
            set(sh, "Prog", prog);
            set(sh, "Alpha", fade);
            begin(bb);
            strip(b, ox, oy, oz, 0, 1, 0, 160, 1.3 * c.size, 1.3 * c.size, c.rgb, 1F);
            flush(bb);
            // anillos de impacto
            if (c.age > FALL) {
                float k = c.age - FALL;
                set(sh, "Kind", 3F);
                for (int r = 0; r < 2; r++) {
                    float kk = k - r * 0.25F;
                    if (kk < 0F) continue;
                    float rad = (1.2F + kk * 7F) * c.size * 1.6F;
                    float al = Math.max(0F, 1F - kk / 1.6F) * fade;
                    if (al <= 0.002F) continue;
                    set(sh, "Alpha", al);
                    sh.safeGetUniform("P1").set(c.rgb[0], c.rgb[1], c.rgb[2]);
                    sh.safeGetUniform("P2").set(c.rgb[0], c.rgb[1], c.rgb[2]);
                    sh.safeGetUniform("P0").set(c.rgb[0], c.rgb[1], c.rgb[2]);
                    begin(bb);
                    b.v(ox - rad, oy + 0.06, oz - rad, 1F, 1F, 1F, 1F, -1F, -1F);
                    b.v(ox + rad, oy + 0.06, oz - rad, 1F, 1F, 1F, 1F, 1F, -1F);
                    b.v(ox + rad, oy + 0.06, oz + rad, 1F, 1F, 1F, 1F, 1F, 1F);
                    b.v(ox - rad, oy + 0.06, oz + rad, 1F, 1F, 1F, 1F, -1F, 1F);
                    flush(bb);
                }
            }
        }
        return !columns.isEmpty();
    }
}
