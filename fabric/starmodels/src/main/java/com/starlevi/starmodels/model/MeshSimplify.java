package com.starlevi.starmodels.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Baja la cantidad de triangulos de una malla sin cambiar como se ve (sin nada de Minecraft).
 * <p>
 * Simplificacion por error cuadratico (Garland-Heckbert) con colapsos "medio borde": un vertice se junta
 * sobre un vecino que se queda donde estaba, asi ningun vertice que sobrevive se mueve ni cambia su
 * textura o su normal. No se tocan las costuras de la textura ni los bordes duros (donde Roblox separa
 * normales), y los bordes abiertos (mechones de pelo, volados) solo se acortan a lo largo de si mismos.
 * Los triangulos son los del armador: v0 v1 v2, t0 t1 t2, n0 n1 n2 (indices del .obj).
 */
final class MeshSimplify {
    private MeshSimplify() {
    }

    static List<int[]> simplify(float[][] V, float[][] VT, float[][] VN, List<int[]> tris, int target) {
        int nt = tris.size();
        if (target >= nt || nt < 60) {
            return tris;
        }
        // --- nodos: vertices soldados por posicion (Roblox duplica vertices iguales). Las dos capas de algo
        // de dos caras (mechones de pelo) quedan separadas porque sus normales apuntan para lados opuestos.
        Map<String, Integer> idOf = new HashMap<>();
        Map<String, List<Integer>> atPos = new HashMap<>();
        List<float[]> pos = new ArrayList<>();
        List<float[]> nrm = new ArrayList<>();
        int[][] node = new int[nt][3];
        int[][] t = new int[nt][];
        Map<String, Integer> attrId = new HashMap<>();
        List<int[]> attrRep = new ArrayList<>();
        List<float[]> attrUv = new ArrayList<>();
        int[][] cattr = new int[nt][3];
        for (int i = 0; i < nt; i++) {
            t[i] = tris.get(i).clone();
            for (int k = 0; k < 3; k++) {
                float[] p = V[t[i][k]];
                float[] n = t[i][6 + k] >= 0 && t[i][6 + k] < VN.length ? VN[t[i][6 + k]] : new float[]{0, 0, 0};
                float[] uv = t[i][3 + k] >= 0 && t[i][3 + k] < VT.length ? VT[t[i][3 + k]] : new float[]{0, 0};
                String pk = Math.round(p[0] * 2e4) + "," + Math.round(p[1] * 2e4) + "," + Math.round(p[2] * 2e4);
                // de que lado mira (dos capas pegadas = dos nodos)
                String key = pk + "|" + hemi(n);
                Integer id = idOf.get(key);
                if (id == null) {
                    id = pos.size();
                    idOf.put(key, id);
                    pos.add(p);
                    nrm.add(n);
                    atPos.computeIfAbsent(pk, x -> new ArrayList<>()).add(id);
                }
                node[i][k] = id;
                String ak = Math.round(uv[0] * 1e5) + "," + Math.round(uv[1] * 1e5) + "|" + Math.round(n[0] * 1e3) + "," + Math.round(n[1] * 1e3) + ","
                        + Math.round(n[2] * 1e3);
                Integer ai = attrId.get(ak);
                if (ai == null) {
                    ai = attrRep.size();
                    attrId.put(ak, ai);
                    attrRep.add(new int[]{t[i][k], t[i][3 + k], t[i][6 + k]});
                    attrUv.add(uv);
                }
                cattr[i][k] = ai;
            }
        }
        int nn = pos.size();
        // atributos de cada nodo (textura y normal): si tiene mas de uno, es costura y no se mueve
        long[] attr = new long[nn];
        boolean[] seam = new boolean[nn];
        Arrays.fill(attr, Long.MIN_VALUE);
        List<Set<Integer>> faces = new ArrayList<>(nn);
        for (int i = 0; i < nn; i++) {
            faces.add(new HashSet<>(8));
        }
        boolean[] alive = new boolean[nt];
        // error en 5 dimensiones: posicion y textura (Garland-Heckbert 1998), asi no se deforma el dibujo
        float[] lo = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE}, hi = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        for (float[] p : pos) {
            for (int c = 0; c < 3; c++) {
                lo[c] = Math.min(lo[c], p[c]);
                hi[c] = Math.max(hi[c], p[c]);
            }
        }
        double diag = Math.sqrt((hi[0] - lo[0]) * (hi[0] - lo[0]) + (hi[1] - lo[1]) * (hi[1] - lo[1]) + (hi[2] - lo[2]) * (hi[2] - lo[2]));
        double uvScale = diag;
        // tope de calidad: no se acepta un error mayor a ~1% del tamaño de la pieza (en forma o en textura)
        double tol = 0.012 * diag;
        double maxCost = tol * tol * 3;
        double[][] Q = new double[nn][21];
        int[] nodeAttr = new int[nn];
        java.util.Arrays.fill(nodeAttr, -1);
        for (int i = 0; i < nt; i++) {
            if (node[i][0] == node[i][1] || node[i][1] == node[i][2] || node[i][0] == node[i][2]) {
                continue; // degenerado: queda como esta
            }
            alive[i] = true;
            for (int k = 0; k < 3; k++) {
                int a = node[i][k];
                long at = cattr[i][k];
                if (attr[a] == Long.MIN_VALUE) {
                    attr[a] = at;
                } else if (attr[a] != at) {
                    seam[a] = true;
                }
                faces.get(a).add(i);
            }
            double[][] pt = new double[3][];
            for (int k = 0; k < 3; k++) {
                float[] p = pos.get(node[i][k]);
                float[] uv = attrUv.get(cattr[i][k]);
                pt[k] = new double[]{p[0], p[1], p[2], uv[0] * uvScale, uv[1] * uvScale};
                if (nodeAttr[node[i][k]] < 0) {
                    nodeAttr[node[i][k]] = cattr[i][k];
                }
            }
            double[] fq = faceQuadric(pt[0], pt[1], pt[2]);
            if (fq != null) {
                for (int k = 0; k < 3; k++) {
                    for (int c = 0; c < 21; c++) {
                        Q[node[i][k]][c] += fq[c];
                    }
                }
            }
        }
        // bordes duros (mismo lugar, otra normal que no es la cara de atras): no se mueven
        for (List<Integer> ids : atPos.values()) {
            if (ids.size() < 2) {
                continue;
            }
            for (int x : ids) {
                for (int y : ids) {
                    float[] a = nrm.get(x), b = nrm.get(y);
                    if (x != y && a[0] * b[0] + a[1] * b[1] + a[2] * b[2] > -0.5f) {
                        seam[x] = true;
                    }
                }
            }
        }
        // bordes abiertos: un plano perpendicular que los mantiene en su lugar
        Map<Long, int[]> edgeFaces = new HashMap<>();
        for (int i = 0; i < nt; i++) {
            if (!alive[i]) {
                continue;
            }
            for (int k = 0; k < 3; k++) {
                long e = edge(node[i][k], node[i][(k + 1) % 3]);
                final int fi = i;
                int[] c = edgeFaces.computeIfAbsent(e, x -> new int[]{0, fi});
                c[0]++;
            }
        }
        int[] borderEdges = new int[nn];
        for (Map.Entry<Long, int[]> e : edgeFaces.entrySet()) {
            if (e.getValue()[0] != 1) {
                continue;
            }
            int a = (int) (e.getKey() >>> 32), b = (int) (long) e.getKey();
            borderEdges[a]++;
            borderEdges[b]++;
            int f = e.getValue()[1];
            int o = node[f][0] != a && node[f][0] != b ? node[f][0] : node[f][1] != a && node[f][1] != b ? node[f][1] : node[f][2];
            float[] pa = pos.get(a), pb = pos.get(b), po = pos.get(o);
            double[] n = cross(pa, pb, po);
            double ex = pb[0] - pa[0], ey = pb[1] - pa[1], ez = pb[2] - pa[2];
            double dx = ey * n[2] - ez * n[1], dy = ez * n[0] - ex * n[2], dz = ex * n[1] - ey * n[0];
            double l = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (l < 1e-12) {
                continue;
            }
            dx /= l;
            dy /= l;
            dz /= l;
            double d = -(dx * pa[0] + dy * pa[1] + dz * pa[2]);
            addPlane(Q[a], dx, dy, dz, d, 10);
            addPlane(Q[b], dx, dy, dz, d, 10);
        }
        // --- colapsos (b se junta sobre a), el mas barato primero
        int[] ver = new int[nn];
        boolean[] gone = new boolean[nn];
        PriorityQueue<double[]> heap = new PriorityQueue<>((x, y) -> Double.compare(x[0], y[0]));
        for (Map.Entry<Long, int[]> e : edgeFaces.entrySet()) {
            int a = (int) (e.getKey() >>> 32), b = (int) (long) e.getKey();
            push(heap, Q, pos, a, b, ver, nodeUv(nodeAttr, attrUv, a), uvScale);
            push(heap, Q, pos, b, a, ver, nodeUv(nodeAttr, attrUv, b), uvScale);
        }
        int live = 0;
        for (boolean al : alive) {
            if (al) {
                live++;
            }
        }
        while (live > target && !heap.isEmpty()) {
            double[] h = heap.poll();
            if (h[0] > maxCost) {
                break; // lo que queda ya se notaria
            }
            int b = (int) h[1], a = (int) h[2];
            if (gone[a] || gone[b] || ver[a] != (int) h[3] || ver[b] != (int) h[4]) {
                continue;
            }
            if (seam[b]) {
                continue;
            }
            int shared = 0;
            for (int f : faces.get(b)) {
                if (alive[f] && has(node[f], a)) {
                    shared++;
                }
            }
            if (shared == 0) {
                continue;
            }
            if (borderEdges[b] > 0 && !(borderEdges[b] == 2 && shared == 1)) {
                continue; // vertice de borde: solo a lo largo de su borde
            }
            // atributo que va a usar a en los triangulos que eran de b
            long useAttr;
            if (!seam[a]) {
                useAttr = attr[a];
            } else {
                long found = Long.MIN_VALUE;
                boolean ok = true;
                for (int f : faces.get(b)) {
                    if (alive[f] && has(node[f], a)) {
                        long at = cattr[f][idx(node[f], a)];
                        if (found == Long.MIN_VALUE) {
                            found = at;
                        } else if (found != at) {
                            ok = false;
                        }
                    }
                }
                if (!ok) {
                    continue;
                }
                useAttr = found;
            }
            // que no queden pliegues raros: vecinos en comun = los del borde compartido
            Set<Integer> na = neighbors(faces.get(a), node, alive, a);
            Set<Integer> nb = neighbors(faces.get(b), node, alive, b);
            int common = 0;
            for (int x : nb) {
                if (na.contains(x)) {
                    common++;
                }
            }
            if (common != shared) {
                continue;
            }
            // que ningun triangulo se de vuelta ni quede finito
            boolean bad = false;
            float[] pa = pos.get(a);
            for (int f : faces.get(b)) {
                if (!alive[f] || has(node[f], a)) {
                    continue;
                }
                float[][] o = {pos.get(node[f][0]), pos.get(node[f][1]), pos.get(node[f][2])};
                double[] n0 = cross(o[0], o[1], o[2]);
                int kb = idx(node[f], b);
                o[kb] = pa;
                double[] n1 = cross(o[0], o[1], o[2]);
                double l0 = Math.sqrt(n0[0] * n0[0] + n0[1] * n0[1] + n0[2] * n0[2]);
                double l1 = Math.sqrt(n1[0] * n1[0] + n1[1] * n1[1] + n1[2] * n1[2]);
                if (l1 < 1e-12 || (n0[0] * n1[0] + n0[1] * n1[1] + n0[2] * n1[2]) < 0.2 * l0 * l1) {
                    bad = true;
                    break;
                }
            }
            if (bad) {
                continue;
            }
            // colapsar
            int[] rp = attrRep.get((int) useAttr);
            for (int f : new ArrayList<>(faces.get(b))) {
                if (!alive[f]) {
                    continue;
                }
                if (has(node[f], a)) {
                    alive[f] = false;
                    live--;
                    for (int k = 0; k < 3; k++) {
                        faces.get(node[f][k]).remove(f);
                    }
                    continue;
                }
                int k = idx(node[f], b);
                node[f][k] = a;
                cattr[f][k] = (int) useAttr;
                t[f][k] = rp[0];
                t[f][3 + k] = rp[1];
                t[f][6 + k] = rp[2];
                faces.get(a).add(f);
            }
            faces.get(b).clear();
            gone[b] = true;
            for (int c = 0; c < 21; c++) {
                Q[a][c] += Q[b][c];
            }
            ver[a]++;
            for (int x : neighbors(faces.get(a), node, alive, a)) {
                push(heap, Q, pos, x, a, ver, nodeUv(nodeAttr, attrUv, a), uvScale);
                push(heap, Q, pos, a, x, ver, nodeUv(nodeAttr, attrUv, x), uvScale);
            }
        }
        List<int[]> out = new ArrayList<>(live);
        for (int i = 0; i < nt; i++) {
            if (alive[i] || node[i][0] == node[i][1] || node[i][1] == node[i][2] || node[i][0] == node[i][2]) {
                if (alive[i]) {
                    out.add(t[i]);
                }
            }
        }
        return out;
    }

    /** Para que lado mira una normal (grueso: solo separa caras opuestas). */
    private static int hemi(float[] n) {
        float ax = Math.abs(n[0]), ay = Math.abs(n[1]), az = Math.abs(n[2]);
        if (ax >= ay && ax >= az) {
            return n[0] >= 0 ? 0 : 1;
        }
        if (ay >= az) {
            return n[1] >= 0 ? 2 : 3;
        }
        return n[2] >= 0 ? 4 : 5;
    }

    private static float[] nodeUv(int[] nodeAttr, List<float[]> attrUv, int n) {
        return nodeAttr[n] < 0 ? new float[]{0, 0} : attrUv.get(nodeAttr[n]);
    }

    /** Indice de (i, j) en la matriz simetrica de 5x5 guardada como triangulo de arriba (15) + vector (5) + constante. */
    private static int ix(int i, int j) {
        if (i > j) {
            int x = i;
            i = j;
            j = x;
        }
        return i * 5 - i * (i - 1) / 2 + (j - i);
    }

    private static void push(PriorityQueue<double[]> heap, double[][] Q, List<float[]> pos, int b, int a, int[] ver, float[] uvA, double uvScale) {
        float[] p = pos.get(a);
        double[] v = {p[0], p[1], p[2], uvA[0] * uvScale, uvA[1] * uvScale};
        double[] qa = Q[a], qb = Q[b];
        double cost = 0;
        for (int i = 0; i < 5; i++) {
            for (int j = 0; j < 5; j++) {
                int k = ix(i, j);
                cost += (qa[k] + qb[k]) * v[i] * v[j];
            }
            cost += 2 * (qa[15 + i] + qb[15 + i]) * v[i];
        }
        cost += qa[20] + qb[20];
        heap.add(new double[]{Math.max(0, cost), b, a, ver[a], ver[b]});
    }

    /** Cuadrica de un triangulo en 5D (posicion + textura): distancia al cuadrado a su plano. */
    private static double[] faceQuadric(double[] p, double[] q, double[] r) {
        double[] e1 = new double[5], e2 = new double[5];
        double l1 = 0;
        for (int i = 0; i < 5; i++) {
            e1[i] = q[i] - p[i];
            l1 += e1[i] * e1[i];
        }
        l1 = Math.sqrt(l1);
        if (l1 < 1e-12) {
            return null;
        }
        double d = 0;
        for (int i = 0; i < 5; i++) {
            e1[i] /= l1;
            d += (r[i] - p[i]) * e1[i];
        }
        double l2 = 0;
        for (int i = 0; i < 5; i++) {
            e2[i] = r[i] - p[i] - d * e1[i];
            l2 += e2[i] * e2[i];
        }
        l2 = Math.sqrt(l2);
        if (l2 < 1e-12) {
            return null;
        }
        for (int i = 0; i < 5; i++) {
            e2[i] /= l2;
        }
        double pe1 = 0, pe2 = 0, pp = 0;
        for (int i = 0; i < 5; i++) {
            pe1 += p[i] * e1[i];
            pe2 += p[i] * e2[i];
            pp += p[i] * p[i];
        }
        double[] out = new double[21];
        for (int i = 0; i < 5; i++) {
            for (int j = i; j < 5; j++) {
                out[ix(i, j)] = (i == j ? 1 : 0) - e1[i] * e1[j] - e2[i] * e2[j];
            }
            out[15 + i] = pe1 * e1[i] + pe2 * e2[i] - p[i];
        }
        out[20] = pp - pe1 * pe1 - pe2 * pe2;
        return out;
    }

    /** Plano solo de posicion (bordes abiertos), metido en la cuadrica de 5D. */
    private static void addPlane(double[] q, double a, double b, double c, double d, double w) {
        double[] n = {a, b, c};
        for (int i = 0; i < 3; i++) {
            for (int j = i; j < 3; j++) {
                q[ix(i, j)] += w * n[i] * n[j];
            }
            q[15 + i] += w * d * n[i];
        }
        q[20] += w * d * d;
    }

    private static Set<Integer> neighbors(Set<Integer> fs, int[][] node, boolean[] alive, int self) {
        Set<Integer> s = new HashSet<>();
        for (int f : fs) {
            if (alive[f]) {
                for (int k = 0; k < 3; k++) {
                    if (node[f][k] != self) {
                        s.add(node[f][k]);
                    }
                }
            }
        }
        return s;
    }

    private static boolean has(int[] n, int a) {
        return n[0] == a || n[1] == a || n[2] == a;
    }

    private static int idx(int[] n, int a) {
        return n[0] == a ? 0 : n[1] == a ? 1 : 2;
    }

    private static long edge(int a, int b) {
        return a < b ? ((long) a << 32) | (b & 0xFFFFFFFFL) : ((long) b << 32) | (a & 0xFFFFFFFFL);
    }

    private static double[] cross(float[] a, float[] b, float[] c) {
        double ux = b[0] - a[0], uy = b[1] - a[1], uz = b[2] - a[2];
        double vx = c[0] - a[0], vy = c[1] - a[1], vz = c[2] - a[2];
        return new double[]{uy * vz - uz * vy, uz * vx - ux * vz, ux * vy - uy * vx};
    }
}
