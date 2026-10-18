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
        /** instantanea: la luz ya esta arriba desde el primer cuadro (la usa el impacto de la estrella) */
        public Column instant() { this.age = FALL_TIME; return this; }
        public Column(Vec3 pos, int rgb, float size, float secs) {
            this.pos = pos;
            this.rgb = new float[]{((rgb >> 16) & 255) / 255F, ((rgb >> 8) & 255) / 255F, (rgb & 255) / 255F};
            this.size = size;
            this.secs = secs;
        }
    }

    public static final float FALL_TIME = 0.9F;
    public final List<Column> columns = new ArrayList<>();

    /** estrella que cruza el cielo y cae en el escenario */
    public static final class Star {
        public final float[] rgb;
        public final Vec3 target;
        public final float dur, side;
        public float age;
        public boolean hit;
        public Star(Vec3 target, int rgb, float dur, float side) {
            this.target = target;
            this.rgb = new float[]{((rgb >> 16) & 255) / 255F, ((rgb >> 8) & 255) / 255F, (rgb & 255) / 255F};
            this.dur = dur;
            this.side = side;
        }
    }
    public final List<Star> stars = new ArrayList<>();

    /** fuentes de chispas a los costados del escenario */
    public static final class Pyro {
        public final float dur;
        public final float[] rgb;
        public final int seed;
        public float age;
        public Pyro(float dur, float[] rgb, int seed) { this.dur = dur; this.rgb = rgb; this.seed = seed; }
    }
    public final List<Pyro> pyros = new ArrayList<>();
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
            // piso redondo: una sola tarima plana (anillos y gajos) a la altura de la marca, asi el borde es un circulo perfecto
            float R = p[2] * S * 0.75F;
            int segs = 144, rings = Math.max(10, Math.min(64, (int) Math.ceil(R * 1.2F)));
            double yy = y + 0.05 - camY;
            begin(bb);
            for (int i = 0; i < rings; i++) {
                double r0 = R * i / rings, r1 = R * (i + 1) / rings;
                for (int j = 0; j < segs; j++) {
                    double a0 = Math.PI * 2 * j / segs, a1 = Math.PI * 2 * (j + 1) / segs;
                    double[][] q = {{r0, a0}, {r1, a0}, {r1, a1}, {r0, a1}};
                    for (double[] v : q) {
                        double ql = v[0] * Math.cos(v[1]), qf = v[0] * Math.sin(v[1]);
                        double wx = x + rx * ql + fx * qf, wz = z + rz * ql + fz * qf;
                        b.v(wx - camX, yy, wz - camZ, (float) (ql / R), (float) (qf / R), 0F, 1F, (float) ql + 0.5F, (float) qf + 0.5F);
                    }
                }
            }
            flush(bb);
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

    private static double[] bez(double[] a, double[] c, double[] e, double u) {
        double k = 1 - u;
        return new double[]{k * k * a[0] + 2 * k * u * c[0] + u * u * e[0], k * k * a[1] + 2 * k * u * c[1] + u * u * e[1], k * k * a[2] + 2 * k * u * c[2] + u * u * e[2]};
    }

    /** estrellas fugaces: cola de luz que sigue la trayectoria y cabeza brillante. Aditivo. */
    public void drawStars(ShaderInstance sh, double camX, double camY, double camZ) {
        if (stars.isEmpty()) return;
        double fx = fx(), fz = fz(), rx = rx(), rz = rz();
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        V b = new V(bb);
        for (Star st : stars) {
            if (st.age >= st.dur) continue;
            double tx = st.target.x - camX, ty = st.target.y - camY, tz = st.target.z - camZ;
            double[] p0 = {tx + fx * 130 + rx * st.side * 70, ty + 78, tz + fz * 130 + rz * st.side * 70};
            double[] p1 = {tx + fx * 55 - rx * st.side * 25, ty + 165, tz + fz * 55 - rz * st.side * 25};
            double[] p2 = {tx, ty + 0.6, tz};
            final int N = 18;
            double[][] pts = new double[N][];
            for (int k = 0; k < N; k++) {
                double tau = Math.min(1.0, Math.max(0.0, (st.age - k * 0.035) / st.dur));
                pts[k] = bez(p0, p1, p2, Math.pow(tau, 1.6));
            }
            // cola (cinta con fundido hacia atras)
            set(sh, "Kind", 7F);
            set(sh, "Alpha", 1F);
            begin(bb);
            double[][] side = new double[N][];
            for (int k = 0; k < N; k++) {
                double[] a = pts[Math.max(0, k - 1)], c = pts[Math.min(N - 1, k + 1)];
                double dx = c[0] - a[0], dy = c[1] - a[1], dz = c[2] - a[2];
                double vx = -pts[k][0], vy = -pts[k][1], vz = -pts[k][2];
                double sx = dy * vz - dz * vy, sy = dz * vx - dx * vz, sz = dx * vy - dy * vx;
                double l = Math.sqrt(sx * sx + sy * sy + sz * sz);
                if (l < 1e-6) l = 1;
                double w = (3.6 - 3.2 * k / (N - 1.0)) * Math.max(1.0, scale / 4F);
                side[k] = new double[]{sx / l * w, sy / l * w, sz / l * w};
            }
            for (int k = 0; k < N - 1; k++) {
                float a0 = (float) Math.pow(1.0 - k / (double) (N - 1), 1.4), a1 = (float) Math.pow(1.0 - (k + 1) / (double) (N - 1), 1.4);
                b.v(pts[k][0] - side[k][0], pts[k][1] - side[k][1], pts[k][2] - side[k][2], st.rgb[0], st.rgb[1], st.rgb[2], a0, -1F, 0F);
                b.v(pts[k][0] + side[k][0], pts[k][1] + side[k][1], pts[k][2] + side[k][2], st.rgb[0], st.rgb[1], st.rgb[2], a0, 1F, 0F);
                b.v(pts[k + 1][0] + side[k + 1][0], pts[k + 1][1] + side[k + 1][1], pts[k + 1][2] + side[k + 1][2], st.rgb[0], st.rgb[1], st.rgb[2], a1, 1F, 1F);
                b.v(pts[k + 1][0] - side[k + 1][0], pts[k + 1][1] - side[k + 1][1], pts[k + 1][2] - side[k + 1][2], st.rgb[0], st.rgb[1], st.rgb[2], a1, -1F, 1F);
            }
            flush(bb);
            // cabeza: estrella brillante que mira a la camara, de tamano angular casi fijo
            double[] h = pts[0];
            double dist = Math.sqrt(h[0] * h[0] + h[1] * h[1] + h[2] * h[2]);
            double nx = -h[0] / dist, ny = -h[1] / dist, nz = -h[2] / dist;
            double rxv = ny * 0 - nz * 1, ryv = nz * 0 - nx * 0, rzv = nx * 1 - ny * 0;
            double rl = Math.sqrt(rxv * rxv + ryv * ryv + rzv * rzv);
            if (rl < 1e-6) continue;
            rxv /= rl; ryv /= rl; rzv /= rl;
            double uxv = ny * rzv - nz * ryv, uyv = nz * rxv - nx * rzv, uzv = nx * ryv - ny * rxv;
            double half = dist * (0.055 + 0.015 * Math.sin(st.age * 18.0));
            set(sh, "Kind", 6F);
            set(sh, "Prog", st.age);
            begin(bb);
            b.v(h[0] - rxv * half - uxv * half, h[1] - ryv * half - uyv * half, h[2] - rzv * half - uzv * half, st.rgb[0], st.rgb[1], st.rgb[2], 1F, -1F, -1F);
            b.v(h[0] + rxv * half - uxv * half, h[1] + ryv * half - uyv * half, h[2] + rzv * half - uzv * half, st.rgb[0], st.rgb[1], st.rgb[2], 1F, 1F, -1F);
            b.v(h[0] + rxv * half + uxv * half, h[1] + ryv * half + uyv * half, h[2] + rzv * half + uzv * half, st.rgb[0], st.rgb[1], st.rgb[2], 1F, 1F, 1F);
            b.v(h[0] - rxv * half + uxv * half, h[1] - ryv * half + uyv * half, h[2] - rzv * half + uzv * half, st.rgb[0], st.rgb[1], st.rgb[2], 1F, -1F, 1F);
            flush(bb);
        }
    }

    private static float rnd(int seed, int i, int k) {
        float s = (float) Math.sin(seed * 12.9898F + i * 78.233F + k * 37.719F) * 43758.5453F;
        return s - (float) Math.floor(s);
    }

    /** fuentes de chispas (pirotecnia) a los costados del escenario. Aditivo. */
    public void drawPyros(ShaderInstance sh, double camX, double camY, double camZ) {
        if (!set || pyros.isEmpty()) return;
        float S = scale;
        double fx = fx(), fz = fz(), rx = rx(), rz = rz();
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        V b = new V(bb);
        set(sh, "Kind", 2F);
        set(sh, "Alpha", 1F);
        begin(bb);
        float k = (float) Math.sqrt(Math.max(1F, S / 4F));
        for (Pyro py : pyros) {
            for (int sgn = -1; sgn <= 1; sgn += 2) {
                double lat = sgn * p[3] * S * 0.55, fwd = p[4] * S + 1.5 * S;
                double sx0 = x + rx * lat + fx * fwd - camX, sy0 = y - camY + 0.2, sz0 = z + rz * lat + fz * fwd - camZ;
                for (int i = 0; i < 70; i++) {
                    int id = i * 2 + (sgn > 0 ? 1 : 0);
                    float birth = rnd(py.seed, id, 1) * py.dur * 0.85F;
                    float life = 1.5F + 0.8F * rnd(py.seed, id, 2);
                    float a = py.age - birth;
                    if (a < 0F || a > life) continue;
                    double ang = rnd(py.seed, id, 3) * Math.PI * 2, spd = (1.5 + 4.5 * rnd(py.seed, id, 4)) * k;
                    double vy = (15 + 9 * rnd(py.seed, id, 5)) * k, g = 15 * k;
                    double inward = -sgn * 2.5 * k;
                    double vx = Math.cos(ang) * spd + rx * inward, vz = Math.sin(ang) * spd + rz * inward;
                    double[] pa = {sx0 + vx * a, sy0 + vy * a - 0.5 * g * a * a, sz0 + vz * a};
                    double a2 = Math.max(0.0, a - 0.09);
                    double[] pb = {sx0 + vx * a2, sy0 + vy * a2 - 0.5 * g * a2 * a2, sz0 + vz * a2};
                    double dx = pb[0] - pa[0], dy = pb[1] - pa[1], dz = pb[2] - pa[2];
                    double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (len < 1e-4) continue;
                    float[] c = switch ((id + py.seed) % 3) { case 0 -> new float[]{1F, 0.85F, 0.45F}; case 1 -> new float[]{1F, 1F, 1F}; default -> py.rgb; };
                    float fade = 1F - a / life;
                    strip(b, pa[0], pa[1], pa[2], dx / len, dy / len, dz / len, len * 2.2, 0.07 * S / 4 + 0.03, 0.02, c, fade);
                }
            }
        }
        flush(bb);
    }
}
