package com.tunombre.jefes.client;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.InputStream;

/**
 * Geometria de Slenderman, sin nada de Minecraft (asi se puede probar aparte): la malla del cuerpo ya transformada y los tentaculos.
 * Cuatro tentaculos son patas (con pasos), seis quedan en guardia y cuatro cortos hacen de antenas. Los de guardia y las antenas son
 * cadenas con inercia y resorte: se arrastran al correr, latiguean al atacar y nunca estan quietos. Salen de la superficie real de la
 * espalda de la malla (el origen de cada uno se busca en los vertices) y empiezan dentro del cuerpo, asi no queda hueco.
 */
public final class SlenderGeo {
    public static int n;
    public static float[] pos, uv;
    public static byte[] nrm;
    private static double[][] anchors;     // en el espacio de la malla
    private static boolean loaded;

    public interface Ground { double y(double x, double yRef, double z); }

    public static final class In {
        public double px, py, pz, yaw, time, dt, speed, camX, camY, camZ;
        public int lashIdx = -1;
        public float lash;
        public Ground ground = (x, y, z) -> y;
        public boolean reset;
    }

    public static final class FList {
        public float[] a = new float[1 << 15];
        public int n;

        void add(float... v) {
            if (n + v.length > a.length) a = java.util.Arrays.copyOf(a, Math.max(a.length * 2, n + v.length));
            System.arraycopy(v, 0, a, n, v.length);
            n += v.length;
        }
    }

    /** salida: malla (x y z u v nx ny nz por vertice) y tubos (x y z nx ny nz r g b por vertice, de a 4 = un cuadrilatero) */
    public static final class Out {
        public float[] mesh;
        public final FList tube = new FList();
    }

    private static final class Chain {
        final double[][] p, prev;
        final int m;
        boolean init;

        Chain(int m) { this.m = m; p = new double[m][3]; prev = new double[m][3]; }
    }

    public static final class State {
        final double[][] tip = new double[4][3], from = new double[4][3], to = new double[4][3];
        final float[] prog = new float[4];
        final boolean[] stepping = new boolean[4];
        boolean init;
        final Chain[] chains = new Chain[10];
        public double lastTime = -1;

        public State() { for (int i = 0; i < 10; i++) chains[i] = new Chain(i < 6 ? 20 : 12); }
    }

    private SlenderGeo() { }

    // ---------------------------------------------------------------- carga ----
    public static synchronized boolean load(InputStream raw) {
        if (loaded) return true;
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(raw))) {
            n = in.readInt();
            in.readInt();
            pos = new float[n * 3];
            for (int i = 0; i < pos.length; i++) pos[i] = in.readFloat();
            nrm = new byte[n * 3];
            in.readFully(nrm);
            uv = new float[n * 2];
            for (int i = 0; i < uv.length; i++) uv[i] = in.readFloat();
        } catch (Exception e) {
            return false;
        }
        // origen de cada tentaculo: en la superficie de la espalda, un poco hacia adentro
        double[][] want = {
                {0.20, 2.05}, {-0.20, 2.05}, {0.27, 1.76}, {-0.27, 1.76},                     // patas
                {0.16, 2.68}, {-0.16, 2.68}, {0.30, 2.50}, {-0.30, 2.50}, {0.12, 2.30}, {-0.12, 2.30},   // guardias (0..5)
                {0.38, 2.72}, {-0.38, 2.72}, {0.34, 2.40}, {-0.34, 2.40}};                    // antenas
        anchors = new double[want.length][];
        for (int k = 0; k < want.length; k++) anchors[k] = backPoint(want[k][0], want[k][1]);
        loaded = true;
        return true;
    }

    private static double[] backPoint(double x, double y) {
        double best = 9;
        for (double rad = 0.08; rad < 0.5 && best > 8; rad += 0.06) {
            for (int i = 0; i < n; i++) {
                if (Math.abs(pos[i * 3] - x) < rad && Math.abs(pos[i * 3 + 1] - y) < rad * 1.2 && pos[i * 3 + 2] < best) best = pos[i * 3 + 2];
            }
        }
        if (best > 8) best = -0.3;
        return new double[]{x, y, best + 0.15};
    }

    public static double[] anchor(int i) { return anchors[i]; }

    // ---------------------------------------------------------------- cuerpo ----
    private static final class Body {
        double ox, oy, oz;              // origen del cuerpo en el mundo
        double[] r = new double[9];     // matriz 3x3 (filas)

        void set(double x, double y, double z, double yaw, double leanDeg) {
            ox = x; oy = y; oz = z;
            double b = -Math.toRadians(yaw), a = Math.toRadians(leanDeg);
            double ca = Math.cos(a), sa = Math.sin(a), cb = Math.cos(b), sb = Math.sin(b);
            // R = Ry(b) * Rx(a)
            r[0] = cb; r[1] = sb * sa; r[2] = sb * ca;
            r[3] = 0; r[4] = ca; r[5] = -sa;
            r[6] = -sb; r[7] = cb * sa; r[8] = cb * ca;
        }

        double[] pt(double x, double y, double z) {
            return new double[]{ox + r[0] * x + r[1] * y + r[2] * z, oy + r[3] * x + r[4] * y + r[5] * z, oz + r[6] * x + r[7] * y + r[8] * z};
        }

        double[] dir(double x, double y, double z) {
            return new double[]{r[0] * x + r[1] * y + r[2] * z, r[3] * x + r[4] * y + r[5] * z, r[6] * x + r[7] * y + r[8] * z};
        }
    }

    private static final double[][] REST = {{1.9, 0.7}, {-1.9, 0.7}, {1.7, -1.8}, {-1.7, -1.8}};
    private static final double[][] G_DIR = {{0.55, 0.60, -0.75}, {-0.55, 0.60, -0.75}, {1.0, 0.40, -0.50}, {-1.0, 0.40, -0.50}, {0.35, 0.80, -0.55}, {-0.35, 0.80, -0.55}};
    private static final double[] G_LEN = {4.6, 4.6, 4.0, 4.0, 3.5, 3.5};
    private static final double[][] F_DIR = {{0.9, 0.45, 0.45}, {-0.9, 0.45, 0.45}, {0.8, 0.2, 0.6}, {-0.8, 0.2, 0.6}};

    // ---------------------------------------------------------------- cuadro ----
    public static float liftOf(In in, State st) {
        int stepping = 0;
        for (boolean b : st.stepping) if (b) stepping++;
        double run = Math.min(1.0, in.speed * 9);
        return (float) (1.62 + 0.05 * Math.sin(in.time * 0.045) + Math.sin(in.time * 0.62) * 0.045 * run - Math.min(in.speed * 2.2, 0.12) - 0.03 * stepping);
    }

    /** malla del cuerpo en el mundo (relativa a la posicion de la entidad); glitch = amplitud del desgarro, 0 = nada */
    public static float[] mesh(In in, State st, double glitch, float[] reuse) {
        Body bd = body(in, st);
        float[] o = reuse != null && reuse.length >= n * 8 ? reuse : new float[n * 8];
        double right = Math.cos(Math.toRadians(in.yaw)), rz = Math.sin(Math.toRadians(in.yaw));
        for (int i = 0; i < n; i++) {
            double[] w = bd.pt(pos[i * 3], pos[i * 3 + 1], pos[i * 3 + 2]);
            double[] nn = bd.dir(nrm[i * 3] / 127.0, nrm[i * 3 + 1] / 127.0, nrm[i * 3 + 2] / 127.0);
            double dx = 0;
            if (glitch > 0) dx = shift(w[1] - in.py, in.time, glitch);
            o[i * 8] = (float) (w[0] - in.px + dx * right); o[i * 8 + 1] = (float) (w[1] - in.py); o[i * 8 + 2] = (float) (w[2] - in.pz + dx * rz);
            o[i * 8 + 3] = uv[i * 2]; o[i * 8 + 4] = uv[i * 2 + 1];
            o[i * 8 + 5] = (float) nn[0]; o[i * 8 + 6] = (float) nn[1]; o[i * 8 + 7] = (float) nn[2];
        }
        return o;
    }

    public static double shift(double y, double time, double amp) {
        int band = (int) Math.floor(y * 7.0) + (int) Math.floor(time * 2.5);
        double h = hash(band) * 2 - 1;
        return Math.abs(h) > 0.45 ? h * amp : 0;
    }

    private static double hash(int x) {
        x = (x << 13) ^ x;
        return (1.0 - ((x * (x * x * 15731 + 789221) + 1376312589) & 0x7fffffff) / 1073741824.0) * 0.5 + 0.5;
    }

    private static Body body(In in, State st) {
        Body bd = new Body();
        float lift = liftOf(in, st);
        bd.set(in.px, in.py + lift, in.pz, in.yaw, Math.min(in.speed * 75, 9) + Math.sin(in.time * 0.03) * 0.8);
        return bd;
    }

    /** actualiza patas y cadenas y escribe los tubos en {@code out} */
    public static void tentacles(In in, State st, Out out) {
        double dt = Math.max(0.0, Math.min(in.dt, 2.5));
        Body bd = body(in, st);
        double yawR = Math.toRadians(in.yaw);
        double[] fwd = {-Math.sin(yawR), 0, Math.cos(yawR)}, rgt = {Math.cos(yawR), 0, Math.sin(yawR)};
        if (in.reset) { st.init = false; for (Chain c : st.chains) c.init = false; }
        // ---- patas ----
        if (!st.init) {
            for (int i = 0; i < 4; i++) {
                double ox = REST[i][0] * rgt[0] + REST[i][1] * fwd[0], oz = REST[i][0] * rgt[2] + REST[i][1] * fwd[2];
                st.tip[i][0] = in.px + ox; st.tip[i][2] = in.pz + oz; st.tip[i][1] = in.ground.y(st.tip[i][0], in.py, st.tip[i][2]);
                st.stepping[i] = false;
            }
            st.init = true;
        }
        int stepping = 0;
        for (boolean b : st.stepping) if (b) stepping++;
        for (int i = 0; i < 4; i++) {
            double ox = REST[i][0] * rgt[0] + REST[i][1] * fwd[0], oz = REST[i][0] * rgt[2] + REST[i][1] * fwd[2];
            double dx = in.px + ox - st.tip[i][0], dz = in.pz + oz - st.tip[i][2], d2 = dx * dx + dz * dz;
            if (d2 > 70) {
                st.tip[i][0] = in.px + ox; st.tip[i][2] = in.pz + oz; st.tip[i][1] = in.ground.y(st.tip[i][0], in.py, st.tip[i][2]); st.stepping[i] = false;
                continue;
            }
            if (st.stepping[i]) {
                st.prog[i] += (float) (dt * (0.17 + in.speed * 1.6));
                float k = Math.min(1F, st.prog[i]);
                float ease = k * k * (3 - 2 * k);
                for (int c = 0; c < 3; c++) st.tip[i][c] = st.from[i][c] + (st.to[i][c] - st.from[i][c]) * ease;
                st.tip[i][1] += Math.sin(Math.PI * k) * (0.8 + in.speed * 3);
                if (st.prog[i] >= 1F) { st.stepping[i] = false; st.tip[i][1] = st.to[i][1]; }
            } else {
                int partner = (i ^ 1);
                double thr = 1.0 + Math.max(0, 0.9 - in.speed * 12);
                if (stepping < 2 && !st.stepping[partner] && d2 > thr + in.speed * 3) {
                    st.stepping[i] = true; stepping++;
                    st.prog[i] = 0;
                    System.arraycopy(st.tip[i], 0, st.from[i], 0, 3);
                    double lead = in.speed * 10;
                    double tx = in.px + ox + fwd[0] * lead, tz = in.pz + oz + fwd[2] * lead;
                    st.to[i][0] = tx; st.to[i][2] = tz; st.to[i][1] = in.ground.y(tx, in.py, tz);
                }
            }
        }
        for (int i = 0; i < 4; i++) leg(in, st, bd, out, i, rgt);
        // ---- guardias y antenas ----
        for (int k = 0; k < 10; k++) chain(in, st, bd, out, k, dt, fwd);
    }

    private static void leg(In in, State st, Body bd, Out out, int i, double[] rgt) {
        double[] a = bd.pt(anchors[i][0], anchors[i][1], anchors[i][2]);
        double[] t = st.tip[i];
        double side = anchors[i][0] > 0 ? 1 : -1;
        double ph = i * 1.9;
        // control: sale hacia afuera y arriba, y cae hasta apoyarse
        double mx = (a[0] + t[0]) / 2 + rgt[0] * side * 1.25, mz = (a[2] + t[2]) / 2 + rgt[2] * side * 1.25;
        double my = Math.max(a[1], t[1]) + 0.55;
        int M = 14;
        double[][] ctl = new double[M][3];
        for (int s = 0; s < M; s++) {
            double u = s / (double) (M - 1), w = 1 - u;
            double x = w * w * a[0] + 2 * w * u * mx + u * u * t[0];
            double y = w * w * a[1] + 2 * w * u * my + u * u * t[1];
            double z = w * w * a[2] + 2 * w * u * mz + u * u * t[2];
            double env = Math.sin(Math.PI * u);
            double wv = Math.sin(u * 7 - in.time * 0.13 + ph) * 0.11 * env, wu = Math.cos(u * 5.2 - in.time * 0.10 + ph) * 0.08 * env;
            ctl[s][0] = x + rgt[0] * wv; ctl[s][1] = y + wu; ctl[s][2] = z + rgt[2] * wv;
        }
        // la punta se apoya plana en el suelo
        ctl[M - 1][1] = t[1] + 0.03;
        ctl[M - 2][1] = Math.max(ctl[M - 2][1], t[1] + 0.10);
        tube(in, out, smooth(ctl, 3), 0.23F, 0.045F, i * 1.3, 0.14F);
    }

    private static void chain(In in, State st, Body bd, Out out, int k, double dt, double[] fwd) {
        Chain c = st.chains[k];
        boolean guard = k < 6;
        double[] an = guard ? anchors[4 + k] : anchors[10 + (k - 6)];
        double[] a = bd.pt(an[0], an[1], an[2]);
        double[] d0 = guard ? G_DIR[k] : F_DIR[k - 6];
        double L = guard ? G_LEN[k] : 1.7;
        double[] dw = bd.dir(d0[0], d0[1], d0[2]);
        double dl = Math.sqrt(dw[0] * dw[0] + dw[1] * dw[1] + dw[2] * dw[2]);
        // al correr se echan hacia atras
        double trail = Math.min(in.speed * 5.0, 0.9);
        dw[0] = dw[0] / dl - fwd[0] * trail; dw[1] = dw[1] / dl + 0.1 * trail; dw[2] = dw[2] / dl - fwd[2] * trail;
        dl = Math.sqrt(dw[0] * dw[0] + dw[1] * dw[1] + dw[2] * dw[2]);
        dw[0] /= dl; dw[1] /= dl; dw[2] /= dl;
        double[] p1 = cross(dw, new double[]{0, 1, 0});
        double l1 = Math.sqrt(p1[0] * p1[0] + p1[1] * p1[1] + p1[2] * p1[2]);
        if (l1 < 1e-4) p1 = new double[]{1, 0, 0}; else { p1[0] /= l1; p1[1] /= l1; p1[2] /= l1; }
        double[] p2 = cross(dw, p1);
        double ph = k * 1.7;
        double fast = guard ? 1.0 : 2.1;
        int m = c.m;
        double[][] rest = new double[m][3];
        boolean lashing = guard && k == in.lashIdx && in.lash > 0;
        for (int i = 0; i < m; i++) {
            double s = i / (double) (m - 1);
            double lat = Math.sin(in.time * 0.06 * fast + ph + s * 3.4) * (guard ? 0.45 : 0.32) * s;
            double vert = Math.cos(in.time * 0.05 * fast + ph * 1.3 + s * 2.6) * (guard ? 0.32 : 0.26) * s;
            double curl = guard ? s * s * 0.8 : -s * s * 0.45;
            double x = a[0] + dw[0] * L * s + p1[0] * lat + p2[0] * vert;
            double y = a[1] + dw[1] * L * s + p1[1] * lat + p2[1] * vert + curl;
            double z = a[2] + dw[2] * L * s + p1[2] * lat + p2[2] * vert;
            if (lashing) {
                double sg = k == 0 ? 1 : -1;
                double wx = a[0] + fwd[0] * 5.8 * s + sg * 0.9 * Math.sin(s * Math.PI) * Math.cos(Math.toRadians(in.yaw));
                double wy = a[1] - 0.7 * s + Math.sin(s * Math.PI) * 1.0 - s * s * 1.0;
                double wz = a[2] + fwd[2] * 5.8 * s + sg * 0.9 * Math.sin(s * Math.PI) * Math.sin(Math.toRadians(in.yaw));
                x += (wx - x) * in.lash; y += (wy - y) * in.lash; z += (wz - z) * in.lash;
            }
            rest[i][0] = x; rest[i][1] = y; rest[i][2] = z;
        }
        if (!c.init) {
            for (int i = 0; i < m; i++) for (int q = 0; q < 3; q++) { c.p[i][q] = rest[i][q]; c.prev[i][q] = rest[i][q]; }
            c.init = true;
        }
        // inercia + resorte hacia la forma de reposo
        double damp = Math.pow(lashing ? 0.80 : 0.90, dt), seg = L / (m - 1);
        for (int i = 1; i < m; i++) {
            double s = i / (double) (m - 1);
            double kk = Math.min(1.0, (lashing ? 0.55 : 0.17) * (1 - 0.55 * s) * dt);
            for (int q = 0; q < 3; q++) {
                double cur = c.p[i][q];
                double vel = (cur - c.prev[i][q]) * damp;
                c.prev[i][q] = cur;
                double nx = cur + vel;
                nx += (rest[i][q] - nx) * kk;
                c.p[i][q] = nx;
            }
            c.p[i][1] -= 0.0025 * dt * (guard ? 1 : 2);
        }
        c.p[0][0] = a[0]; c.p[0][1] = a[1]; c.p[0][2] = a[2];
        for (int it = 0; it < 4; it++) {
            c.p[0][0] = a[0]; c.p[0][1] = a[1]; c.p[0][2] = a[2];
            for (int i = 1; i < m; i++) {
                double dx = c.p[i][0] - c.p[i - 1][0], dy = c.p[i][1] - c.p[i - 1][1], dz = c.p[i][2] - c.p[i - 1][2];
                double l = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (l < 1e-9) continue;
                double f = seg / l;
                c.p[i][0] = c.p[i - 1][0] + dx * f; c.p[i][1] = c.p[i - 1][1] + dy * f; c.p[i][2] = c.p[i - 1][2] + dz * f;
            }
        }
        // el suelo: nada lo atraviesa
        for (int i = 1; i < m; i++) {
            double g = in.py + 0.05;
            if (c.p[i][1] < g) c.p[i][1] = g;
        }
        double[][] cp = new double[m][3];
        for (int i = 0; i < m; i++) { cp[i][0] = c.p[i][0]; cp[i][1] = c.p[i][1]; cp[i][2] = c.p[i][2]; }
        if (guard) tube(in, out, smooth(cp, 3), 0.19F, 0.018F, ph, 0.22F);
        else tube(in, out, smooth(cp, 3), 0.11F, 0.014F, ph, 0.18F);
    }

    // ---------------------------------------------------------------- utilidades ----
    private static double[] cross(double[] a, double[] b) {
        return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    /** Catmull-Rom: mete {@code sub - 1} puntos entre cada par para que las curvas sean redondas */
    private static double[][] smooth(double[][] p, int sub) {
        int m = p.length;
        double[][] o = new double[(m - 1) * sub + 1][3];
        int w = 0;
        for (int i = 0; i < m - 1; i++) {
            double[] p0 = p[Math.max(0, i - 1)], p1 = p[i], p2 = p[i + 1], p3 = p[Math.min(m - 1, i + 2)];
            for (int s = 0; s < sub; s++) {
                double u = s / (double) sub, u2 = u * u, u3 = u2 * u;
                for (int q = 0; q < 3; q++)
                    o[w][q] = 0.5 * ((2 * p1[q]) + (-p0[q] + p2[q]) * u + (2 * p0[q] - 5 * p1[q] + 4 * p2[q] - p3[q]) * u2 + (-p0[q] + 3 * p1[q] - 3 * p2[q] + p3[q]) * u3);
                w++;
            }
        }
        System.arraycopy(p[m - 1], 0, o[w], 0, 3);
        return o;
    }

    private static final int SIDES = 8;

    /** tubo viscoso: radio que se afina con bultos que viajan, base ensanchada que se funde con el cuerpo y brillo humedo segun la camara */
    private static void tube(In in, Out out, double[][] p, float r0, float r1, double phase, float bulge) {
        int m = p.length;
        float[][] ring = new float[m * SIDES][9];
        double px0 = p[0][0];
        double[] prevU = null;
        for (int i = 0; i < m; i++) {
            double[] a = p[Math.max(0, i - 1)], b = p[Math.min(m - 1, i + 1)];
            double tx = b[0] - a[0], ty = b[1] - a[1], tz = b[2] - a[2];
            double tl = Math.sqrt(tx * tx + ty * ty + tz * tz);
            if (tl < 1e-9) { tx = 0; ty = 1; tz = 0; tl = 1; }
            tx /= tl; ty /= tl; tz /= tl;
            double[] u;
            if (prevU == null) {
                double[] ref = Math.abs(ty) > 0.95 ? new double[]{1, 0, 0} : new double[]{0, 1, 0};
                u = cross(new double[]{tx, ty, tz}, ref);
            } else {
                // transporte paralelo: evita que el tubo se retuerza
                double d = prevU[0] * tx + prevU[1] * ty + prevU[2] * tz;
                u = new double[]{prevU[0] - tx * d, prevU[1] - ty * d, prevU[2] - tz * d};
            }
            double ul = Math.sqrt(u[0] * u[0] + u[1] * u[1] + u[2] * u[2]);
            if (ul < 1e-9) { u = new double[]{1, 0, 0}; ul = 1; }
            u[0] /= ul; u[1] /= ul; u[2] /= ul;
            prevU = u;
            double[] v = cross(new double[]{tx, ty, tz}, u);
            double s = i / (double) (m - 1);
            double r = (r0 + (r1 - r0) * Math.pow(s, 0.85)) * (1 + 0.55 * Math.exp(-s * 13));
            r *= 1 + bulge * Math.sin(s * 24 - in.time * 0.15 + phase) * Math.min(1, s * 4) * (1 - 0.6 * s);
            for (int q = 0; q < SIDES; q++) {
                double ph = Math.PI * 2 * q / SIDES, c = Math.cos(ph), sn = Math.sin(ph);
                double nx = u[0] * c + v[0] * sn, ny = u[1] * c + v[1] * sn, nz = u[2] * c + v[2] * sn;
                float[] w = ring[i * SIDES + q];
                w[0] = (float) (p[i][0] + nx * r - in.px); w[1] = (float) (p[i][1] + ny * r - in.py); w[2] = (float) (p[i][2] + nz * r - in.pz);
                w[3] = (float) nx; w[4] = (float) ny; w[5] = (float) nz;
                // color humedo
                double vx = in.camX - p[i][0], vy = in.camY - p[i][1], vz = in.camZ - p[i][2];
                double vl = Math.sqrt(vx * vx + vy * vy + vz * vz) + 1e-9;
                vx /= vl; vy /= vl; vz /= vl;
                double nv = Math.abs(nx * vx + ny * vy + nz * vz);
                double fres = Math.pow(1 - nv, 2.6);
                double hx = 0.35 + vx, hy = 0.85 + vy, hz = 0.45 + vz, hl = Math.sqrt(hx * hx + hy * hy + hz * hz) + 1e-9;
                double nh = Math.max(0, (nx * hx + ny * hy + nz * hz) / hl);
                double spec = Math.pow(nh, 36) * (0.55 + 0.45 * Math.sin(s * 16 - in.time * 0.09 + phase));
                double base = 0.030;
                w[6] = (float) Math.min(1, base + 0.05 * fres + 0.45 * spec);
                w[7] = (float) Math.min(1, base + 0.06 * fres + 0.50 * spec);
                w[8] = (float) Math.min(1, base + 0.012 + 0.10 * fres + 0.62 * spec);
            }
        }
        for (int i = 0; i < m - 1; i++) {
            for (int q = 0; q < SIDES; q++) {
                int q2 = (q + 1) % SIDES;
                float[][] vs = {ring[i * SIDES + q], ring[i * SIDES + q2], ring[(i + 1) * SIDES + q2], ring[(i + 1) * SIDES + q]};
                for (float[] w : vs) out.tube.add(w);
            }
        }
    }
}
