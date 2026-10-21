package com.tunombre.jefes.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Modelo de un jefe: huesos (cubos con jerarquia), sus caras ya calculadas y las animaciones propias del modelo.
 * Se carga desde el JSON que escribe tools/jefes/cpm2jefe.py. Unidades: pixeles (1/16 de bloque), eje Y hacia abajo
 * (igual que los modelos de Minecraft); el frente del modelo mira a -Z.
 */
public final class Model {
    public final String id, name;
    public final int n;
    /** id de cubo (el del archivo) -> indice */
    public final Map<Integer, Integer> index = new HashMap<>();
    public final int[] ids, parent;           // parent = indice del padre o -1 (raiz)
    public final int[] rootId;                // para los de raiz: id de la raiz del jugador (1 = cuerpo)
    public final float[] bpos, brot;          // posicion y rotacion base (n*3)
    public final float[][] quads;             // por cubo: 20 floats por cara (4 x x,y,z,u,v)
    public final Map<Integer, float[]> roots = new HashMap<>();
    public final float ground, top;
    public final List<Clip> clips = new ArrayList<>();
    private final int[][] kids;

    private Model(JsonObject j) {
        id = j.get("id").getAsString();
        name = j.get("name").getAsString();
        ground = j.get("ground").getAsFloat();
        top = j.get("top").getAsFloat();
        for (Map.Entry<String, JsonElement> e : j.getAsJsonObject("roots").entrySet()) {
            JsonArray a = e.getValue().getAsJsonArray();
            roots.put(Integer.parseInt(e.getKey()), new float[]{a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat()});
        }
        JsonArray cs = j.getAsJsonArray("cubes");
        n = cs.size();
        ids = new int[n]; parent = new int[n]; rootId = new int[n];
        bpos = new float[n * 3]; brot = new float[n * 3]; quads = new float[n][];
        int[] pid = new int[n];
        for (int i = 0; i < n; i++) {
            JsonObject c = cs.get(i).getAsJsonObject();
            ids[i] = c.get("id").getAsInt();
            index.put(ids[i], i);
            pid[i] = c.get("p").getAsInt();
            JsonArray p = c.getAsJsonArray("pos"), r = c.getAsJsonArray("rot");
            for (int k = 0; k < 3; k++) { bpos[i * 3 + k] = p.get(k).getAsFloat(); brot[i * 3 + k] = r.get(k).getAsFloat(); }
            JsonArray q = c.getAsJsonArray("q");
            quads[i] = new float[q.size() * 0 + totalLen(q)];
            int o = 0;
            for (JsonElement qe : q) for (JsonElement v : qe.getAsJsonArray()) quads[i][o++] = v.getAsFloat();
        }
        for (int i = 0; i < n; i++) {
            Integer pi = pid[i] >= 10 ? index.get(pid[i]) : null;
            parent[i] = pi == null ? -1 : pi;
            rootId[i] = pid[i] < 10 ? pid[i] : 0;
        }
        List<List<Integer>> kl = new ArrayList<>();
        for (int i = 0; i < n; i++) kl.add(new ArrayList<>());
        for (int i = 0; i < n; i++) if (parent[i] >= 0) kl.get(parent[i]).add(i);
        kids = new int[n][];
        for (int i = 0; i < n; i++) { kids[i] = kl.get(i).stream().mapToInt(Integer::intValue).toArray(); }
        for (JsonElement ae : j.getAsJsonArray("anims")) clips.add(new Clip(this, ae.getAsJsonObject()));
    }

    private static int totalLen(JsonArray q) { int t = 0; for (JsonElement e : q) t += e.getAsJsonArray().size(); return t; }

    public static Model parse(String json) { return new Model(JsonParser.parseString(json).getAsJsonObject()); }

    /** indices de todos los cubos que cuelgan de {@code cube} (incluido) */
    public void subtree(int cubeIdx, java.util.Collection<Integer> out) {
        out.add(cubeIdx);
        for (int k : kids[cubeIdx]) subtree(k, out);
    }

    public int idx(int cubeId) {
        Integer i = index.get(cubeId);
        if (i == null) throw new IllegalArgumentException("no existe el cubo " + cubeId + " en " + id);
        return i;
    }

    public Clip clip(String name) { for (Clip c : clips) if (c.name.equals(name)) return c; return null; }

    public List<Clip> clipsNamed(String name) {
        List<Clip> l = new ArrayList<>();
        for (Clip c : clips) if (c.name.equals(name)) l.add(c);
        return l;
    }

    /** orden de recorrido: padres siempre antes que hijos */
    public int[] order() {
        if (order != null) return order;
        int[] o = new int[n]; int k = 0;
        boolean[] done = new boolean[n];
        for (int pass = 0; pass < n && k < n; pass++)
            for (int i = 0; i < n; i++) if (!done[i] && (parent[i] < 0 || done[parent[i]])) { done[i] = true; o[k++] = i; }
        order = o;
        return o;
    }

    private int[] order;

    /** un fotograma de animacion del modelo */
    public static final class Clip {
        public final String name, kind;
        public final int frames, dur, interp;
        public final boolean add, loop;
        public final int[] cube;               // indice de cubo de cada pista
        public final float[][] rot, pos;       // por pista: frames*3 (o null)

        Clip(Model m, JsonObject a) {
            name = a.get("name").getAsString();
            kind = a.get("kind").getAsString();
            frames = a.get("frames").getAsInt();
            dur = Math.max(1, a.get("dur").getAsInt());
            interp = a.get("interp").getAsInt();
            add = a.get("add").getAsBoolean();
            loop = a.get("loop").getAsBoolean();
            JsonArray t = a.getAsJsonArray("tracks");
            int tn = 0;
            for (JsonElement e : t) if (m.index.containsKey(e.getAsJsonObject().get("c").getAsInt())) tn++;
            cube = new int[tn]; rot = new float[tn][]; pos = new float[tn][];
            int k = 0;
            for (JsonElement e : t) {
                JsonObject o = e.getAsJsonObject();
                Integer ci = m.index.get(o.get("c").getAsInt());
                if (ci == null) continue;
                cube[k] = ci;
                rot[k] = o.has("rot") ? arr(o.getAsJsonArray("rot")) : null;
                pos[k] = o.has("pos") ? arr(o.getAsJsonArray("pos")) : null;
                k++;
            }
        }

        private static float[] arr(JsonArray a) { float[] f = new float[a.size()]; for (int i = 0; i < f.length; i++) f[i] = a.get(i).getAsFloat(); return f; }

        /** valor de un canal en el paso {@code step} (0..frames); los bucles cierran el ciclo, los demas se quedan en el ultimo */
        public float sample(float[] v, int comp, float step) {
            int f = frames;
            if (f <= 1) return v[comp];
            if (interp == 4) { // sin interpolar
                int i = Math.floorMod((int) Math.floor(step), f);
                return v[i * 3 + comp];
            }
            boolean cyc = interp == 0 || interp == 2 || interp == 5;
            int i1; float u;
            if (cyc) {
                float s = step % f; if (s < 0) s += f;
                i1 = (int) Math.floor(s); u = s - i1;
            } else {
                float s = Math.max(0F, Math.min(f - 1F, step));
                i1 = (int) Math.floor(s); u = s - i1;
                if (i1 >= f - 1) { i1 = f - 1; u = 0; }
            }
            int i0 = idx(i1 - 1, f, cyc), i2 = idx(i1 + 1, f, cyc), i3 = idx(i1 + 2, f, cyc), ic = idx(i1, f, cyc);
            float p0 = v[i0 * 3 + comp], p1 = v[ic * 3 + comp], p2 = v[i2 * 3 + comp], p3 = v[i3 * 3 + comp];
            if (interp == 2 || interp == 3) return p1 + (p2 - p1) * u;
            if (interp == 5 || interp == 6) { float w = (1F - (float) Math.cos(u * Math.PI)) * 0.5F; return p1 + (p2 - p1) * w; }
            // Catmull-Rom
            float u2 = u * u, u3 = u2 * u;
            return 0.5F * ((2 * p1) + (-p0 + p2) * u + (2 * p0 - 5 * p1 + 4 * p2 - p3) * u2 + (-p0 + 3 * p1 - 3 * p2 + p3) * u3);
        }

        private static int idx(int i, int f, boolean cyc) { return cyc ? Math.floorMod(i, f) : Math.max(0, Math.min(f - 1, i)); }
    }
}
