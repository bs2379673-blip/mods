package com.starlevi.staravatars.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Arma un personaje desde un export de Roblox Studio (.obj + .mtl + imagenes), sin nada de Minecraft
 * (para poder probarlo solo):
 * <ul>
 * <li>Encuentra el cuerpo R15 (las piezas con el nombre del usuario y un numero) y reconoce cabeza,
 * torso, brazos y piernas por donde estan, no por el numero (el orden cambia segun el export).</li>
 * <li>Cada accesorio va entero a una parte del cuerpo; la ropa que cubre las dos piernas o los dos
 * brazos se reparte por triangulo.</li>
 * <li>Los brazos abiertos (pose T/A) se bajan girandolos desde el hombro.</li>
 * <li>Normales suaves (con bordes marcados donde la superficie dobla mucho), como se ve en Roblox.</li>
 * </ul>
 * El resultado queda en el espacio del modelo del jugador de Minecraft (pixeles, y hacia abajo,
 * frente hacia -z), relativo a la articulacion de cada parte.
 */
public final class AvatarBuilder {
    public static final int HEAD = 0, BODY = 1, RIGHT_ARM = 2, LEFT_ARM = 3, RIGHT_LEG = 4, LEFT_LEG = 5;
    public static final String[] PART_NAMES = {"cabeza", "torso", "brazo_der", "brazo_izq", "pierna_der", "pierna_izq"};
    private static final Pattern NUMBERED = Pattern.compile("^(.*?)(\\d+)$");
    private static final Set<String> SCENERY = Set.of("baseplate", "wheel", "spawnlocation", "driversseat", "seat", "part", "terrain", "head",
            "meshpart", "union", "camera");

    /** Un material ya resuelto: textura con el color de Roblox debajo de lo transparente. */
    public static final class Material {
        public final String name;
        public int width, height;
        public int[] argb;
        public boolean cutout, doubleSided;

        Material(String name) {
            this.name = name;
        }
    }

    /** Triangulos de una parte con un material: x, y, z, nx, ny, nz, u, v por vertice. */
    public static final class Batch {
        public final int material;
        public float[] data;

        Batch(int material, float[] data) {
            this.material = material;
            this.data = data;
        }

        public int triangles() {
            return this.data.length / 24;
        }
    }

    public static final class Result {
        public final List<Material> materials = new ArrayList<>();
        public final List<List<Batch>> parts = new ArrayList<>();
        /** Articulacion de cada parte en el espacio del modelo del jugador (pixeles). */
        public final float[][] joints = new float[6][3];
        public int triangles;
        public final List<String> log = new ArrayList<>();
    }

    private static final class MtlDef {
        float[] kd = {1, 1, 1};
        String map;
        boolean overlay, doubleSided;
        float d = 1;
    }

    private static final class Group {
        final String name;
        String material;
        final List<int[]> tris = new ArrayList<>(); // v0 v1 v2 t0 t1 t2
        float[] min = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE}, max = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};

        Group(String name) {
            this.name = name;
        }

        float cx() {
            return (this.min[0] + this.max[0]) / 2;
        }

        float cy() {
            return (this.min[1] + this.max[1]) / 2;
        }
    }

    private AvatarBuilder() {
    }

    // ------------------------------------------------------------------ lectura

    private static Map<String, MtlDef> parseMtl(String text) {
        Map<String, MtlDef> out = new HashMap<>();
        MtlDef cur = null;
        for (String raw : text.split("\n")) {
            String l = raw.trim();
            if (l.isEmpty() || l.startsWith("#")) {
                continue;
            }
            String[] t = l.split("\\s+");
            switch (t[0]) {
                case "newmtl" -> {
                    cur = new MtlDef();
                    out.put(l.substring(7).trim(), cur);
                }
                case "Kd" -> {
                    if (cur != null && t.length >= 4) {
                        cur.kd = new float[]{f(t[1]), f(t[2]), f(t[3])};
                    }
                }
                case "map_Kd" -> {
                    if (cur != null) {
                        cur.map = l.substring(6).trim();
                    }
                }
                case "d" -> {
                    if (cur != null && t.length > 1) {
                        cur.d = f(t[1]);
                    }
                }
                case "rbx_alphamode" -> {
                    if (cur != null) {
                        cur.overlay = t.length > 1 && t[1].equalsIgnoreCase("overlay");
                    }
                }
                case "rbx_doublesided" -> {
                    if (cur != null) {
                        cur.doubleSided = t.length > 1 && t[1].equals("1");
                    }
                }
                default -> {
                }
            }
        }
        return out;
    }

    private static float f(String s) {
        try {
            return Float.parseFloat(s);
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * @param files nombre de archivo -> contenido (el .obj, el .mtl y las imagenes)
     * @param json  opciones del avatar.json ("{}" si no hay)
     */
    public static Result build(Map<String, byte[]> files, String json) {
        Result res = new Result();
        JsonObject opt;
        try {
            opt = JsonParser.parseString(json == null || json.isBlank() ? "{}" : json).getAsJsonObject();
        } catch (Exception e) {
            opt = new JsonObject();
        }
        Map<String, byte[]> byLower = new HashMap<>();
        byte[] objData = null, mtlData = null;
        for (Map.Entry<String, byte[]> e : files.entrySet()) {
            String n = e.getKey().toLowerCase(Locale.ROOT);
            byLower.put(n, e.getValue());
            if (n.endsWith(".obj") && objData == null) {
                objData = e.getValue();
            } else if (n.endsWith(".mtl") && mtlData == null) {
                mtlData = e.getValue();
            }
        }
        if (objData == null) {
            throw new IllegalArgumentException("falta el .obj");
        }
        Map<String, MtlDef> mtl = mtlData == null ? new HashMap<>() : parseMtl(new String(mtlData, StandardCharsets.UTF_8));

        // --- OBJ
        List<float[]> vs = new ArrayList<>();
        List<float[]> vts = new ArrayList<>();
        Map<String, Group> groups = new LinkedHashMap<>();
        Group cur = null;
        String curMat = null;
        String text = new String(objData, StandardCharsets.UTF_8);
        int start = 0, n = text.length();
        while (start < n) {
            int end = text.indexOf('\n', start);
            if (end < 0) {
                end = n;
            }
            String l = text.substring(start, end).trim();
            start = end + 1;
            if (l.length() < 2) {
                continue;
            }
            char c0 = l.charAt(0), c1 = l.charAt(1);
            if (c0 == 'v' && c1 == ' ') {
                String[] t = l.split("\\s+");
                vs.add(new float[]{f(t[1]), f(t[2]), f(t[3])});
            } else if (c0 == 'v' && c1 == 't') {
                String[] t = l.split("\\s+");
                vts.add(new float[]{f(t[1]), t.length > 2 ? f(t[2]) : 0});
            } else if (c0 == 'g' || c0 == 'o') {
                String name = l.substring(1).trim();
                cur = groups.computeIfAbsent(name, Group::new);
                if (curMat != null && cur.material == null) {
                    cur.material = curMat;
                }
            } else if (l.startsWith("usemtl")) {
                curMat = l.substring(6).trim();
                if (cur != null) {
                    cur.material = curMat;
                }
            } else if (c0 == 'f' && c1 == ' ') {
                if (cur == null) {
                    cur = groups.computeIfAbsent("sin_nombre", Group::new);
                    cur.material = curMat;
                }
                String[] t = l.split("\\s+");
                int k = t.length - 1;
                int[] vi = new int[k], ti = new int[k];
                for (int i = 0; i < k; i++) {
                    String[] p = t[i + 1].split("/");
                    int a = Integer.parseInt(p[0]);
                    vi[i] = a < 0 ? vs.size() + a : a - 1;
                    if (p.length > 1 && !p[1].isEmpty()) {
                        int b = Integer.parseInt(p[1]);
                        ti[i] = b < 0 ? vts.size() + b : b - 1;
                    } else {
                        ti[i] = -1;
                    }
                }
                for (int i = 1; i + 1 < k; i++) {
                    cur.tris.add(new int[]{vi[0], vi[i], vi[i + 1], ti[0], ti[i], ti[i + 1]});
                }
            }
        }
        float[][] V = vs.toArray(new float[0][]);
        float[][] VT = vts.toArray(new float[0][]);
        for (Group g : groups.values()) {
            for (int[] t : g.tris) {
                for (int i = 0; i < 3; i++) {
                    float[] p = V[t[i]];
                    for (int a = 0; a < 3; a++) {
                        g.min[a] = Math.min(g.min[a], p[a]);
                        g.max[a] = Math.max(g.max[a], p[a]);
                    }
                }
            }
        }
        groups.values().removeIf(g -> g.tris.isEmpty());
        Set<String> drop = strings(opt, "sacar");
        groups.keySet().removeAll(drop);

        // --- accesorios y cuerpo
        List<Group> handles = new ArrayList<>();
        Map<String, List<Group>> numbered = new LinkedHashMap<>();
        for (Group g : groups.values()) {
            Matcher m = NUMBERED.matcher(g.name);
            String prefix = m.matches() ? m.group(1) : g.name;
            if (prefix.equalsIgnoreCase("handle") || prefix.equalsIgnoreCase("accessory")) {
                handles.add(g);
            } else if (m.matches() && !SCENERY.contains(prefix.toLowerCase(Locale.ROOT))) {
                numbered.computeIfAbsent(prefix, x -> new ArrayList<>()).add(g);
            }
        }
        String bodyPrefix = opt.has("cuerpo") ? opt.get("cuerpo").getAsString() : pickBody(numbered, handles);
        List<Group> body = bodyPrefix == null ? new ArrayList<>() : numbered.getOrDefault(bodyPrefix, new ArrayList<>());
        if (body.isEmpty()) {
            throw new IllegalArgumentException("no encontre el cuerpo R15 (las piezas con el nombre del usuario)");
        }
        res.log.add("cuerpo: " + bodyPrefix + " (" + body.size() + " piezas), accesorios: " + handles.size());

        // cabeza: la pieza mas alta; torso: las centradas; el resto son brazos o piernas
        Group head = body.get(0);
        for (Group g : body) {
            if (g.cy() > head.cy()) {
                head = g;
            }
        }
        float headW = head.max[0] - head.min[0];
        float hx = head.cx();
        float feet = Float.MAX_VALUE;
        for (Group g : body) {
            feet = Math.min(feet, g.min[1]);
        }
        for (Group g : handles) {
            feet = Math.min(feet, g.min[1]);
        }
        float headBottom = head.min[1], headTop = head.max[1], headH = headTop - headBottom;
        int[] role = new int[body.size()];
        List<Group> torso = new ArrayList<>();
        for (int i = 0; i < body.size(); i++) {
            Group g = body.get(i);
            float dx = g.cx() - hx;
            if (g == head) {
                role[i] = HEAD;
            } else if (Math.abs(dx) < 0.15f * headW) {
                role[i] = BODY;
                torso.add(g);
            } else {
                boolean arm = g.min[1] > feet + 0.33f * (headBottom - feet);
                // Roblox mira a -z: su derecha es +x
                role[i] = arm ? (dx > 0 ? RIGHT_ARM : LEFT_ARM) : (dx > 0 ? RIGHT_LEG : LEFT_LEG);
            }
        }
        float[][] box = new float[6][];
        for (int i = 0; i < body.size(); i++) {
            box[role[i]] = union(box[role[i]], body.get(i));
        }
        float cx = box[BODY] != null ? (box[BODY][0] + box[BODY][3]) / 2 : hx;
        float cz = box[BODY] != null ? (box[BODY][2] + box[BODY][5]) / 2 : (head.min[2] + head.max[2]) / 2;
        float neckY = headBottom;
        // piernas que faltan (cuerpos sin piernas, o con pantalon 3D): cajas imaginarias debajo de la cadera
        float hipY;
        if (box[BODY] != null && box[BODY][1] > feet + 0.1f * (neckY - feet) && box[BODY][3] - box[BODY][0] > 0.35f * headW) {
            hipY = box[BODY][1]; // la cadera es donde termina el torso (aunque las piernas sean cortas o falten)
        } else if (box[RIGHT_LEG] != null || box[LEFT_LEG] != null) {
            hipY = Math.max(box[RIGHT_LEG] == null ? -Float.MAX_VALUE : box[RIGHT_LEG][4], box[LEFT_LEG] == null ? -Float.MAX_VALUE : box[LEFT_LEG][4]);
        } else {
            hipY = feet + 0.42f * (neckY - feet);
        }
        for (int side : new int[]{RIGHT_LEG, LEFT_LEG}) {
            if (box[side] != null && box[side][4] < hipY) {
                box[side][4] = hipY;
            }
        }
        float halfW = box[BODY] != null ? (box[BODY][3] - box[BODY][0]) / 2 : headW / 2;
        float zMin = box[BODY] != null ? box[BODY][2] : head.min[2], zMax = box[BODY] != null ? box[BODY][5] : head.max[2];
        if (box[RIGHT_LEG] == null) {
            box[RIGHT_LEG] = new float[]{cx, feet, zMin, cx + halfW, hipY, zMax};
        }
        if (box[LEFT_LEG] == null) {
            box[LEFT_LEG] = new float[]{cx - halfW, feet, zMin, cx, hipY, zMax};
        }
        if (box[BODY] == null) {
            box[BODY] = new float[]{cx - halfW, hipY, zMin, cx + halfW, neckY, zMax};
        } else {
            box[BODY][1] = Math.min(box[BODY][1], hipY);
            box[BODY][4] = Math.max(box[BODY][4], neckY);
        }

        // --- a que parte va cada triangulo
        Map<Group, int[]> triPart = new LinkedHashMap<>();
        for (int i = 0; i < body.size(); i++) {
            int[] p = new int[body.get(i).tris.size()];
            java.util.Arrays.fill(p, role[i]);
            triPart.put(body.get(i), p);
        }
        Map<String, String> force = map(opt, "forzar");
        Set<String> split = strings(opt, "separar");
        for (Group g : handles) {
            int[] p = new int[g.tris.size()];
            String f = force.get(g.name);
            int forced = f == null ? -1 : partIndex(f);
            if (forced >= 0) {
                java.util.Arrays.fill(p, forced);
            } else if (g.max[1] > headBottom + 0.3f * headH) {
                java.util.Arrays.fill(p, HEAD); // pelo, sombreros, cosas de la cara
            } else {
                int[] count = new int[6];
                for (int i = 0; i < p.length; i++) {
                    int[] t = g.tris.get(i);
                    float x = 0, y = 0, z = 0;
                    for (int k = 0; k < 3; k++) {
                        x += V[t[k]][0] / 3;
                        y += V[t[k]][1] / 3;
                        z += V[t[k]][2] / 3;
                    }
                    int best = BODY;
                    float bd = Float.MAX_VALUE;
                    for (int b = 0; b < 6; b++) {
                        if (box[b] == null) {
                            continue;
                        }
                        float d = dist(box[b], x, y, z);
                        if (d < bd - 1e-4f) {
                            bd = d;
                            best = b;
                        }
                    }
                    p[i] = best;
                    count[best]++;
                }
                int total = p.length;
                boolean legs = count[RIGHT_LEG] > 0.2f * total && count[LEFT_LEG] > 0.2f * total;
                boolean arms = count[RIGHT_ARM] > 0.2f * total && count[LEFT_ARM] > 0.2f * total;
                if ((legs || arms) && !(legs && arms)) {
                    // ropa de piernas (o de brazos): se reparte solo entre esas partes y el torso
                    int[] allowed = legs ? new int[]{BODY, RIGHT_LEG, LEFT_LEG} : new int[]{BODY, RIGHT_ARM, LEFT_ARM};
                    for (int i = 0; i < p.length; i++) {
                        int[] t = g.tris.get(i);
                        float x = 0, y = 0, z = 0;
                        for (int k = 0; k < 3; k++) {
                            x += V[t[k]][0] / 3;
                            y += V[t[k]][1] / 3;
                            z += V[t[k]][2] / 3;
                        }
                        int best = BODY;
                        float bd = Float.MAX_VALUE;
                        for (int b : allowed) {
                            float d = dist(box[b], x, y, z);
                            if (d < bd - 1e-4f) {
                                bd = d;
                                best = b;
                            }
                        }
                        p[i] = best;
                    }
                }
                if (!(split.contains(g.name) || legs || arms)) {
                    int best = BODY;
                    for (int b = 0; b < 6; b++) {
                        if (count[b] > count[best]) {
                            best = b;
                        }
                    }
                    java.util.Arrays.fill(p, best);
                }
            }
            triPart.put(g, p);
        }

        // --- articulaciones (Roblox, studs)
        float[][] joint = new float[6][];
        float[][] armDir = new float[6][];
        joint[HEAD] = new float[]{cx, neckY, cz};
        joint[BODY] = new float[]{cx, neckY, cz};
        for (int side : new int[]{RIGHT_ARM, LEFT_ARM}) {
            float[] b = box[side];
            if (b == null) {
                // sin brazos: donde estarian
                float sx = side == RIGHT_ARM ? cx + halfW * 1.5f : cx - halfW * 1.5f;
                joint[side] = new float[]{sx, neckY - 0.12f * (neckY - feet), cz};
                continue;
            }
            float[] ax = armAxis(V, body, role, side);
            joint[side] = new float[]{ax[0], ax[1], (b[2] + b[5]) / 2};
            armDir[side] = new float[]{ax[2], ax[3]};
        }
        for (int side : new int[]{RIGHT_LEG, LEFT_LEG}) {
            float[] b = box[side];
            joint[side] = new float[]{(b[0] + b[3]) / 2, hipY, (b[2] + b[5]) / 2};
        }

        // --- bajar los brazos abiertos (giro alrededor del hombro, en el plano del frente)
        boolean lower = !opt.has("brazos_abajo") || opt.get("brazos_abajo").getAsBoolean();
        Set<Integer> moved = new HashSet<>();
        if (lower) {
            for (int side : new int[]{RIGHT_ARM, LEFT_ARM}) {
                boolean has = false;
                for (int r : role) {
                    has |= r == side;
                }
                if (!has) {
                    continue;
                }
                float[] sh = joint[side];
                float dx = armDir[side][0], dy = armDir[side][1];
                double ang = Math.atan2(dx, -dy);
                if (Math.abs(ang) < Math.toRadians(8)) {
                    continue;
                }
                double phi = Math.abs(Math.cos(ang) * dx - Math.sin(ang) * dy) < Math.abs(Math.cos(-ang) * dx - Math.sin(-ang) * dy) ? ang : -ang;
                float c = (float) Math.cos(phi), s = (float) Math.sin(phi);
                Set<Integer> verts = new HashSet<>();
                for (Map.Entry<Group, int[]> e : triPart.entrySet()) {
                    List<int[]> tris = e.getKey().tris;
                    for (int i = 0; i < tris.size(); i++) {
                        if (e.getValue()[i] == side) {
                            for (int k = 0; k < 3; k++) {
                                verts.add(tris.get(i)[k]);
                            }
                        }
                    }
                }
                for (int vi : verts) {
                    if (!moved.add(vi)) {
                        continue;
                    }
                    float[] p = V[vi];
                    float qx = p[0] - sh[0], qy = p[1] - sh[1];
                    V[vi] = new float[]{c * qx - s * qy + sh[0], s * qx + c * qy + sh[1], p[2]};
                }
                res.log.add(PART_NAMES[side] + " bajado " + Math.round(Math.toDegrees(phi)) + " grados");
            }
        }

        // --- escala y paso al espacio del jugador de Minecraft
        float S = 32f / (headTop - feet);
        for (int i = 0; i < 6; i++) {
            res.joints[i] = toModel(joint[i], cx, cz, feet, S);
        }

        // --- materiales
        Map<String, Integer> matIndex = new HashMap<>();
        Map<String, Integer> same = new HashMap<>();
        for (Map.Entry<Group, int[]> e : triPart.entrySet()) {
            String mname = e.getKey().material == null ? "" : e.getKey().material;
            if (!matIndex.containsKey(mname)) {
                MtlDef d = mtl.get(mname);
                String k = d == null ? mname : d.map + "|" + d.kd[0] + "," + d.kd[1] + "," + d.kd[2] + "|" + d.overlay + "|" + d.doubleSided;
                Integer idx = same.get(k);
                if (idx == null) {
                    idx = res.materials.size();
                    res.materials.add(material(mname, d, byLower, opt, res.log));
                    same.put(k, idx);
                }
                matIndex.put(mname, idx);
            }
        }

        // --- normales suaves por pieza y lotes por parte y material
        List<Map<Integer, FloatList>> acc = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            acc.add(new HashMap<>());
        }
        for (Map.Entry<Group, int[]> e : triPart.entrySet()) {
            Group g = e.getKey();
            int[] parts = e.getValue();
            int mi = matIndex.get(g.material == null ? "" : g.material);
            float[][][] normals = smoothNormals(V, g.tris, 55);
            for (int i = 0; i < g.tris.size(); i++) {
                int[] t = g.tris.get(i);
                int part = parts[i];
                float[] j = res.joints[part];
                FloatList fl = acc.get(part).computeIfAbsent(mi, x -> new FloatList());
                float mu = 0, mv = 0;
                for (int k = 0; k < 3; k++) {
                    float[] uv = t[3 + k] >= 0 && t[3 + k] < VT.length ? VT[t[3 + k]] : new float[]{0, 0};
                    mu += uv[0] / 3;
                    mv += uv[1] / 3;
                }
                float su = (float) Math.floor(mu), sv = (float) Math.floor(mv);
                for (int k = 0; k < 3; k++) {
                    float[] m = toModel(V[t[k]], cx, cz, feet, S);
                    float[] nn = normals[i][k];
                    float[] uv = t[3 + k] >= 0 && t[3 + k] < VT.length ? VT[t[3 + k]] : new float[]{0, 0};
                    fl.add(m[0] - j[0], m[1] - j[1], m[2] - j[2], -nn[0], -nn[1], nn[2], uv[0] - su, 1 - (uv[1] - sv));
                }
                res.triangles++;
            }
        }
        for (int i = 0; i < 6; i++) {
            List<Batch> list = new ArrayList<>();
            for (Map.Entry<Integer, FloatList> e : acc.get(i).entrySet()) {
                list.add(new Batch(e.getKey(), e.getValue().toArray()));
            }
            res.parts.add(list);
        }
        res.log.add("triangulos: " + res.triangles);
        return res;
    }

    // ------------------------------------------------------------------ ayudas

    private static String pickBody(Map<String, List<Group>> numbered, List<Group> handles) {
        float[] hb = null;
        for (Group g : handles) {
            hb = union(hb, g);
        }
        String best = null;
        float bestScore = Float.MAX_VALUE;
        for (Map.Entry<String, List<Group>> e : numbered.entrySet()) {
            int n = e.getValue().size();
            if (n < 5 || n > 20) {
                continue;
            }
            float[] b = null;
            for (Group g : e.getValue()) {
                b = union(b, g);
            }
            float h = b[4] - b[1];
            if (h < 1 || h > 30) {
                continue;
            }
            float score;
            if (hb != null) {
                float dx = (b[0] + b[3]) / 2 - (hb[0] + hb[3]) / 2, dy = (b[1] + b[4]) / 2 - (hb[1] + hb[4]) / 2,
                        dz = (b[2] + b[5]) / 2 - (hb[2] + hb[5]) / 2;
                score = dx * dx + dy * dy + dz * dz;
            } else {
                score = Math.abs(n - 15);
            }
            if (score < bestScore) {
                bestScore = score;
                best = e.getKey();
            }
        }
        return best;
    }

    private static float[] union(float[] b, Group g) {
        if (b == null) {
            return new float[]{g.min[0], g.min[1], g.min[2], g.max[0], g.max[1], g.max[2]};
        }
        return new float[]{Math.min(b[0], g.min[0]), Math.min(b[1], g.min[1]), Math.min(b[2], g.min[2]), Math.max(b[3], g.max[0]),
                Math.max(b[4], g.max[1]), Math.max(b[5], g.max[2])};
    }

    private static float dist(float[] b, float x, float y, float z) {
        float dx = Math.max(Math.max(b[0] - x, 0), x - b[3]);
        float dy = Math.max(Math.max(b[1] - y, 0), y - b[4]);
        float dz = Math.max(Math.max(b[2] - z, 0), z - b[5]);
        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * Eje del brazo (en el plano del frente): direccion principal de sus vertices, apuntando hacia abajo.
     * Devuelve el hombro (x, y: arriba del eje, medio grosor hacia adentro) y la direccion (dx, dy).
     */
    private static float[] armAxis(float[][] V, List<Group> body, int[] role, int side) {
        double sx = 0, sy = 0;
        int n = 0;
        for (int i = 0; i < body.size(); i++) {
            if (role[i] != side) {
                continue;
            }
            for (int[] t : body.get(i).tris) {
                for (int k = 0; k < 3; k++) {
                    sx += V[t[k]][0];
                    sy += V[t[k]][1];
                    n++;
                }
            }
        }
        double mx = sx / n, my = sy / n, cxx = 0, cxy = 0, cyy = 0;
        for (int i = 0; i < body.size(); i++) {
            if (role[i] != side) {
                continue;
            }
            for (int[] t : body.get(i).tris) {
                for (int k = 0; k < 3; k++) {
                    double x = V[t[k]][0] - mx, y = V[t[k]][1] - my;
                    cxx += x * x;
                    cxy += x * y;
                    cyy += y * y;
                }
            }
        }
        double th = 0.5 * Math.atan2(2 * cxy, cxx - cyy);
        double dx = Math.cos(th), dy = Math.sin(th);
        if (Math.abs(dy) < 0.3) {
            // eje casi horizontal (pose T): se toma igual, apuntando hacia afuera y abajo
            dx = Math.signum(dx) * Math.abs(dx);
        }
        if (dy > 0) {
            dx = -dx;
            dy = -dy;
        }
        double tmin = Double.MAX_VALUE, wmax = 0;
        for (int i = 0; i < body.size(); i++) {
            if (role[i] != side) {
                continue;
            }
            for (int[] t : body.get(i).tris) {
                for (int k = 0; k < 3; k++) {
                    double x = V[t[k]][0] - mx, y = V[t[k]][1] - my;
                    tmin = Math.min(tmin, x * dx + y * dy);
                    wmax = Math.max(wmax, Math.abs(-x * dy + y * dx));
                }
            }
        }
        double t0 = tmin + wmax; // medio grosor (wmax es la mitad del ancho) hacia adentro desde la punta de arriba
        return new float[]{(float) (mx + dx * t0), (float) (my + dy * t0), (float) dx, (float) dy};
    }

    private static float topCenterX(float[][] V, List<Group> body, int[] role, int side, float top) {
        float s = 0;
        int n = 0;
        for (int i = 0; i < body.size(); i++) {
            if (role[i] != side) {
                continue;
            }
            for (int[] t : body.get(i).tris) {
                for (int k = 0; k < 3; k++) {
                    if (V[t[k]][1] > top - 0.2f) {
                        s += V[t[k]][0];
                        n++;
                    }
                }
            }
        }
        return n == 0 ? 0 : s / n;
    }

    /** Roblox (studs, y arriba, frente -z, derecha +x) -> modelo del jugador (pixeles, y abajo, derecha -x). */
    private static float[] toModel(float[] p, float cx, float cz, float feet, float S) {
        return new float[]{-(p[0] - cx) * S, 24 - (p[1] - feet) * S, (p[2] - cz) * S};
    }

    public static int partIndex(String s) {
        return switch (s.toLowerCase(Locale.ROOT)) {
            case "cabeza", "head" -> HEAD;
            case "torso", "cuerpo", "body" -> BODY;
            case "brazo_der", "brazo_derecho", "rightarm" -> RIGHT_ARM;
            case "brazo_izq", "brazo_izquierdo", "leftarm" -> LEFT_ARM;
            case "pierna_der", "pierna_derecha", "rightleg" -> RIGHT_LEG;
            case "pierna_izq", "pierna_izquierda", "leftleg" -> LEFT_LEG;
            default -> -1;
        };
    }

    private static Set<String> strings(JsonObject o, String key) {
        Set<String> out = new HashSet<>();
        if (o.has(key) && o.get(key).isJsonArray()) {
            for (JsonElement e : o.getAsJsonArray(key)) {
                out.add(e.getAsString());
            }
        }
        return out;
    }

    private static Map<String, String> map(JsonObject o, String key) {
        Map<String, String> out = new HashMap<>();
        if (o.has(key) && o.get(key).isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject(key).entrySet()) {
                out.put(e.getKey(), e.getValue().getAsString());
            }
        }
        return out;
    }

    /** Normal de cada esquina: promedio (por area) de las caras que la tocan y doblan menos de maxDeg. */
    private static float[][][] smoothNormals(float[][] V, List<int[]> tris, float maxDeg) {
        int n = tris.size();
        float[][] fn = new float[n][];
        Map<Long, List<Integer>> byPos = new HashMap<>();
        for (int i = 0; i < n; i++) {
            int[] t = tris.get(i);
            float[] a = V[t[0]], b = V[t[1]], c = V[t[2]];
            float ux = b[0] - a[0], uy = b[1] - a[1], uz = b[2] - a[2], vx = c[0] - a[0], vy = c[1] - a[1], vz = c[2] - a[2];
            float nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
            fn[i] = new float[]{nx, ny, nz}; // largo = 2 * area
            for (int k = 0; k < 3; k++) {
                byPos.computeIfAbsent(key(V[t[k]]), x -> new ArrayList<>()).add(i);
            }
        }
        float cosMax = (float) Math.cos(Math.toRadians(maxDeg));
        float[][][] out = new float[n][3][];
        for (int i = 0; i < n; i++) {
            float[] me = unit(fn[i]);
            int[] t = tris.get(i);
            for (int k = 0; k < 3; k++) {
                float sx = 0, sy = 0, sz = 0;
                for (int j : byPos.get(key(V[t[k]]))) {
                    float[] o = fn[j];
                    float[] ou = unit(o);
                    if (ou[0] * me[0] + ou[1] * me[1] + ou[2] * me[2] >= cosMax) {
                        sx += o[0];
                        sy += o[1];
                        sz += o[2];
                    }
                }
                float[] r = unit(new float[]{sx, sy, sz});
                out[i][k] = r[0] == 0 && r[1] == 0 && r[2] == 0 ? me : r;
            }
        }
        return out;
    }

    private static long key(float[] p) {
        long x = Math.round(p[0] * 2000), y = Math.round(p[1] * 2000), z = Math.round(p[2] * 2000);
        return (x * 73856093L) ^ (y * 19349663L) ^ (z * 83492791L);
    }

    private static float[] unit(float[] v) {
        float l = (float) Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        return l < 1e-12f ? new float[]{0, 0, 0} : new float[]{v[0] / l, v[1] / l, v[2] / l};
    }

    private static Material material(String name, MtlDef def, Map<String, byte[]> files, JsonObject opt, List<String> log) {
        Material m = new Material(name);
        if (def == null) {
            def = new MtlDef();
        }
        m.doubleSided = def.doubleSided;
        Map<String, String> remap = map(opt, "texturas");
        String file = remap.getOrDefault(name, def.map);
        BufferedImage img = null;
        if (file != null) {
            byte[] data = files.get(file.toLowerCase(Locale.ROOT).replace('\\', '/').replaceAll(".*/", ""));
            if (data != null) {
                try {
                    img = ImageIO.read(new ByteArrayInputStream(data));
                } catch (Exception e) {
                    log.add("no se pudo leer " + file + ": " + e);
                }
            } else {
                log.add("falta la imagen " + file + " (" + name + ")");
            }
        }
        int kr = Math.round(clamp(def.kd[0]) * 255), kg = Math.round(clamp(def.kd[1]) * 255), kb = Math.round(clamp(def.kd[2]) * 255);
        if (img == null) {
            m.width = m.height = 4;
            m.argb = new int[16];
            java.util.Arrays.fill(m.argb, 0xFF000000 | kr << 16 | kg << 8 | kb);
            return m;
        }
        int w = img.getWidth(), h = img.getHeight();
        m.width = w;
        m.height = h;
        m.argb = img.getRGB(0, 0, w, h, null, 0, w);
        boolean overlay = def.overlay;
        int transparent = 0;
        for (int i = 0; i < m.argb.length; i++) {
            int p = m.argb[i];
            int a = p >>> 24;
            if (overlay || a == 255) {
                // el color del material asoma donde la textura es transparente (asi pinta Roblox la ropa y las caras)
                float al = a / 255f;
                int r = Math.round(((p >> 16) & 255) * al + kr * (1 - al));
                int g = Math.round(((p >> 8) & 255) * al + kg * (1 - al));
                int b = Math.round((p & 255) * al + kb * (1 - al));
                m.argb[i] = 0xFF000000 | r << 16 | g << 8 | b;
            } else if (a < 128) {
                transparent++;
            }
        }
        m.cutout = transparent > 0;
        return m;
    }

    private static float clamp(float v) {
        return Math.max(0, Math.min(1, v));
    }

    private static final class FloatList {
        float[] a = new float[1024];
        int n;

        void add(float... v) {
            if (this.n + v.length > this.a.length) {
                this.a = java.util.Arrays.copyOf(this.a, Math.max(this.a.length * 2, this.n + v.length));
            }
            System.arraycopy(v, 0, this.a, this.n, v.length);
            this.n += v.length;
        }

        float[] toArray() {
            return java.util.Arrays.copyOf(this.a, this.n);
        }
    }
}
