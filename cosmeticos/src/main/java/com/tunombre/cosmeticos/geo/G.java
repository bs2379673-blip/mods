package com.tunombre.cosmeticos.geo;

import java.util.function.DoubleUnaryOperator;

/** primitivas: hojas/plumas con volumen, tubos, cartelitos, esferas, cristales */
public final class G {
    private G() { }

    public static final float PI = (float) Math.PI;

    // perfiles de ancho (f: 0 base .. 1 punta) -> 0..1
    public static final DoubleUnaryOperator LANCE = f -> {
        double a = Math.min(1, f * 6 + 0.35);
        double tip = Math.pow(Math.max(0, 1 - Math.pow(Math.max(0, (f - 0.55) / 0.45), 1.6)), 0.75);
        return a * tip;
    };
    public static final DoubleUnaryOperator PETAL = f -> Math.pow(Math.max(0, Math.sin(Math.PI * Math.pow(f, 0.75))), 0.62) * (0.35 + 0.65 * Math.min(1, f * 4));
    public static final DoubleUnaryOperator LEAF = f -> Math.pow(Math.max(0, Math.sin(Math.PI * Math.pow(f, 0.8))), 0.8);
    public static final DoubleUnaryOperator FLAME = f -> Math.pow(Math.max(0, Math.sin(Math.PI * Math.pow(f, 0.55))), 1.2) * (1 - 0.25 * f);
    public static final DoubleUnaryOperator BLADE = f -> Math.max(0, (1 - f)) * Math.min(1, 0.55 + f * 4);
    public static final DoubleUnaryOperator FLAT = f -> 1.0;

    public static final float[] FULL = {0, 0, 1, 1};

    // ---------- cintas con volumen ----------

    /**
     * hoja / pluma / petalo: desde base hacia dir, con curvatura hacia bendDir (angulo total bend, en radianes), ancho segun perfil.
     * El eje central se levanta thick sobre la superficie para que el sombreado de la sensacion de volumen.
     */
    public static void blade(Mesh m, V3 base, V3 dir, V3 side, float len, float wid, float bend, V3 bendDir, int segs,
                             DoubleUnaryOperator prof, float[] uv, int cBase, int cTip, float thick) {
        dir = dir.norm();
        V3 axis = dir.cross(bendDir);
        axis = axis.len() < 1e-5F ? side.norm() : axis.norm();
        int rows = segs + 1;
        V3[] L = new V3[rows], M = new V3[rows], R = new V3[rows], nL = new V3[rows], nM = new V3[rows], nR = new V3[rows];
        V3 p = base;
        V3 prevDir = dir;
        float segLen = len / segs;
        for (int k = 0; k < rows; k++) {
            float f = k / (float) segs;
            V3 dk = dir.rot(axis, bend * (float) Math.pow(f, 1.35F));
            V3 sk = side.perp(dk);
            sk = sk.len() < 1e-5F ? dk.cross(V3.Y) : sk;
            sk = sk.norm();
            V3 nk = sk.cross(dk).norm();
            float w = wid * 0.5F * (float) prof.applyAsDouble(f);
            L[k] = p.sub(sk.mul(w));
            R[k] = p.add(sk.mul(w));
            M[k] = p.add(nk.mul(thick * (float) Math.sin(Math.PI * Math.min(1, f * 1.2F + 0.05F))));
            nM[k] = nk;
            nL[k] = nk.sub(sk.mul(0.55F)).norm();
            nR[k] = nk.add(sk.mul(0.55F)).norm();
            if (k < segs) p = p.add(dk.mul(segLen));
            prevDir = dk;
        }
        float um = (uv[0] + uv[2]) * 0.5F;
        for (int k = 0; k < segs; k++) {
            float f0 = k / (float) segs, f1 = (k + 1) / (float) segs;
            float v0 = uv[3] + (uv[1] - uv[3]) * f0, v1 = uv[3] + (uv[1] - uv[3]) * f1;
            int c0 = Col.mix(cBase, cTip, f0), c1 = Col.mix(cBase, cTip, f1);
            m.vert(L[k], uv[0], v0, nL[k], c0); m.vert(M[k], um, v0, nM[k], c0); m.vert(M[k + 1], um, v1, nM[k + 1], c1); m.vert(L[k + 1], uv[0], v1, nL[k + 1], c1);
            m.vert(M[k], um, v0, nM[k], c0); m.vert(R[k], uv[2], v0, nR[k], c0); m.vert(R[k + 1], uv[2], v1, nR[k + 1], c1); m.vert(M[k + 1], um, v1, nM[k + 1], c1);
        }
    }

    /** cinta que sigue una curva con ancho variable, orientada hacia una direccion de referencia (p. ej. la camara) */
    public static void ribbon(Mesh m, V3[] pts, float[] w, V3 face, float[] uv, int[] cols) {
        int n = pts.length;
        V3[] L = new V3[n], R = new V3[n];
        V3 nrm = face.neg().norm();
        for (int i = 0; i < n; i++) {
            V3 tan = (i == 0 ? pts[1].sub(pts[0]) : i == n - 1 ? pts[n - 1].sub(pts[n - 2]) : pts[i + 1].sub(pts[i - 1])).norm();
            V3 sd = tan.cross(face);
            sd = sd.len() < 1e-5F ? V3.X : sd.norm();
            L[i] = pts[i].sub(sd.mul(w[i] * 0.5F));
            R[i] = pts[i].add(sd.mul(w[i] * 0.5F));
        }
        for (int i = 0; i < n - 1; i++) {
            float v0 = uv[3] + (uv[1] - uv[3]) * i / (n - 1F), v1 = uv[3] + (uv[1] - uv[3]) * (i + 1) / (n - 1F);
            m.vert(L[i], uv[0], v0, nrm, cols[i]); m.vert(R[i], uv[2], v0, nrm, cols[i]);
            m.vert(R[i + 1], uv[2], v1, nrm, cols[i + 1]); m.vert(L[i + 1], uv[0], v1, nrm, cols[i + 1]);
        }
    }

    // ---------- tubos ----------

    /** tubo con radio variable a lo largo de una polilinea; u gira alrededor (uRep veces), v avanza segun la longitud (vScale por bloque) */
    public static void tube(Mesh m, V3[] pts, float[] rad, int sides, float uRep, float vScale, float v0, int[] cols) {
        int n = pts.length;
        V3[] N = new V3[n], B = new V3[n];
        V3 t0 = pts[1].sub(pts[0]).norm();
        V3 ref = Math.abs(t0.y) < 0.9F ? V3.Y : V3.X;
        N[0] = t0.cross(ref).norm();
        B[0] = t0.cross(N[0]).norm();
        for (int i = 1; i < n; i++) {
            V3 ti = (i == n - 1 ? pts[i].sub(pts[i - 1]) : pts[i + 1].sub(pts[i - 1])).norm();
            V3 nn = N[i - 1].perp(ti);
            nn = nn.len() < 1e-5F ? N[i - 1] : nn.norm();
            N[i] = nn;
            B[i] = ti.cross(nn).norm();
        }
        float dist = 0;
        for (int i = 0; i < n - 1; i++) {
            float d0 = dist, d1 = dist + pts[i + 1].sub(pts[i]).len();
            for (int j = 0; j < sides; j++) {
                float a0 = 2 * PI * j / sides, a1 = 2 * PI * (j + 1) / sides;
                V3 o00 = N[i].mul((float) Math.cos(a0)).add(B[i].mul((float) Math.sin(a0)));
                V3 o10 = N[i].mul((float) Math.cos(a1)).add(B[i].mul((float) Math.sin(a1)));
                V3 o01 = N[i + 1].mul((float) Math.cos(a0)).add(B[i + 1].mul((float) Math.sin(a0)));
                V3 o11 = N[i + 1].mul((float) Math.cos(a1)).add(B[i + 1].mul((float) Math.sin(a1)));
                float u0 = uRep * j / sides, u1 = uRep * (j + 1) / sides;
                m.vert(pts[i].add(o00.mul(rad[i])), u0, v0 + d0 * vScale, o00, cols[i]);
                m.vert(pts[i].add(o10.mul(rad[i])), u1, v0 + d0 * vScale, o10, cols[i]);
                m.vert(pts[i + 1].add(o11.mul(rad[i + 1])), u1, v0 + d1 * vScale, o11, cols[i + 1]);
                m.vert(pts[i + 1].add(o01.mul(rad[i + 1])), u0, v0 + d1 * vScale, o01, cols[i + 1]);
            }
            dist = d1;
        }
    }

    public static int[] fill(int n, int c) { int[] a = new int[n]; java.util.Arrays.fill(a, c); return a; }

    public static float[] ffill(int n, float v) { float[] a = new float[n]; java.util.Arrays.fill(a, v); return a; }

    public static int[] ramp(int n, int c0, int c1) { int[] a = new int[n]; for (int i = 0; i < n; i++) a[i] = Col.mix(c0, c1, i / (n - 1F)); return a; }

    public static float[] taper(int n, float r0, float r1, float pow) {
        float[] r = new float[n];
        for (int i = 0; i < n; i++) r[i] = r0 + (r1 - r0) * (float) Math.pow(i / (n - 1F), pow);
        return r;
    }

    /** anillo (toro) en el plano generado por ax y ay */
    public static void torus(Mesh m, V3 c, V3 ax, V3 ay, float R, float r, int seg, int sides, float uRep, int col) {
        V3[] pts = new V3[seg + 1];
        for (int i = 0; i <= seg; i++) {
            float a = 2 * PI * i / seg;
            pts[i] = c.add(ax.mul((float) Math.cos(a) * R)).add(ay.mul((float) Math.sin(a) * R));
        }
        tube(m, pts, ffill(seg + 1, r), sides, 1F, uRep * R * 2 * PI, 0F, fill(seg + 1, col));
    }

    // ---------- cartelitos y planos ----------

    public static void quad(Mesh m, V3 a, V3 b, V3 c, V3 d, float[] uv, int col) {
        V3 nr = b.sub(a).cross(d.sub(a)).norm();
        m.vert(a, uv[0], uv[3], nr, col); m.vert(b, uv[2], uv[3], nr, col); m.vert(c, uv[2], uv[1], nr, col); m.vert(d, uv[0], uv[1], nr, col);
    }

    /** cartel que mira a la camara; rot en radianes */
    public static void sprite(Mesh m, V3 c, V3 camR, V3 camU, V3 camF, float sx, float sy, float rot, float[] uv, int col) {
        float cs = (float) Math.cos(rot), sn = (float) Math.sin(rot);
        V3 r = camR.mul(cs).add(camU.mul(sn)).mul(sx * 0.5F), u = camU.mul(cs).sub(camR.mul(sn)).mul(sy * 0.5F);
        V3 nr = camF.neg();
        m.vert(c.sub(r).sub(u), uv[0], uv[3], nr, col); m.vert(c.add(r).sub(u), uv[2], uv[3], nr, col);
        m.vert(c.add(r).add(u), uv[2], uv[1], nr, col); m.vert(c.sub(r).add(u), uv[0], uv[1], nr, col);
    }

    /** plano texturado centrado en c, con ejes ax (ancho) y ay (alto) ya escalados por el llamador */
    public static void plane(Mesh m, V3 c, V3 ax, V3 ay, float[] uv, int col) {
        quad(m, c.sub(ax).sub(ay), c.add(ax).sub(ay), c.add(ax).add(ay), c.sub(ax).add(ay), uv, col);
    }

    /** esfera con ejes propios (z = frente): la textura equirectangular tiene su centro al frente */
    public static void sphere(Mesh m, V3 c, V3 ex, V3 ey, V3 ez, float r, int lat, int lon, int col) {
        for (int i = 0; i < lat; i++) {
            float a0 = PI * (i / (float) lat - 0.5F), a1 = PI * ((i + 1) / (float) lat - 0.5F);
            for (int j = 0; j < lon; j++) {
                float b0 = 2 * PI * j / lon - PI, b1 = 2 * PI * (j + 1) / lon - PI;
                V3[] q = {sp(ex, ey, ez, a0, b0), sp(ex, ey, ez, a0, b1), sp(ex, ey, ez, a1, b1), sp(ex, ey, ez, a1, b0)};
                float[] us = {j / (float) lon, (j + 1) / (float) lon, (j + 1) / (float) lon, j / (float) lon};
                float[] vs = {1 - i / (float) lat, 1 - i / (float) lat, 1 - (i + 1) / (float) lat, 1 - (i + 1) / (float) lat};
                for (int k = 0; k < 4; k++) m.vert(c.add(q[k].mul(r)), us[k], vs[k], q[k], col);
            }
        }
    }

    private static V3 sp(V3 ex, V3 ey, V3 ez, float lat, float lon) {
        float cl = (float) Math.cos(lat);
        return ex.mul((float) Math.sin(lon) * cl).add(ey.mul((float) Math.sin(lat))).add(ez.mul((float) Math.cos(lon) * cl));
    }

    /** cristal alargado de seccion romboidal (caras planas con sombreado propio) */
    public static void shard(Mesh m, V3 base, V3 dir, V3 side, float len, float wid, float thick, float[] uv, int cBase, int cTip) {
        dir = dir.norm();
        V3 sd = side.perp(dir).norm();
        V3 nm = sd.cross(dir).norm();
        V3 mid = base.add(dir.mul(len * 0.62F));
        V3 tip = base.add(dir.mul(len));
        V3 a = base.add(sd.mul(wid * 0.35F)), b = base.add(nm.mul(thick * 0.35F)), c = base.sub(sd.mul(wid * 0.35F)), d = base.sub(nm.mul(thick * 0.35F));
        V3 ma = mid.add(sd.mul(wid * 0.5F)), mb = mid.add(nm.mul(thick * 0.5F)), mc = mid.sub(sd.mul(wid * 0.5F)), md = mid.sub(nm.mul(thick * 0.5F));
        V3[] lo = {a, b, c, d}, hi = {ma, mb, mc, md};
        int cm = Col.mix(cBase, cTip, 0.55F);
        for (int i = 0; i < 4; i++) {
            int j = (i + 1) % 4;
            quadN(m, lo[i], lo[j], hi[j], hi[i], uv, cBase, cBase, cm, cm, i);
            // punta
            V3 nr = hi[j].sub(hi[i]).cross(tip.sub(hi[i])).norm();
            float u0 = uv[0] + (uv[2] - uv[0]) * (i / 4F), u1 = uv[0] + (uv[2] - uv[0]) * ((i + 1) / 4F);
            m.vert(hi[i], u0, uv[1] + (uv[3] - uv[1]) * 0.4F, nr, cm); m.vert(hi[j], u1, uv[1] + (uv[3] - uv[1]) * 0.4F, nr, cm);
            m.vert(tip, (u0 + u1) / 2, uv[1], nr, cTip); m.vert(tip, (u0 + u1) / 2, uv[1], nr, cTip);
        }
    }

    private static void quadN(Mesh m, V3 a, V3 b, V3 c, V3 d, float[] uv, int ca, int cb, int cc, int cd, int face) {
        V3 nr = b.sub(a).cross(d.sub(a)).norm();
        float u0 = uv[0] + (uv[2] - uv[0]) * (face / 4F), u1 = uv[0] + (uv[2] - uv[0]) * ((face + 1) / 4F);
        float vm = uv[1] + (uv[3] - uv[1]) * 0.4F;
        m.vert(a, u0, uv[3], nr, ca); m.vert(b, u1, uv[3], nr, cb); m.vert(c, u1, vm, nr, cc); m.vert(d, u0, vm, nr, cd);
    }

    /** celda de un atlas regular: cols x rows, indice (cx, cy) */
    public static float[] cell(int cols, int rows, int cx, int cy) {
        return new float[]{cx / (float) cols, cy / (float) rows, (cx + 1) / (float) cols, (cy + 1) / (float) rows};
    }

    /** frame de animacion de una hoja de sprites en fila */
    public static float[] frame(int frames, int i) { return cell(frames, 1, ((i % frames) + frames) % frames, 0); }
}
