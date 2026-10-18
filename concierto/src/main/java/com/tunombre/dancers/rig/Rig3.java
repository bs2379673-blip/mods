package com.tunombre.dancers.rig;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Formato 3: modelo con los huesos reales del rig (p. ej. MMD) y animaciones bakeadas (.dnc) a partir de VMD.
 * Sin dependencias de Minecraft. Espacio: el de Epic Fight (X derecha, Y frente, Z arriba), en metros.
 */
public final class Rig3 {
    private Rig3() {}

    private static final double STEP = 1.0 / 120.0;

    public static final class Model3 {
        public String name;
        public String[] textures;
        public String[] boneNames;
        public int[] parent;
        public double[] pivot;            // 3 por hueso
        public double unit;               // metros por unidad MMD
        public Rig.Skin skin;
        // resortes (en orden padres primero)
        public int[] sBone, sAnchor, sGroup;
        public double[] sTip, sLen, sPar, sRad;     // sPar: rigidez, amortiguacion, gravedad, angulo por eslabon (rad), angulo total (rad)
        public boolean[] isSpring;
        // colisionadores (capsulas ligadas a un hueso no resorte)
        public int[] cBone, cMask;
        public double[] cA, cB, cR;
        public int[] pBone, pMask; public double[] pP, pN;   // planos: el nodo debe quedar del lado al que apunta pN
        // morphs de cara
        public String[] morphNames;
        public int[][] morphIdx;
        public float[][] morphD;
        public Map<String, Integer> morphByName = new HashMap<>();
        public Object userData;

        public int boneCount() { return boneNames.length; }

        public static Model3 load(JsonObject o) {
            Model3 m = new Model3();
            m.name = o.get("name").getAsString();
            JsonArray tx = o.getAsJsonArray("textures"); m.textures = new String[tx.size()];
            for (int i = 0; i < tx.size(); i++) m.textures[i] = tx.get(i).getAsString();
            m.unit = o.has("unit") ? o.get("unit").getAsDouble() : 0.08;
            JsonArray bs = o.getAsJsonArray("bones"); int nb = bs.size();
            m.boneNames = new String[nb]; m.parent = new int[nb]; m.pivot = new double[nb * 3]; m.isSpring = new boolean[nb];
            for (int i = 0; i < nb; i++) {
                JsonObject b = bs.get(i).getAsJsonObject();
                m.boneNames[i] = b.get("n").getAsString(); m.parent[i] = b.get("p").getAsInt();
                JsonArray pv = b.getAsJsonArray("pv");
                for (int k = 0; k < 3; k++) m.pivot[i * 3 + k] = pv.get(k).getAsDouble();
            }
            // resortes
            JsonArray sp = o.has("springs") ? o.getAsJsonArray("springs") : new JsonArray();
            int S = sp.size();
            m.sBone = new int[S]; m.sAnchor = new int[S]; m.sGroup = new int[S]; m.sTip = new double[S * 3]; m.sLen = new double[S]; m.sPar = new double[S * 5]; m.sRad = new double[S];
            Map<String, Integer> groups = new HashMap<>();
            for (int s = 0; s < S; s++) {
                JsonObject so = sp.get(s).getAsJsonObject();
                int b = so.get("bone").getAsInt();
                m.sBone[s] = b; m.sAnchor[s] = so.get("anchor").getAsInt(); m.isSpring[b] = true;
                String g = so.has("group") ? so.get("group").getAsString() : "hair";
                m.sGroup[s] = groups.computeIfAbsent(g, k -> groups.size());
                double len2 = 0;
                for (int k = 0; k < 3; k++) {
                    m.sTip[s * 3 + k] = so.getAsJsonArray("tip").get(k).getAsDouble();
                    double d = m.sTip[s * 3 + k] - m.pivot[b * 3 + k]; len2 += d * d;
                }
                m.sLen[s] = Math.max(Math.sqrt(len2), 1e-4);
                m.sRad[s] = so.has("radius") ? so.get("radius").getAsDouble() : 0;
                m.sPar[s * 5] = so.has("stiffness") ? so.get("stiffness").getAsDouble() : 90;
                m.sPar[s * 5 + 1] = so.has("damping") ? so.get("damping").getAsDouble() : 4;
                m.sPar[s * 5 + 2] = so.has("gravity") ? so.get("gravity").getAsDouble() : 2;
                m.sPar[s * 5 + 3] = Math.toRadians(so.has("max_angle") ? so.get("max_angle").getAsDouble() : 25);
                m.sPar[s * 5 + 4] = Math.toRadians(so.has("max_total") ? so.get("max_total").getAsDouble() : 70);
            }
            // colisionadores
            JsonArray co = o.has("colliders") ? o.getAsJsonArray("colliders") : new JsonArray();
            int C = co.size();
            m.cBone = new int[C]; m.cMask = new int[C]; m.cA = new double[C * 3]; m.cB = new double[C * 3]; m.cR = new double[C];
            for (int c = 0; c < C; c++) {
                JsonObject cj = co.get(c).getAsJsonObject();
                m.cBone[c] = cj.get("bone").getAsInt(); m.cR[c] = cj.get("r").getAsDouble();
                for (int k = 0; k < 3; k++) { m.cA[c * 3 + k] = cj.getAsJsonArray("a").get(k).getAsDouble(); m.cB[c * 3 + k] = cj.getAsJsonArray("b").get(k).getAsDouble(); }
                int mask = 0;
                for (JsonElement ge : cj.getAsJsonArray("groups")) { Integer gi = groups.get(ge.getAsString()); if (gi != null) mask |= 1 << gi; }
                m.cMask[c] = mask;
            }
            // planos (p.ej. el pelo siempre detras del torso)
            JsonArray pl = o.has("planes") ? o.getAsJsonArray("planes") : new JsonArray();
            int PN = pl.size();
            m.pBone = new int[PN]; m.pMask = new int[PN]; m.pP = new double[PN * 3]; m.pN = new double[PN * 3];
            for (int c = 0; c < PN; c++) {
                JsonObject pj = pl.get(c).getAsJsonObject();
                m.pBone[c] = pj.get("bone").getAsInt();
                double nl = 0;
                for (int k = 0; k < 3; k++) { m.pP[c * 3 + k] = pj.getAsJsonArray("p").get(k).getAsDouble(); double nv = pj.getAsJsonArray("n").get(k).getAsDouble(); m.pN[c * 3 + k] = nv; nl += nv * nv; }
                nl = Math.sqrt(nl); for (int k = 0; k < 3; k++) m.pN[c * 3 + k] /= nl;
                int mask = 0;
                for (JsonElement ge : pj.getAsJsonArray("groups")) { Integer gi = groups.get(ge.getAsString()); if (gi != null) mask |= 1 << gi; }
                m.pMask[c] = mask;
            }
            // malla
            JsonObject so = o.getAsJsonObject("skin");
            Rig.Skin k = new Rig.Skin();
            JsonArray va = so.getAsJsonArray("v"); k.v = new float[va.size()];
            for (int i = 0; i < k.v.length; i++) k.v[i] = va.get(i).getAsFloat();
            JsonArray bia = so.getAsJsonArray("bi"); k.bi = new int[bia.size()];
            for (int i = 0; i < k.bi.length; i++) k.bi[i] = bia.get(i).getAsInt();
            JsonArray bwa = so.getAsJsonArray("bw"); k.bw = new float[bwa.size()];
            for (int i = 0; i < k.bw.length; i++) k.bw[i] = bwa.get(i).getAsFloat();
            JsonArray ta = so.getAsJsonArray("tris"); k.tris = new int[ta.size()];
            for (int i = 0; i < k.tris.length; i++) k.tris[i] = ta.get(i).getAsInt();
            JsonArray ga = so.getAsJsonArray("groups"); int G = ga.size();
            k.groupTex = new int[G]; k.groupStart = new int[G]; k.groupCount = new int[G];
            for (int i = 0; i < G; i++) { JsonObject go = ga.get(i).getAsJsonObject(); k.groupTex[i] = go.get("tex").getAsInt(); k.groupStart[i] = go.get("start").getAsInt(); k.groupCount[i] = go.get("count").getAsInt(); }
            m.skin = k;
            // morphs
            List<String> mn = new ArrayList<>(); List<int[]> mi = new ArrayList<>(); List<float[]> md = new ArrayList<>();
            if (o.has("morphs")) for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("morphs").entrySet()) {
                JsonObject mo = e.getValue().getAsJsonObject();
                JsonArray ia = mo.getAsJsonArray("i"), da = mo.getAsJsonArray("d");
                int[] idx = new int[ia.size()]; float[] d = new float[da.size()];
                for (int i = 0; i < idx.length; i++) idx[i] = ia.get(i).getAsInt();
                for (int i = 0; i < d.length; i++) d[i] = da.get(i).getAsFloat();
                m.morphByName.put(e.getKey(), mn.size()); mn.add(e.getKey()); mi.add(idx); md.add(d);
            }
            m.morphNames = mn.toArray(new String[0]); m.morphIdx = mi.toArray(new int[0][]); m.morphD = md.toArray(new float[0][]);
            return m;
        }
    }

    /** Animacion bakeada .dnc (30 fps, un cuaternion local por hueso y fotograma). */
    public static final class Anim3 {
        public int fps, n;
        public double duration;
        int[] constBone; float[] constQ;
        int[] varBone; short[][] varQ;
        int rootBone; float[] root;
        String[] morphName; int[][] morphFrame; float[][] morphW;
        int[] boneToVar, boneToConst;   // por hueso del modelo (se arma al usar)
        Model3 boundTo;

        public static Anim3 load(InputStream in) throws IOException {
            byte[] raw;
            try (GZIPInputStream gz = new GZIPInputStream(in)) { raw = gz.readAllBytes(); }
            ByteBuffer b = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
            if (b.get() != 'D' || b.get() != 'N' || b.get() != 'C' || b.get() != '1') throw new IOException("no es un .dnc");
            Anim3 a = new Anim3();
            a.fps = b.getShort() & 0xFFFF; a.n = b.getInt();
            int nc = b.getShort() & 0xFFFF, nv = b.getShort() & 0xFFFF;
            a.constBone = new int[nc]; a.constQ = new float[nc * 4];
            for (int i = 0; i < nc; i++) { a.constBone[i] = b.getShort() & 0xFFFF; for (int k = 0; k < 4; k++) a.constQ[i * 4 + k] = b.getFloat(); }
            a.varBone = new int[nv]; a.varQ = new short[nv][];
            for (int i = 0; i < nv; i++) {
                a.varBone[i] = b.getShort() & 0xFFFF;
                short[] q = new short[a.n * 4]; b.asShortBuffer().get(q); b.position(b.position() + q.length * 2);
                a.varQ[i] = q;
            }
            a.rootBone = b.getShort() & 0xFFFF;
            a.root = new float[a.n * 3]; b.asFloatBuffer().get(a.root); b.position(b.position() + a.root.length * 4);
            int nm = b.getShort() & 0xFFFF;
            a.morphName = new String[nm]; a.morphFrame = new int[nm][]; a.morphW = new float[nm][];
            for (int i = 0; i < nm; i++) {
                int len = b.get() & 0xFF; byte[] nb = new byte[len]; b.get(nb); a.morphName[i] = new String(nb, StandardCharsets.UTF_8);
                int nk = b.getInt(); a.morphFrame[i] = new int[nk]; a.morphW[i] = new float[nk];
                for (int k = 0; k < nk; k++) { a.morphFrame[i][k] = b.getInt(); a.morphW[i][k] = b.getFloat(); }
            }
            a.duration = a.n > 1 ? (a.n - 1) / (double) a.fps : 0;
            return a;
        }

        private void bind(Model3 m) {
            if (boundTo == m) return;
            int nb = m.boneCount();
            boneToVar = new int[nb]; boneToConst = new int[nb];
            java.util.Arrays.fill(boneToVar, -1); java.util.Arrays.fill(boneToConst, -1);
            for (int i = 0; i < varBone.length; i++) if (varBone[i] < nb) boneToVar[varBone[i]] = i;
            for (int i = 0; i < constBone.length; i++) if (constBone[i] < nb) boneToConst[constBone[i]] = i;
            boundTo = m;
        }
    }

    /** Estado de los resortes (posiciones de las puntas, en espacio del modelo). */
    public static final class SpringState3 {
        final double[] x, xp; final boolean[] init; double acc;
        public SpringState3(Model3 m) { int S = m.sBone.length; x = new double[S * 3]; xp = new double[S * 3]; init = new boolean[S]; }
        public void reset() { java.util.Arrays.fill(init, false); acc = 0; }
    }

    private static void quatMat(double x, double y, double z, double w, double[] o, int off) {
        double l = Math.sqrt(x * x + y * y + z * z + w * w); if (l < 1e-9) { x = y = z = 0; w = 1; l = 1; }
        x /= l; y /= l; z /= l; w /= l;
        o[off] = 1 - 2 * (y * y + z * z); o[off + 1] = 2 * (x * y - w * z); o[off + 2] = 2 * (x * z + w * y);
        o[off + 3] = 2 * (x * y + w * z); o[off + 4] = 1 - 2 * (x * x + z * z); o[off + 5] = 2 * (y * z - w * x);
        o[off + 6] = 2 * (x * z - w * y); o[off + 7] = 2 * (y * z + w * x); o[off + 8] = 1 - 2 * (x * x + y * y);
    }

    private static final double I3 = 1.0 / 32767.0;

    private static double cr(double p0, double p1, double p2, double p3, double t) {
        return 0.5 * (2 * p1 + (p2 - p0) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t * t + (3 * p1 - p0 - 3 * p2 + p3) * t * t * t);
    }

    /** Catmull-Rom entre fotogramas guardados en int16 (velocidad continua: el pelo no recibe tirones a cada fotograma). */
    private static void sampleVar(short[] q, int fm, int f0, int f1, int f2, double a, double[] out) {
        double[] r = new double[16];
        int[] fs = {fm, f0, f1, f2};
        for (int j = 0; j < 4; j++) {
            double x = q[fs[j] * 4] * I3, y = q[fs[j] * 4 + 1] * I3, z = q[fs[j] * 4 + 2] * I3, w = q[fs[j] * 4 + 3] * I3;
            if (j > 0 && x * r[(j - 1) * 4] + y * r[(j - 1) * 4 + 1] + z * r[(j - 1) * 4 + 2] + w * r[(j - 1) * 4 + 3] < 0) { x = -x; y = -y; z = -z; w = -w; }
            r[j * 4] = x; r[j * 4 + 1] = y; r[j * 4 + 2] = z; r[j * 4 + 3] = w;
        }
        for (int k = 0; k < 4; k++) out[k] = cr(r[k], r[4 + k], r[8 + k], r[12 + k], a);
    }

    /** Pesos de los morphs (en el orden de model.morphNames) para un instante. */
    public static void morphWeights(Model3 m, Anim3 a, double time, float[] out) {
        java.util.Arrays.fill(out, 0f);
        if (a == null) return;
        double f = time * a.fps;
        for (int i = 0; i < a.morphName.length; i++) {
            Integer mi = m.morphByName.get(a.morphName[i]); if (mi == null) continue;
            int[] fr = a.morphFrame[i]; float[] w = a.morphW[i]; int nk = fr.length;
            if (nk == 0) continue;
            int lo = 0, hi = nk - 1;
            while (lo < hi) { int mid = (lo + hi + 1) >>> 1; if (fr[mid] <= f) lo = mid; else hi = mid - 1; }
            float v;
            if (f <= fr[0]) v = w[0];
            else if (lo >= nk - 1) v = w[nk - 1];
            else { double t = (f - fr[lo]) / Math.max(1, fr[lo + 1] - fr[lo]); v = (float) (w[lo] + (w[lo + 1] - w[lo]) * t); }
            out[mi] = v;
        }
    }

    /**
     * Calcula la pose. outR (9 por hueso, rotacion respecto al reposo) y outT (3 por hueso): vertice' = R*vertice + T.
     */
    public static void pose(Model3 m, Anim3 a, double time, double travel, SpringState3 st, double dt, double[] outR, double[] outT) {
        int nb = m.boneCount();
        double[] P = new double[nb * 3]; double[] L = new double[9], tmp = new double[3], q = new double[4];
        double[] rt = new double[3];
        int f0 = 0, f1 = 0, fm = 0, f2 = 0; double fa = 0;
        if (a != null) {
            a.bind(m);
            double f = Math.max(0, time * a.fps);
            f0 = (int) Math.min(f, a.n - 1); f1 = Math.min(f0 + 1, a.n - 1); fa = f - f0; if (f0 == a.n - 1) fa = 0;
            fm = Math.max(f0 - 1, 0); f2 = Math.min(f1 + 1, a.n - 1);
            for (int k = 0; k < 3; k++) rt[k] = cr(a.root[fm * 3 + k], a.root[f0 * 3 + k], a.root[f1 * 3 + k], a.root[f2 * 3 + k], fa);
            rt[0] *= travel; rt[1] *= travel;
        }
        for (int i = 0; i < nb; i++) {
            int p = m.parent[i];
            L[0] = 1; L[1] = 0; L[2] = 0; L[3] = 0; L[4] = 1; L[5] = 0; L[6] = 0; L[7] = 0; L[8] = 1;
            if (a != null && !m.isSpring[i]) {
                int v = a.boneToVar[i], c = a.boneToConst[i];
                if (v >= 0) { sampleVar(a.varQ[v], fm, f0, f1, f2, fa, q); quatMat(q[0], q[1], q[2], q[3], L, 0); }
                else if (c >= 0) quatMat(a.constQ[c * 4], a.constQ[c * 4 + 1], a.constQ[c * 4 + 2], a.constQ[c * 4 + 3], L, 0);
            }
            double px = m.pivot[i * 3], py = m.pivot[i * 3 + 1], pz = m.pivot[i * 3 + 2];
            if (p < 0) {
                System.arraycopy(L, 0, outR, i * 9, 9);
                P[i * 3] = px; P[i * 3 + 1] = py; P[i * 3 + 2] = pz;
                if (a != null && i == a.rootBone) { P[i * 3] += rt[0]; P[i * 3 + 1] += rt[1]; P[i * 3 + 2] += rt[2]; }
            } else {
                mul3(outR, p * 9, L, 0, outR, i * 9);
                Rig.apply(outR, p * 9, px - m.pivot[p * 3], py - m.pivot[p * 3 + 1], pz - m.pivot[p * 3 + 2], tmp);
                P[i * 3] = P[p * 3] + tmp[0]; P[i * 3 + 1] = P[p * 3 + 1] + tmp[1]; P[i * 3 + 2] = P[p * 3 + 2] + tmp[2];
                if (a != null && i == a.rootBone) {
                    Rig.apply(outR, p * 9, rt[0], rt[1], rt[2], tmp);
                    P[i * 3] += tmp[0]; P[i * 3 + 1] += tmp[1]; P[i * 3 + 2] += tmp[2];
                }
            }
            Rig.apply(outR, i * 9, px, py, pz, tmp);
            outT[i * 3] = P[i * 3] - tmp[0]; outT[i * 3 + 1] = P[i * 3 + 1] - tmp[1]; outT[i * 3 + 2] = P[i * 3 + 2] - tmp[2];
        }
        int S = m.sBone.length;
        if (S == 0) return;
        if (st != null) st.acc += Math.min(Math.max(dt, 0), 0.1);
        int steps = st == null ? 0 : Math.min(10, (int) (st.acc / STEP));
        // colisionadores en el espacio posado
        int C = m.cBone.length;
        double[] ca = new double[C * 3], cb = new double[C * 3];
        for (int c = 0; c < C; c++) {
            int b = m.cBone[c];
            Rig.apply(outR, b * 9, m.cA[c * 3], m.cA[c * 3 + 1], m.cA[c * 3 + 2], tmp); for (int k = 0; k < 3; k++) ca[c * 3 + k] = tmp[k] + outT[b * 3 + k];
            Rig.apply(outR, b * 9, m.cB[c * 3], m.cB[c * 3 + 1], m.cB[c * 3 + 2], tmp); for (int k = 0; k < 3; k++) cb[c * 3 + k] = tmp[k] + outT[b * 3 + k];
        }
        int PC = m.pBone.length;
        double[] pp = new double[PC * 3], pn = new double[PC * 3];
        for (int c = 0; c < PC; c++) {
            int b = m.pBone[c];
            Rig.apply(outR, b * 9, m.pP[c * 3], m.pP[c * 3 + 1], m.pP[c * 3 + 2], tmp); for (int k = 0; k < 3; k++) pp[c * 3 + k] = tmp[k] + outT[b * 3 + k];
            Rig.apply(outR, b * 9, m.pN[c * 3], m.pN[c * 3 + 1], m.pN[c * 3 + 2], tmp); for (int k = 0; k < 3; k++) pn[c * 3 + k] = tmp[k];
        }
        double[] Q = new double[3], d0 = new double[3], da = new double[3], X0 = new double[3], dir = new double[3];
        for (int s = 0; s < S; s++) {
            int b = m.sBone[s], pb = m.parent[b];
            double px = m.pivot[b * 3], py = m.pivot[b * 3 + 1], pz = m.pivot[b * 3 + 2];
            // el pivote del resorte sigue al padre ya posado
            Rig.apply(outR, pb * 9, px - m.pivot[pb * 3], py - m.pivot[pb * 3 + 1], pz - m.pivot[pb * 3 + 2], tmp);
            // posicion posada del padre: T_pb + R_pb * pivot_pb
            double qx = outT[pb * 3], qy = outT[pb * 3 + 1], qz = outT[pb * 3 + 2];
            Rig.apply(outR, pb * 9, m.pivot[pb * 3], m.pivot[pb * 3 + 1], m.pivot[pb * 3 + 2], Q);
            Q[0] += qx + tmp[0]; Q[1] += qy + tmp[1]; Q[2] += qz + tmp[2];
            double L1 = m.sLen[s];
            double rx = (m.sTip[s * 3] - px) / L1, ry = (m.sTip[s * 3 + 1] - py) / L1, rz = (m.sTip[s * 3 + 2] - pz) / L1;
            Rig.apply(outR, pb * 9, rx, ry, rz, d0);
            Rig.apply(outR, m.sAnchor[s] * 9, rx, ry, rz, da);
            for (int k = 0; k < 3; k++) { X0[k] = Q[k] + L1 * d0[k]; dir[k] = d0[k]; }
            if (st != null) {
                int o = s * 3;
                double ddx = st.x[o] - X0[0], ddy = st.x[o + 1] - X0[1], ddz = st.x[o + 2] - X0[2];
                if (!st.init[s] || ddx * ddx + ddy * ddy + ddz * ddz > 9 * L1 * L1) { for (int k = 0; k < 3; k++) { st.x[o + k] = X0[k]; st.xp[o + k] = X0[k]; } st.init[s] = true; }
                double kk = m.sPar[s * 5], cc = m.sPar[s * 5 + 1], g = m.sPar[s * 5 + 2], maxA = m.sPar[s * 5 + 3], maxT = m.sPar[s * 5 + 4];
                double cosA = Math.cos(maxA), cosT = Math.cos(maxT), damp = Math.max(0, 1 - cc * STEP);
                int gm = 1 << m.sGroup[s];
                for (int it = 0; it < steps; it++) {
                    double vx = (st.x[o] - st.xp[o]) * damp, vy = (st.x[o + 1] - st.xp[o + 1]) * damp, vz = (st.x[o + 2] - st.xp[o + 2]) * damp;
                    double vl = Math.sqrt(vx * vx + vy * vy + vz * vz), vmax = 0.15 * L1;
                    if (vl > vmax) { double sc = vmax / vl; vx *= sc; vy *= sc; vz *= sc; }
                    double nx = st.x[o] + vx + kk * (X0[0] - st.x[o]) * STEP * STEP;
                    double ny = st.x[o + 1] + vy + kk * (X0[1] - st.x[o + 1]) * STEP * STEP;
                    double nz = st.x[o + 2] + vz + (kk * (X0[2] - st.x[o + 2]) - g) * STEP * STEP;
                    double[] n3 = {nx - Q[0], ny - Q[1], nz - Q[2]};
                    normalize(n3, d0);
                    clampCone(n3, d0, cosA);
                    clampCone(n3, da, cosT);
                    // colision con capsulas (la punta del eslabon + el semiancho del mechon); varias pasadas porque
                    // volver a la longitud de la varilla puede devolver la punta dentro del cuerpo
                    double bx = Q[0] + n3[0] * L1, by = Q[1] + n3[1] * L1, bz = Q[2] + n3[2] * L1;   // punta antes de colisionar
                    boolean anyHit = false; double hnx = 0, hny = 0, hnz = 0;
                    for (int pass = 0; pass < 3; pass++) {
                        boolean hit = false;
                        for (int c = 0; c < C; c++) {
                            if ((m.cMask[c] & gm) == 0) continue;
                            double ex = Q[0] + n3[0] * L1, ey = Q[1] + n3[1] * L1, ez = Q[2] + n3[2] * L1;
                            double abx = cb[c * 3] - ca[c * 3], aby = cb[c * 3 + 1] - ca[c * 3 + 1], abz = cb[c * 3 + 2] - ca[c * 3 + 2];
                            double ab2 = abx * abx + aby * aby + abz * abz;
                            double t = ab2 < 1e-12 ? 0 : ((ex - ca[c * 3]) * abx + (ey - ca[c * 3 + 1]) * aby + (ez - ca[c * 3 + 2]) * abz) / ab2;
                            t = Math.max(0, Math.min(1, t));
                            double cx = ca[c * 3] + abx * t, cy = ca[c * 3 + 1] + aby * t, cz = ca[c * 3 + 2] + abz * t;
                            double dx = ex - cx, dy = ey - cy, dz = ez - cz, dl = Math.sqrt(dx * dx + dy * dy + dz * dz), r = m.cR[c] + m.sRad[s];
                            if (dl < r && dl > 1e-9) {
                                ex = cx + dx / dl * r; ey = cy + dy / dl * r; ez = cz + dz / dl * r;
                                n3[0] = ex - Q[0]; n3[1] = ey - Q[1]; n3[2] = ez - Q[2]; normalize(n3, n3); hit = true;
                                anyHit = true; hnx = dx / dl; hny = dy / dl; hnz = dz / dl;
                            }
                        }
                        for (int c = 0; c < PC; c++) {
                            if ((m.pMask[c] & gm) == 0) continue;
                            double ex = Q[0] + n3[0] * L1, ey = Q[1] + n3[1] * L1, ez = Q[2] + n3[2] * L1;
                            double dd = (ex - pp[c * 3]) * pn[c * 3] + (ey - pp[c * 3 + 1]) * pn[c * 3 + 1] + (ez - pp[c * 3 + 2]) * pn[c * 3 + 2] - m.sRad[s] * 0;
                            if (dd < 0) {
                                ex -= dd * pn[c * 3]; ey -= dd * pn[c * 3 + 1]; ez -= dd * pn[c * 3 + 2];
                                n3[0] = ex - Q[0]; n3[1] = ey - Q[1]; n3[2] = ez - Q[2]; normalize(n3, n3); hit = true;
                                anyHit = true; hnx = pn[c * 3]; hny = pn[c * 3 + 1]; hnz = pn[c * 3 + 2];
                            }
                        }
                        if (!hit) break;
                    }
                    double ax = Q[0] + n3[0] * L1, ay = Q[1] + n3[1] * L1, az = Q[2] + n3[2] * L1;
                    double pxo = st.x[o], pyo = st.x[o + 1], pzo = st.x[o + 2];
                    if (anyHit) {
                        // colision inelastica: la correccion de posicion no se convierte en velocidad y se anula la parte que entra al cuerpo
                        pxo += ax - bx; pyo += ay - by; pzo += az - bz;
                        double vx2 = ax - pxo, vy2 = ay - pyo, vz2 = az - pzo, vn = vx2 * hnx + vy2 * hny + vz2 * hnz;
                        if (vn < 0) { pxo += vn * hnx; pyo += vn * hny; pzo += vn * hnz; }
                    }
                    st.xp[o] = pxo; st.xp[o + 1] = pyo; st.xp[o + 2] = pzo;
                    st.x[o] = ax; st.x[o + 1] = ay; st.x[o + 2] = az;
                }
                double dx = st.x[o] - Q[0], dy = st.x[o + 1] - Q[1], dz = st.x[o + 2] - Q[2], dl = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (dl > 1e-9) { dir[0] = dx / dl; dir[1] = dy / dl; dir[2] = dz / dl; }
            }
            double[] rot = Rig.rotBetween(d0, dir);
            mul3(rot, 0, outR, pb * 9, outR, b * 9);
            Rig.apply(outR, b * 9, px, py, pz, tmp);
            outT[b * 3] = Q[0] - tmp[0]; outT[b * 3 + 1] = Q[1] - tmp[1]; outT[b * 3 + 2] = Q[2] - tmp[2];
        }
        if (st != null) st.acc -= ((int) (st.acc / STEP)) * STEP;
        if (st != null && steps >= 10) st.acc = 0;
    }

    private static void normalize(double[] v, double[] fallback) {
        double l = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        if (l < 1e-9) { v[0] = fallback[0]; v[1] = fallback[1]; v[2] = fallback[2]; return; }
        v[0] /= l; v[1] /= l; v[2] /= l;
    }

    /** Si v esta a mas de acos(cosMax) de ref, lo lleva al borde del cono (ambos unitarios). */
    private static void clampCone(double[] v, double[] ref, double cosMax) {
        double dot = v[0] * ref[0] + v[1] * ref[1] + v[2] * ref[2];
        if (dot >= cosMax) return;
        double ex = v[0] - dot * ref[0], ey = v[1] - dot * ref[1], ez = v[2] - dot * ref[2], el = Math.sqrt(ex * ex + ey * ey + ez * ez);
        if (el < 1e-9) return;
        double sa = Math.sqrt(Math.max(0, 1 - cosMax * cosMax));
        v[0] = cosMax * ref[0] + sa * ex / el; v[1] = cosMax * ref[1] + sa * ey / el; v[2] = cosMax * ref[2] + sa * ez / el;
    }

    private static void mul3(double[] a, int ao, double[] b, int bo, double[] out, int oo) {
        double a0 = a[ao], a1 = a[ao + 1], a2 = a[ao + 2], a3 = a[ao + 3], a4 = a[ao + 4], a5 = a[ao + 5], a6 = a[ao + 6], a7 = a[ao + 7], a8 = a[ao + 8];
        double b0 = b[bo], b1 = b[bo + 1], b2 = b[bo + 2], b3 = b[bo + 3], b4 = b[bo + 4], b5 = b[bo + 5], b6 = b[bo + 6], b7 = b[bo + 7], b8 = b[bo + 8];
        out[oo] = a0 * b0 + a1 * b3 + a2 * b6; out[oo + 1] = a0 * b1 + a1 * b4 + a2 * b7; out[oo + 2] = a0 * b2 + a1 * b5 + a2 * b8;
        out[oo + 3] = a3 * b0 + a4 * b3 + a5 * b6; out[oo + 4] = a3 * b1 + a4 * b4 + a5 * b7; out[oo + 5] = a3 * b2 + a4 * b5 + a5 * b8;
        out[oo + 6] = a6 * b0 + a7 * b3 + a8 * b6; out[oo + 7] = a6 * b1 + a7 * b4 + a8 * b7; out[oo + 8] = a6 * b2 + a7 * b5 + a8 * b8;
    }

    /** LBS con morphs: delta = 3 floats por vertice que se suman a la posicion de reposo antes de deformar (puede ser null). */
    public static void skin(Rig.Skin k, double[] R, double[] T, float[] delta, float[] outPos, float[] outNrm) {
        int n = k.count();
        for (int i = 0; i < n; i++) {
            double x = k.v[i * 8], y = k.v[i * 8 + 1], z = k.v[i * 8 + 2], nx = k.v[i * 8 + 5], ny = k.v[i * 8 + 6], nz = k.v[i * 8 + 7];
            if (delta != null) { x += delta[i * 3]; y += delta[i * 3 + 1]; z += delta[i * 3 + 2]; }
            double px = 0, py = 0, pz = 0, qx = 0, qy = 0, qz = 0;
            for (int j = 0; j < 4; j++) {
                double w = k.bw[i * 4 + j];
                if (w <= 0) continue;
                int b = k.bi[i * 4 + j], r = b * 9, t = b * 3;
                px += w * (R[r] * x + R[r + 1] * y + R[r + 2] * z + T[t]);
                py += w * (R[r + 3] * x + R[r + 4] * y + R[r + 5] * z + T[t + 1]);
                pz += w * (R[r + 6] * x + R[r + 7] * y + R[r + 8] * z + T[t + 2]);
                qx += w * (R[r] * nx + R[r + 1] * ny + R[r + 2] * nz);
                qy += w * (R[r + 3] * nx + R[r + 4] * ny + R[r + 5] * nz);
                qz += w * (R[r + 6] * nx + R[r + 7] * ny + R[r + 8] * nz);
            }
            outPos[i * 3] = (float) px; outPos[i * 3 + 1] = (float) py; outPos[i * 3 + 2] = (float) pz;
            double l = Math.sqrt(qx * qx + qy * qy + qz * qz); if (l < 1e-9) l = 1;
            outNrm[i * 3] = (float) (qx / l); outNrm[i * 3 + 1] = (float) (qy / l); outNrm[i * 3 + 2] = (float) (qz / l);
        }
    }

    /** Arma el arreglo de deltas de vertices a partir de los pesos de morph. Devuelve false si no hay ninguno activo. */
    public static boolean buildDelta(Model3 m, float[] w, float[] delta) {
        boolean any = false;
        for (int i = 0; i < w.length; i++) if (w[i] > 0.001f) { any = true; break; }
        if (!any) return false;
        java.util.Arrays.fill(delta, 0f);
        for (int i = 0; i < w.length; i++) {
            float wi = w[i]; if (wi <= 0.001f) continue;
            int[] idx = m.morphIdx[i]; float[] d = m.morphD[i];
            for (int j = 0; j < idx.length; j++) { int v = idx[j] * 3; delta[v] += wi * d[j * 3]; delta[v + 1] += wi * d[j * 3 + 1]; delta[v + 2] += wi * d[j * 3 + 2]; }
        }
        return true;
    }
}
