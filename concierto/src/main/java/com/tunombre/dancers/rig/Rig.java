package com.tunombre.dancers.rig;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Nucleo matematico (sin dependencias de Minecraft): lee un modelo "dancers", una animacion de Epic Fight y calcula
 * la pose de cada hueso del modelo. Espacio de trabajo = espacio de reposo de Epic Fight (X derecha, Y frente, Z arriba).
 *
 * Retargeting: de cada articulacion de Epic Fight se toma el giro respecto a su reposo (en el mundo) y se aplica al
 * hueso del modelo alrededor de SU propio pivote, asi las proporciones del modelo no importan.
 * Los huesos "resorte" (pelo, coletas) se simulan con un pendulo con rigidez hacia su posicion de reposo.
 */
public final class Rig {
    private Rig() {}

    // ------------------------------------------------------------------ matematicas 3x3 (fila-mayor) y cuaterniones
    static void mul3(double[] a, int ao, double[] b, int bo, double[] out, int oo) {
        double a0 = a[ao], a1 = a[ao + 1], a2 = a[ao + 2], a3 = a[ao + 3], a4 = a[ao + 4], a5 = a[ao + 5], a6 = a[ao + 6], a7 = a[ao + 7], a8 = a[ao + 8];
        double b0 = b[bo], b1 = b[bo + 1], b2 = b[bo + 2], b3 = b[bo + 3], b4 = b[bo + 4], b5 = b[bo + 5], b6 = b[bo + 6], b7 = b[bo + 7], b8 = b[bo + 8];
        out[oo] = a0 * b0 + a1 * b3 + a2 * b6;      out[oo + 1] = a0 * b1 + a1 * b4 + a2 * b7;      out[oo + 2] = a0 * b2 + a1 * b5 + a2 * b8;
        out[oo + 3] = a3 * b0 + a4 * b3 + a5 * b6;  out[oo + 4] = a3 * b1 + a4 * b4 + a5 * b7;      out[oo + 5] = a3 * b2 + a4 * b5 + a5 * b8;
        out[oo + 6] = a6 * b0 + a7 * b3 + a8 * b6;  out[oo + 7] = a6 * b1 + a7 * b4 + a8 * b7;      out[oo + 8] = a6 * b2 + a7 * b5 + a8 * b8;
    }

    /** out = a * b^T */
    static void mul3T(double[] a, int ao, double[] b, int bo, double[] out, int oo) {
        double[] bt = new double[9];
        for (int i = 0; i < 3; i++) for (int j = 0; j < 3; j++) bt[i * 3 + j] = b[bo + j * 3 + i];
        mul3(a, ao, bt, 0, out, oo);
    }

    static void apply(double[] R, int ro, double x, double y, double z, double[] out) {
        out[0] = R[ro] * x + R[ro + 1] * y + R[ro + 2] * z;
        out[1] = R[ro + 3] * x + R[ro + 4] * y + R[ro + 5] * z;
        out[2] = R[ro + 6] * x + R[ro + 7] * y + R[ro + 8] * z;
    }

    public static double[] quatToMat(double x, double y, double z, double w) {
        return new double[]{1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w),
                2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w),
                2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)};
    }

    static double[] matToQuat(double m00, double m01, double m02, double m10, double m11, double m12, double m20, double m21, double m22) {
        double t = m00 + m11 + m22;
        double[] q = new double[4];
        if (t > 0) {
            double s = Math.sqrt(t + 1) * 2;
            q[0] = (m21 - m12) / s; q[1] = (m02 - m20) / s; q[2] = (m10 - m01) / s; q[3] = 0.25 * s;
        } else if (m00 >= m11 && m00 >= m22) {
            double s = Math.sqrt(m00 - m11 - m22 + 1) * 2;
            q[0] = 0.25 * s; q[1] = (m10 + m01) / s; q[2] = (m20 + m02) / s; q[3] = (m21 - m12) / s;
        } else if (m11 >= m22) {
            double s = Math.sqrt(m11 - m00 - m22 + 1) * 2;
            q[0] = (m01 + m10) / s; q[1] = 0.25 * s; q[2] = (m21 + m12) / s; q[3] = (m02 - m20) / s;
        } else {
            double s = Math.sqrt(m22 - m00 - m11 + 1) * 2;
            q[0] = (m02 + m20) / s; q[1] = (m12 + m21) / s; q[2] = 0.25 * s; q[3] = (m10 - m01) / s;
        }
        double n = Math.sqrt(q[0] * q[0] + q[1] * q[1] + q[2] * q[2] + q[3] * q[3]);
        for (int i = 0; i < 4; i++) q[i] /= n;
        return q;
    }

    static void slerp(float[] a, int ao, float[] b, int bo, double t, double[] out) {
        double ax = a[ao], ay = a[ao + 1], az = a[ao + 2], aw = a[ao + 3];
        double bx = b[bo], by = b[bo + 1], bz = b[bo + 2], bw = b[bo + 3];
        double d = ax * bx + ay * by + az * bz + aw * bw;
        if (d < 0) { bx = -bx; by = -by; bz = -bz; bw = -bw; d = -d; }
        double k0, k1;
        if (d > 0.9995) { k0 = 1 - t; k1 = t; }
        else { double th = Math.acos(d), s = Math.sin(th); k0 = Math.sin((1 - t) * th) / s; k1 = Math.sin(t * th) / s; }
        double x = k0 * ax + k1 * bx, y = k0 * ay + k1 * by, z = k0 * az + k1 * bz, w = k0 * aw + k1 * bw;
        double n = Math.sqrt(x * x + y * y + z * z + w * w);
        out[0] = x / n; out[1] = y / n; out[2] = z / n; out[3] = w / n;
    }

    // ------------------------------------------------------------------ esqueleto de Epic Fight (solo el reposo)
    public static final class Skeleton {
        public String[] names;
        public int[] parent;
        public double[] restRot;     // 9 por articulacion: rotacion global en reposo
        public double[] restLocalRot;
        public double[] restRootT = new double[3];
        public Map<String, Integer> index = new HashMap<>();

        public static Skeleton load(JsonObject root) {
            Skeleton s = new Skeleton();
            List<String> names = new ArrayList<>(); List<Integer> par = new ArrayList<>(); List<double[]> loc = new ArrayList<>();
            for (JsonElement h : root.getAsJsonArray("hierarchy")) walk(h.getAsJsonObject(), -1, names, par, loc);
            int n = names.size();
            s.names = names.toArray(new String[0]);
            s.parent = new int[n];
            s.restRot = new double[n * 9];
            s.restLocalRot = new double[n * 9];
            for (int i = 0; i < n; i++) {
                s.parent[i] = par.get(i); s.index.put(s.names[i], i);
                double[] m = loc.get(i);
                double[] r = {m[0], m[1], m[2], m[4], m[5], m[6], m[8], m[9], m[10]};
                System.arraycopy(r, 0, s.restLocalRot, i * 9, 9);
                if (s.parent[i] < 0) { System.arraycopy(r, 0, s.restRot, i * 9, 9); if (i == 0) { s.restRootT = new double[]{m[3], m[7], m[11]}; } }
                else mul3(s.restRot, s.parent[i] * 9, r, 0, s.restRot, i * 9);
            }
            return s;
        }

        private static void walk(JsonObject o, int parent, List<String> names, List<Integer> par, List<double[]> loc) {
            int me = names.size();
            names.add(o.get("name").getAsString()); par.add(parent);
            JsonArray t = o.getAsJsonArray("transform"); double[] m = new double[16];
            for (int i = 0; i < 16; i++) m[i] = t.get(i).getAsDouble();
            loc.add(m);
            for (JsonElement c : o.getAsJsonArray("children")) walk(c.getAsJsonObject(), me, names, par, loc);
        }
    }

    // ------------------------------------------------------------------ animacion de Epic Fight (JSON {"animation":[{name,time[],transform[[16]..]}]})
    public static final class Anim {
        public float duration;
        float[][] times;   // por articulacion
        float[][] trs;     // por articulacion: 7 floats por fotograma (qx qy qz qw tx ty tz)

        public static Anim load(InputStream in, Skeleton sk) throws IOException {
            Anim a = new Anim();
            int n = sk.names.length;
            a.times = new float[n][]; a.trs = new float[n][];
            try (JsonReader r = new JsonReader(new java.io.BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8), 1 << 16))) {
                r.beginObject();
                while (r.hasNext()) {
                    if (!r.nextName().equals("animation")) { r.skipValue(); continue; }
                    r.beginArray();
                    while (r.hasNext()) {
                        r.beginObject();
                        String name = null; float[] time = null; float[] data = null;
                        while (r.hasNext()) {
                            String k = r.nextName();
                            if (k.equals("name")) name = r.nextString();
                            else if (k.equals("time")) {
                                float[] buf = new float[256]; int c = 0;
                                r.beginArray();
                                while (r.hasNext()) { if (c == buf.length) buf = Arrays.copyOf(buf, c * 2); buf[c++] = (float) r.nextDouble(); }
                                r.endArray(); time = Arrays.copyOf(buf, c);
                            } else if (k.equals("transform")) {
                                float[] buf = new float[7 * 256]; int c = 0;
                                r.beginArray();
                                double[] m = new double[16];
                                while (r.hasNext()) {
                                    r.beginArray(); for (int i = 0; i < 16; i++) m[i] = r.nextDouble(); r.endArray();
                                    if (c + 7 > buf.length) buf = Arrays.copyOf(buf, buf.length * 2);
                                    double[] q = matToQuat(m[0], m[1], m[2], m[4], m[5], m[6], m[8], m[9], m[10]);
                                    buf[c++] = (float) q[0]; buf[c++] = (float) q[1]; buf[c++] = (float) q[2]; buf[c++] = (float) q[3];
                                    buf[c++] = (float) m[3]; buf[c++] = (float) m[7]; buf[c++] = (float) m[11];
                                }
                                r.endArray(); data = Arrays.copyOf(buf, c);
                            } else r.skipValue();
                        }
                        r.endObject();
                        Integer idx = name == null ? null : sk.index.get(name);
                        if (idx != null && time != null && data != null) { a.times[idx] = time; a.trs[idx] = data; a.duration = Math.max(a.duration, time[time.length - 1]); }
                    }
                    r.endArray();
                }
                r.endObject();
            }
            return a;
        }

        /** Muestrea la articulacion j en el instante t: q[4], t[3]. Devuelve false si no hay datos. */
        boolean sample(int j, double t, double[] q, double[] tr) {
            float[] ts = this.times[j]; float[] d = this.trs[j];
            if (ts == null || ts.length == 0) return false;
            int lo = 0, hi = ts.length - 1;
            if (t <= ts[0]) hi = lo = 0;
            else if (t >= ts[hi]) lo = hi;
            else { while (hi - lo > 1) { int mid = (lo + hi) >>> 1; if (ts[mid] <= t) lo = mid; else hi = mid; } }
            int i1 = lo, i2 = Math.min(lo + 1, ts.length - 1);
            double f = i2 == i1 ? 0 : Math.min(1, Math.max(0, (t - ts[i1]) / (ts[i2] - ts[i1])));
            slerp(d, i1 * 7, d, i2 * 7, f, q);
            for (int k = 0; k < 3; k++) tr[k] = d[i1 * 7 + 4 + k] * (1 - f) + d[i2 * 7 + 4 + k] * f;
            return true;
        }
    }

    // ------------------------------------------------------------------ modelo
    /** Malla con pesos: 8 floats por vertice (x y z u v nx ny nz), 4 huesos y 4 pesos por vertice, triangulos por grupo de textura. */
    public static final class Skin {
        public float[] v; public int[] bi; public float[] bw; public int[] tris; public int[] groupTex, groupStart, groupCount;
        public int count() { return v.length / 8; }
    }

    public static final class Model {
        public String name;
        public String[] textures;
        public String[] jointNames;     // huesos 0..J-1
        public int[] jointEf;           // indice de la articulacion de Epic Fight
        public int[] jointParent;       // padre (indice de hueso de modelo) o -1
        public double[] jointPivot;     // 3 por articulacion
        public String[] springNames;
        public int[] springParent;      // indice de hueso (articulacion o resorte)
        public double[] springPivot, springTip, springLen, springParams;   // params: rigidez, amortiguacion, gravedad, angulo max (rad)
        public double hipHeight;
        public Part[] parts;
        public Skin skin;   // malla con pesos (formato 2), puede ser null
        /** Para el cliente: cache de ResourceLocation de las texturas. */
        public Object userData;
        public int boneCount() { return jointNames.length + springNames.length; }

        public static final class Part { public int bone, tex; public float[] v; /* 8 floats por vertice, 4 vertices por cara */ }

        public static Model load(JsonObject o, Skeleton sk) {
            Model m = new Model();
            m.name = o.get("name").getAsString();
            JsonArray tx = o.getAsJsonArray("textures"); m.textures = new String[tx.size()];
            for (int i = 0; i < tx.size(); i++) m.textures[i] = tx.get(i).getAsString();
            m.hipHeight = o.get("hip_height").getAsDouble();
            JsonObject js = o.getAsJsonObject("joints");
            List<String> jn = new ArrayList<>(js.keySet());
            // ordenar para que el padre vaya antes que el hijo (segun la jerarquia de Epic Fight)
            List<String> sorted = new ArrayList<>();
            for (String efn : sk.names) for (String n : jn) { String ef = js.getAsJsonObject(n).has("ef") ? js.getAsJsonObject(n).get("ef").getAsString() : n; if (ef.equals(efn)) sorted.add(n); }
            int J = sorted.size();
            m.jointNames = sorted.toArray(new String[0]); m.jointEf = new int[J]; m.jointParent = new int[J]; m.jointPivot = new double[J * 3];
            Map<Integer, Integer> efToJoint = new HashMap<>();
            for (int i = 0; i < J; i++) {
                JsonObject jo = js.getAsJsonObject(m.jointNames[i]);
                String ef = jo.has("ef") ? jo.get("ef").getAsString() : m.jointNames[i];
                m.jointEf[i] = sk.index.get(ef); efToJoint.put(m.jointEf[i], i);
                JsonArray pv = jo.getAsJsonArray("pivot");
                for (int k = 0; k < 3; k++) m.jointPivot[i * 3 + k] = pv.get(k).getAsDouble();
            }
            for (int i = 0; i < J; i++) {
                int p = sk.parent[m.jointEf[i]];
                while (p >= 0 && !efToJoint.containsKey(p)) p = sk.parent[p];
                m.jointParent[i] = p < 0 ? -1 : efToJoint.get(p);
            }
            JsonObject sp = o.has("springs") ? o.getAsJsonObject("springs") : new JsonObject();
            // orden: un resorte despues de su padre
            List<String> sn = new ArrayList<>(); List<String> pending = new ArrayList<>(sp.keySet());
            Map<String, Integer> boneIdx = new HashMap<>(); for (int i = 0; i < J; i++) boneIdx.put(m.jointNames[i], i);
            int guard = 0;
            while (!pending.isEmpty() && guard++ < 1000) {
                for (String n : new ArrayList<>(pending)) {
                    String par = sp.getAsJsonObject(n).get("parent").getAsString();
                    if (boneIdx.containsKey(par)) { boneIdx.put(n, J + sn.size()); sn.add(n); pending.remove(n); }
                }
            }
            int S = sn.size();
            m.springNames = sn.toArray(new String[0]); m.springParent = new int[S];
            m.springPivot = new double[S * 3]; m.springTip = new double[S * 3]; m.springLen = new double[S]; m.springParams = new double[S * 4];
            for (int i = 0; i < S; i++) {
                JsonObject so = sp.getAsJsonObject(m.springNames[i]);
                m.springParent[i] = boneIdx.get(so.get("parent").getAsString());
                double len2 = 0;
                for (int k = 0; k < 3; k++) {
                    m.springPivot[i * 3 + k] = so.getAsJsonArray("pivot").get(k).getAsDouble();
                    m.springTip[i * 3 + k] = so.getAsJsonArray("tip").get(k).getAsDouble();
                    double d = m.springTip[i * 3 + k] - m.springPivot[i * 3 + k]; len2 += d * d;
                }
                m.springLen[i] = Math.sqrt(len2);
                m.springParams[i * 4] = so.has("stiffness") ? so.get("stiffness").getAsDouble() : 150;
                m.springParams[i * 4 + 1] = so.has("damping") ? so.get("damping").getAsDouble() : 4;
                m.springParams[i * 4 + 2] = so.has("gravity") ? so.get("gravity").getAsDouble() : 9.8;
                m.springParams[i * 4 + 3] = Math.toRadians(so.has("max_angle") ? so.get("max_angle").getAsDouble() : 70);
            }
            List<Part> parts = new ArrayList<>();
            for (JsonElement pe : o.getAsJsonArray("parts")) {
                JsonObject po = pe.getAsJsonObject();
                Integer bone = boneIdx.get(po.get("bone").getAsString());
                if (bone == null) continue;
                Map<Integer, List<float[]>> byTex = new HashMap<>();
                for (JsonElement qe : po.getAsJsonArray("quads")) {
                    JsonObject qo = qe.getAsJsonObject();
                    JsonArray va = qo.getAsJsonArray("v"); float[] v = new float[va.size()];
                    for (int i = 0; i < v.length; i++) v[i] = va.get(i).getAsFloat();
                    byTex.computeIfAbsent(qo.get("t").getAsInt(), k -> new ArrayList<>()).add(v);
                }
                for (Map.Entry<Integer, List<float[]>> e : byTex.entrySet()) {
                    Part p = new Part(); p.bone = bone; p.tex = e.getKey();
                    p.v = new float[e.getValue().size() * 32]; int c = 0;
                    for (float[] q : e.getValue()) { System.arraycopy(q, 0, p.v, c, 32); c += 32; }
                    parts.add(p);
                }
            }
            m.parts = parts.toArray(new Part[0]);
            if (o.has("skin")) {
                JsonObject so = o.getAsJsonObject("skin");
                Skin k = new Skin();
                JsonArray names = so.getAsJsonArray("bones"); int[] map = new int[names.size()];
                for (int i = 0; i < map.length; i++) { Integer bi = boneIdx.get(names.get(i).getAsString()); map[i] = bi == null ? 0 : bi; }
                JsonArray va = so.getAsJsonArray("v"); k.v = new float[va.size()]; for (int i = 0; i < k.v.length; i++) k.v[i] = va.get(i).getAsFloat();
                JsonArray ba = so.getAsJsonArray("bi"); k.bi = new int[ba.size()]; for (int i = 0; i < k.bi.length; i++) k.bi[i] = map[ba.get(i).getAsInt()];
                JsonArray wa = so.getAsJsonArray("bw"); k.bw = new float[wa.size()]; for (int i = 0; i < k.bw.length; i++) k.bw[i] = wa.get(i).getAsFloat();
                JsonArray ta = so.getAsJsonArray("tris"); k.tris = new int[ta.size()]; for (int i = 0; i < k.tris.length; i++) k.tris[i] = ta.get(i).getAsInt();
                JsonArray ga = so.getAsJsonArray("groups"); int g = ga.size();
                k.groupTex = new int[g]; k.groupStart = new int[g]; k.groupCount = new int[g];
                for (int i = 0; i < g; i++) { JsonObject go = ga.get(i).getAsJsonObject(); k.groupTex[i] = go.get("tex").getAsInt(); k.groupStart[i] = go.get("start").getAsInt(); k.groupCount[i] = go.get("count").getAsInt(); }
                m.skin = k;
            }
            return m;
        }
    }

    // ------------------------------------------------------------------ estado de los resortes (uno por entidad)
    public static final class SpringState {
        double[] x, xp; boolean[] init; double acc;
        public SpringState(Model m) { int s = m.springNames.length; x = new double[s * 3]; xp = new double[s * 3]; init = new boolean[s]; }
        public void reset() { Arrays.fill(init, false); acc = 0; }
    }

    private static final double STEP = 1.0 / 120.0;

    /**
     * Calcula la pose.
     * @param anim animacion (o null = reposo)
     * @param time segundos desde el inicio (ya con bucle aplicado)
     * @param travel factor del recorrido horizontal de la raiz (0 = en el sitio)
     * @param dt segundos desde la pasada anterior (para los resortes); st puede ser null (sin resortes)
     * @param outR 9 por hueso, outT 3 por hueso: p' = R p + t, en espacio de reposo de Epic Fight
     */
    public static void pose(Model m, Skeleton sk, Anim anim, double time, double travel, SpringState st, double dt, double[] outR, double[] outT) {
        int n = sk.names.length;
        double[] rg = new double[n * 9]; double[] rl = new double[9]; double[] q = new double[4], tr = new double[3];
        double[] troot = new double[3];
        boolean animated = anim != null;
        for (int i = 0; i < n; i++) {
            if (animated && anim.sample(i, time, q, tr)) {
                double[] r = quatToMat(q[0], q[1], q[2], q[3]); System.arraycopy(r, 0, rl, 0, 9);
                if (i == 0) { troot[0] = tr[0] - sk.restRootT[0]; troot[1] = tr[1] - sk.restRootT[1]; troot[2] = tr[2] - sk.restRootT[2]; }
            } else System.arraycopy(sk.restLocalRot, i * 9, rl, 0, 9);
            if (sk.parent[i] < 0) System.arraycopy(rl, 0, rg, i * 9, 9); else mul3(rg, sk.parent[i] * 9, rl, 0, rg, i * 9);
        }
        double k = m.hipHeight / sk.restRootT[2];
        int J = m.jointNames.length;
        double[] posed = new double[J * 3]; double[] tmp = new double[3];
        double[] rrel = new double[J * 9];
        for (int j = 0; j < J; j++) {
            mul3T(rg, m.jointEf[j] * 9, sk.restRot, m.jointEf[j] * 9, rrel, j * 9);
            double px = m.jointPivot[j * 3], py = m.jointPivot[j * 3 + 1], pz = m.jointPivot[j * 3 + 2];
            int p = m.jointParent[j];
            if (p < 0) { posed[j * 3] = px + troot[0] * k * travel; posed[j * 3 + 1] = py + troot[1] * k * travel; posed[j * 3 + 2] = pz + troot[2] * k; }
            else {
                apply(rrel, p * 9, px - m.jointPivot[p * 3], py - m.jointPivot[p * 3 + 1], pz - m.jointPivot[p * 3 + 2], tmp);
                posed[j * 3] = posed[p * 3] + tmp[0]; posed[j * 3 + 1] = posed[p * 3 + 1] + tmp[1]; posed[j * 3 + 2] = posed[p * 3 + 2] + tmp[2];
            }
            System.arraycopy(rrel, j * 9, outR, j * 9, 9);
            apply(outR, j * 9, px, py, pz, tmp);
            outT[j * 3] = posed[j * 3] - tmp[0]; outT[j * 3 + 1] = posed[j * 3 + 1] - tmp[1]; outT[j * 3 + 2] = posed[j * 3 + 2] - tmp[2];
        }
        int S = m.springNames.length;
        if (S == 0) return;
        if (st != null) { st.acc += Math.min(Math.max(dt, 0), 0.1); }
        double[] Q = new double[3], d0 = new double[3], X0 = new double[3], rs = new double[9];
        for (int s = 0; s < S; s++) {
            int b = J + s, pb = m.springParent[s];
            double px = m.springPivot[s * 3], py = m.springPivot[s * 3 + 1], pz = m.springPivot[s * 3 + 2];
            apply(outR, pb * 9, px, py, pz, Q);
            Q[0] += outT[pb * 3]; Q[1] += outT[pb * 3 + 1]; Q[2] += outT[pb * 3 + 2];
            double L = m.springLen[s];
            apply(outR, pb * 9, (m.springTip[s * 3] - px) / L, (m.springTip[s * 3 + 1] - py) / L, (m.springTip[s * 3 + 2] - pz) / L, d0);
            for (int c = 0; c < 3; c++) X0[c] = Q[c] + L * d0[c];
            double[] dir = {d0[0], d0[1], d0[2]};
            if (st != null) {
                if (!st.init[s] || dist2(st.x, s * 3, X0) > 9 * L * L) { for (int c = 0; c < 3; c++) { st.x[s * 3 + c] = X0[c]; st.xp[s * 3 + c] = X0[c]; } st.init[s] = true; }
            }
            if (st != null) {
                double kk = m.springParams[s * 4], cc = m.springParams[s * 4 + 1], g = m.springParams[s * 4 + 2], maxA = m.springParams[s * 4 + 3];
                double acc = st.acc;   // todos los resortes comparten el acumulador; se consume al final
                int steps = (int) (acc / STEP);
                for (int it = 0; it < steps; it++) {
                    double[] x = st.x, xp = st.xp; int o = s * 3;
                    double damp = Math.max(0, 1 - cc * STEP);
                    double nx = x[o] + (x[o] - xp[o]) * damp + (kk * (X0[0] - x[o])) * STEP * STEP;
                    double ny = x[o + 1] + (x[o + 1] - xp[o + 1]) * damp + (kk * (X0[1] - x[o + 1])) * STEP * STEP;
                    double nz = x[o + 2] + (x[o + 2] - xp[o + 2]) * damp + (kk * (X0[2] - x[o + 2]) - g) * STEP * STEP;
                    // varilla de largo fijo
                    double dx = nx - Q[0], dy = ny - Q[1], dz = nz - Q[2], dl = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (dl < 1e-9) { dx = d0[0]; dy = d0[1]; dz = d0[2]; dl = 1; }
                    dx /= dl; dy /= dl; dz /= dl;
                    // limite de angulo respecto al reposo
                    double dot = dx * d0[0] + dy * d0[1] + dz * d0[2];
                    if (dot < Math.cos(maxA)) {
                        double ex = dx - dot * d0[0], ey = dy - dot * d0[1], ez = dz - dot * d0[2], el = Math.sqrt(ex * ex + ey * ey + ez * ez);
                        if (el > 1e-9) { double ca = Math.cos(maxA), sa = Math.sin(maxA); dx = ca * d0[0] + sa * ex / el; dy = ca * d0[1] + sa * ey / el; dz = ca * d0[2] + sa * ez / el; }
                    }
                    xp[o] = x[o]; xp[o + 1] = x[o + 1]; xp[o + 2] = x[o + 2];
                    x[o] = Q[0] + dx * L; x[o + 1] = Q[1] + dy * L; x[o + 2] = Q[2] + dz * L;
                }
                double dx = st.x[s * 3] - Q[0], dy = st.x[s * 3 + 1] - Q[1], dz = st.x[s * 3 + 2] - Q[2], dl = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (dl > 1e-9) { dir[0] = dx / dl; dir[1] = dy / dl; dir[2] = dz / dl; }
            }
            // rotacion que lleva d0 -> dir
            double[] rot = rotBetween(d0, dir);
            mul3(rot, 0, outR, pb * 9, outR, b * 9);
            apply(outR, b * 9, px, py, pz, tmp);
            outT[b * 3] = Q[0] - tmp[0]; outT[b * 3 + 1] = Q[1] - tmp[1]; outT[b * 3 + 2] = Q[2] - tmp[2];
        }
        if (st != null) st.acc -= ((int) (st.acc / STEP)) * STEP;
    }

    /** Deforma la malla con pesos: outPos (3 por vertice) y outNrm (3 por vertice), en espacio de Epic Fight. */
    public static void skin(Skin k, double[] R, double[] T, float[] outPos, float[] outNrm) {
        int n = k.count();
        for (int i = 0; i < n; i++) {
            double x = k.v[i * 8], y = k.v[i * 8 + 1], z = k.v[i * 8 + 2], nx = k.v[i * 8 + 5], ny = k.v[i * 8 + 6], nz = k.v[i * 8 + 7];
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

    private static double dist2(double[] a, int ao, double[] b) {
        double x = a[ao] - b[0], y = a[ao + 1] - b[1], z = a[ao + 2] - b[2]; return x * x + y * y + z * z;
    }

    /** Rotacion minima que lleva el vector unitario a hacia b (Rodrigues). */
    static double[] rotBetween(double[] a, double[] b) {
        double cx = a[1] * b[2] - a[2] * b[1], cy = a[2] * b[0] - a[0] * b[2], cz = a[0] * b[1] - a[1] * b[0];
        double dot = a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
        if (dot > 0.999999) return new double[]{1, 0, 0, 0, 1, 0, 0, 0, 1};
        if (dot < -0.999999) {   // opuestos: 180 grados sobre un eje perpendicular
            double[] ax = Math.abs(a[0]) < 0.9 ? new double[]{1, 0, 0} : new double[]{0, 1, 0};
            double ux = a[1] * ax[2] - a[2] * ax[1], uy = a[2] * ax[0] - a[0] * ax[2], uz = a[0] * ax[1] - a[1] * ax[0];
            double l = Math.sqrt(ux * ux + uy * uy + uz * uz); ux /= l; uy /= l; uz /= l;
            return new double[]{2 * ux * ux - 1, 2 * ux * uy, 2 * ux * uz, 2 * ux * uy, 2 * uy * uy - 1, 2 * uy * uz, 2 * ux * uz, 2 * uy * uz, 2 * uz * uz - 1};
        }
        double kf = 1 / (1 + dot);
        return new double[]{cx * cx * kf + dot, cx * cy * kf - cz, cx * cz * kf + cy,
                cy * cx * kf + cz, cy * cy * kf + dot, cy * cz * kf - cx,
                cz * cx * kf - cy, cz * cy * kf + cx, cz * cz * kf + dot};
    }
}
