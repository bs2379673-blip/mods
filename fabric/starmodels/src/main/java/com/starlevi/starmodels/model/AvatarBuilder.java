package com.starlevi.starmodels.model;

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
    /** Marca de triangulo con piel (ropa 3D). */
    static final int SKIN = -1;
    public static final int HEAD = 0, BODY = 1, RIGHT_ARM = 2, LEFT_ARM = 3, RIGHT_LEG = 4, LEFT_LEG = 5;
    /** Antebrazos y pantorrillas: cuelgan del brazo o la pierna (parte - 4) y se doblan en el codo o la rodilla. */
    public static final int RIGHT_FOREARM = 6, LEFT_FOREARM = 7, RIGHT_SHIN = 8, LEFT_SHIN = 9;
    public static final int PARTS = 10;
    public static final String[] PART_NAMES = {"cabeza", "torso", "brazo_der", "brazo_izq", "pierna_der", "pierna_izq", "antebrazo_der",
            "antebrazo_izq", "pantorrilla_der", "pantorrilla_izq"};
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

    /** Triangulos de una parte con un material: x, y, z (modelo, absolutos), nx, ny, nz, u, v por vertice. */
    public static final class Batch {
        public final int material;
        /** Parte que lo mueve, o -1 en las uniones (cada vertice con su parte). */
        public final int part;
        public float[] data;

        Batch(int material, int part, float[] data) {
            this.material = material;
            this.part = part;
            this.data = data;
        }

        /** Unión: x, y, z, nx, ny, nz, u, v y la parte de ese vertice. */
        public static final int SEAM_STRIDE = 9;
    }

    /**
     * Un accesorio rigido (pelo, sombrero, alas, mochila...): se puede sacar, o ponerselo a otro personaje.
     * Sus vertices estan en el espacio del modelo de este personaje; para otro se corre de la articulacion
     * de esta parte a la del otro.
     */
    public static final class Accessory {
        public final String id;
        public String name;
        public final int part;
        public final List<Batch> batches = new ArrayList<>();
        public final float[] center = new float[3];
        public int triangles;

        Accessory(String id, int part) {
            this.id = id;
            this.name = id;
            this.part = part;
        }
    }

    public static final class Result {
        /** Accesorios que se pueden sacar o pasar a otros personajes (la ropa 3D no: va pegada al cuerpo). */
        public final List<Accessory> accessories = new ArrayList<>();
        public final List<Material> materials = new ArrayList<>();
        public final List<List<Batch>> parts = new ArrayList<>();
        /**
         * Uniones de la ropa 3D: triangulos cuyos vertices van pegados a partes distintas (la tela entre
         * el muslo y la pantorrilla, por ejemplo). Se estiran al doblar en vez de abrirse un agujero.
         */
        public final List<Batch> seams = new ArrayList<>();
        /** Todo junto por material (para dibujar cada textura de una sola vez). */
        public final List<List<Batch>> byMaterial = new ArrayList<>();
        /** Articulacion de cada parte en el espacio del modelo del jugador (pixeles); las partes 6-9 son codos y rodillas. */
        public final float[][] joints = new float[PARTS][3];
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
        byte[] objData = null, mtlData = null, gltfData = null;
        for (Map.Entry<String, byte[]> e : files.entrySet()) {
            String n = e.getKey().toLowerCase(Locale.ROOT);
            byLower.put(n, e.getValue());
            if (n.endsWith(".obj") && objData == null) {
                objData = e.getValue();
            } else if (n.endsWith(".mtl") && mtlData == null) {
                mtlData = e.getValue();
            } else if ((n.endsWith(".gltf") || n.endsWith(".glb")) && gltfData == null) {
                gltfData = e.getValue();
            }
        }
        if (objData == null) {
            throw new IllegalArgumentException("falta el .obj");
        }
        Map<String, MtlDef> mtl = mtlData == null ? new HashMap<>() : parseMtl(new String(mtlData, StandardCharsets.UTF_8));

        // --- OBJ
        List<float[]> vs = new ArrayList<>();
        List<float[]> vts = new ArrayList<>();
        List<float[]> vns = new ArrayList<>();
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
            } else if (c0 == 'v' && c1 == 'n') {
                String[] t = l.split("\\s+");
                vns.add(new float[]{f(t[1]), f(t[2]), f(t[3])});
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
                int[] vi = new int[k], ti = new int[k], ni = new int[k];
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
                    if (p.length > 2 && !p[2].isEmpty()) {
                        int c = Integer.parseInt(p[2]);
                        ni[i] = c < 0 ? vns.size() + c : c - 1;
                    } else {
                        ni[i] = -1;
                    }
                }
                for (int i = 1; i + 1 < k; i++) {
                    cur.tris.add(new int[]{vi[0], vi[i], vi[i + 1], ti[0], ti[i], ti[i + 1], ni[0], ni[i], ni[i + 1]});
                }
            }
        }
        float[][] V = vs.toArray(new float[0][]);
        float[][] VT = vts.toArray(new float[0][]);
        float[][] VN = vns.toArray(new float[0][]);
        // giro que se le aplico a cada vertice al bajar los brazos (para girar tambien su normal)
        Map<Integer, Float> turned = new HashMap<>();
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
        // --- menos triangulos (las texturas quedan iguales): lo que mas pesa al dibujar
        int budget = opt.has("triangulos_max") ? opt.get("triangulos_max").getAsInt() : 14000;
        List<Group> used = new ArrayList<>(body);
        used.addAll(handles);
        int before = 0;
        for (Group g : used) {
            before += g.tris.size();
        }
        if (budget > 0 && before > budget) {
            // las piezas chicas no se tocan; las grandes (pelo, ropa) se reparten lo que queda
            int small = 0, big = 0;
            for (Group g : used) {
                if (g.tris.size() <= 300) {
                    small += g.tris.size();
                } else {
                    big += g.tris.size();
                }
            }
            float ratio = Math.max(0.25f, Math.min(1, (budget - small) / (float) Math.max(1, big)));
            int after = 0;
            for (Group g : used) {
                if (g.tris.size() > 300) {
                    List<int[]> st = MeshSimplify.simplify(V, VT, VN, g.tris, Math.round(g.tris.size() * ratio));
                    g.tris.clear();
                    g.tris.addAll(st);
                }
                after += g.tris.size();
            }
            res.log.add("triangulos: " + before + " -> " + after);
        }
        GltfRig rig = null;
        if (gltfData != null) {
            try {
                rig = GltfRig.read(gltfData, opt.has("gltf_nombre") ? opt.get("gltf_nombre").getAsString() : bodyPrefix, res.log);
            } catch (Exception e) {
                res.log.add("glTF: no se pudo leer (" + e + ")");
            }
        }

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
        // esqueleto del glTF pasado a este .obj (puede estar en otro lugar del mapa o a otra escala)
        Map<String, float[]> rj = new HashMap<>();
        if (rig != null && rig.bounds.containsKey("Head") && (rig.bounds.containsKey("LeftFoot") || rig.bounds.containsKey("RightFoot"))) {
            float[] hb = rig.bounds.get("Head");
            float gFeet = Float.MAX_VALUE;
            for (String f : new String[]{"LeftFoot", "RightFoot"}) {
                if (rig.bounds.containsKey(f)) {
                    gFeet = Math.min(gFeet, rig.bounds.get(f)[1]);
                }
            }
            float sc = (headTop - feet) / (hb[4] - gFeet);
            if (Math.abs(sc - 1) < 0.08f) {
                sc = 1;
            }
            float gcx = (hb[0] + hb[3]) / 2, gcz = (hb[2] + hb[5]) / 2, ocx = head.cx(), ocz = (head.min[2] + head.max[2]) / 2;
            float dy = ((feet - gFeet * sc) + (headTop - hb[4] * sc)) / 2;
            for (Map.Entry<String, float[]> e : rig.joints.entrySet()) {
                float[] p = e.getValue();
                rj.put(e.getKey(), new float[]{(p[0] - gcx) * sc + ocx, p[1] * sc + dy, (p[2] - gcz) * sc + ocz});
            }
            res.log.add("glTF alineado: escala " + sc);
        }
        // piernas que faltan (cuerpos sin piernas, o con pantalon 3D): cajas imaginarias debajo de la cadera
        float hipY;
        if (rj.containsKey("LeftUpperLeg") && rj.containsKey("RightUpperLeg")) {
            hipY = (rj.get("LeftUpperLeg")[1] + rj.get("RightUpperLeg")[1]) / 2; // la cadera de verdad, del esqueleto
        } else if (box[BODY] != null && box[BODY][1] > feet + 0.1f * (neckY - feet) && box[BODY][3] - box[BODY][0] > 0.35f * headW) {
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
        if (box[RIGHT_LEG] == null || box[LEFT_LEG] == null) {
            // sin piernas propias (las hace la ropa 3D, o faltan): ancho y fondo de lo que hay debajo de la cadera
            float lx0 = Float.MAX_VALUE, lx1 = -Float.MAX_VALUE, lz0 = Float.MAX_VALUE, lz1 = -Float.MAX_VALUE;
            for (Group g : handles) {
                for (int[] t : g.tris) {
                    for (int k = 0; k < 3; k++) {
                        float[] q = V[t[k]];
                        if (q[1] < hipY) {
                            lx0 = Math.min(lx0, q[0]);
                            lx1 = Math.max(lx1, q[0]);
                            lz0 = Math.min(lz0, q[2]);
                            lz1 = Math.max(lz1, q[2]);
                        }
                    }
                }
            }
            if (lx0 < lx1) {
                float shrink = 0.15f * (lx1 - lx0);
                if (box[RIGHT_LEG] == null) {
                    box[RIGHT_LEG] = new float[]{cx, feet, lz0, lx1 - shrink, hipY, lz1};
                }
                if (box[LEFT_LEG] == null) {
                    box[LEFT_LEG] = new float[]{lx0 + shrink, feet, lz0, cx, hipY, lz1};
                }
            }
        }
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
        Set<String> clothing = strings(opt, "ropa");
        Map<Group, boolean[]> clothAllow = new HashMap<>();
        Set<String> rigid = strings(opt, "rigido");
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
                // ropa 3D: pegada al cuerpo (casi todos sus vertices cerca de alguna parte) y cubriendo
                // mas de una parte (torso y piernas, torso y brazos...). Va con piel, no rigida.
                int partsCovered = 0;
                for (int b = 1; b < 6; b++) {
                    if (count[b] > 0.1f * total) {
                        partsCovered++;
                    }
                }
                // a un costado (pulseras, puños, cosas de una sola mano o pierna): de ese brazo o pierna, solido
                float half = box[BODY] != null ? (box[BODY][3] - box[BODY][0]) / 2 : headW / 2;
                boolean sideOnly = !clothing.contains(g.name) && Math.abs(g.cx() - cx) > 0.9f * half;
                if (sideOnly) {
                    int sideBest = g.cy() < hipY ? (g.cx() > cx ? RIGHT_LEG : LEFT_LEG) : (g.cx() > cx ? RIGHT_ARM : LEFT_ARM);
                    java.util.Arrays.fill(p, sideBest);
                    triPart.put(g, p);
                    res.log.add(g.name + ": de un costado (" + PART_NAMES[sideBest] + ")");
                    continue;
                }
                boolean cloth = clothing.contains(g.name) || (!rigid.contains(g.name) && partsCovered >= 2
                        && hugs(V, g, box, 0.06f * (neckY - feet)));
                if (cloth) {
                    // solo sigue a las partes que de verdad cubre (un pantalon no se dobla con las manos que cuelgan cerca)
                    boolean[] allow = new boolean[6];
                    allow[BODY] = true;
                    for (int b = 2; b < 6; b++) {
                        allow[b] = count[b] > 0.1f * total;
                    }
                    if (allow[RIGHT_LEG] || allow[LEFT_LEG]) {
                        allow[RIGHT_LEG] = allow[LEFT_LEG] = true;
                    }
                    if (allow[RIGHT_ARM] && allow[LEFT_ARM] || clothAllowArmsBoth(count, total)) {
                        allow[RIGHT_ARM] = allow[LEFT_ARM] = true;
                    }
                    clothAllow.put(g, allow);
                    java.util.Arrays.fill(p, SKIN);
                    triPart.put(g, p);
                    res.log.add(g.name + ": ropa 3D (con piel)");
                    continue;
                }
                // accesorio rigido (alas, mochilas, cosas que flotan): entero a la parte que mas toca; si toca los
                // dos lados (alas, capas, cinturones) va con el torso, no con un brazo o una pierna
                if (!split.contains(g.name)) {
                    int best = BODY;
                    for (int b = 0; b < 6; b++) {
                        if (count[b] > count[best]) {
                            best = b;
                        }
                    }
                    int right = count[RIGHT_ARM] + count[RIGHT_LEG], left = count[LEFT_ARM] + count[LEFT_LEG];
                    boolean crosses = g.min[0] < cx - 0.2f * headW && g.max[0] > cx + 0.2f * headW;
                    if (best != HEAD && (right > 0.15f * total && left > 0.15f * total || crosses)) {
                        best = BODY;
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

        // --- codos y rodillas: hasta donde llega el brazo (o el muslo), medido a lo largo del miembro
        float[] bendAt = new float[PARTS];
        float[] limbLen = new float[PARTS], limbRad = new float[PARTS];
        float[][] limbDir = new float[PARTS][];
        boolean[] byGroup = new boolean[PARTS];
        for (int side = RIGHT_ARM; side <= LEFT_LEG; side++) {
            boolean arm = side <= LEFT_ARM;
            float[] o = joint[side];
            float[] d = arm && armDir[side] != null ? armDir[side] : new float[]{0, -1};
            limbDir[side] = d;
            List<float[]> grp = new ArrayList<>(); // centro, minimo y maximo de cada pieza, a lo largo del miembro
            float len = 0;
            for (int i = 0; i < body.size(); i++) {
                if (role[i] != side) {
                    continue;
                }
                float mn = Float.MAX_VALUE, mx = -Float.MAX_VALUE, sum = 0;
                int c = 0;
                for (int[] t : body.get(i).tris) {
                    for (int k = 0; k < 3; k++) {
                        float sp = along(V[t[k]], o, d);
                        float[] q = V[t[k]];
                        limbRad[side] = Math.max(limbRad[side], Math.abs(-(q[0] - o[0]) * d[1] + (q[1] - o[1]) * d[0]));
                        mn = Math.min(mn, sp);
                        mx = Math.max(mx, sp);
                        sum += sp;
                        c++;
                    }
                }
                grp.add(new float[]{sum / c, mn, mx});
                len = Math.max(len, mx);
            }
            if (!arm) {
                len = Math.max(len, hipY - feet);
            }
            if (len <= 0) {
                len = 0.45f * (neckY - feet);
            }
            grp.sort((a, b) -> Float.compare(a[0], b[0]));
            byGroup[side] = grp.size() >= 2;
            float t = -1;
            String how = "estimado";
            if (grp.size() >= 2) {
                // entre la pieza de arriba (muslo, brazo) y la siguiente (pantorrilla, antebrazo)
                float b = (grp.get(0)[2] + grp.get(1)[1]) / 2;
                if (b > 0.3f * len && b < 0.72f * len) {
                    t = b;
                    how = "piezas del cuerpo";
                }
            }
            String pre = side == RIGHT_ARM || side == RIGHT_LEG ? "Right" : "Left";
            if (t < 0 && !arm && rj.containsKey(pre + "LowerLeg")) {
                float b = hipY - rj.get(pre + "LowerLeg")[1];
                if (b > 0.15f * len && b < 0.85f * len) {
                    t = b;
                    how = "glTF";
                }
            }
            if (t < 0 && arm && rig != null) {
                float[] sh = rig.joints.get(pre + "UpperArm"), el = rig.joints.get(pre + "LowerArm"), wr = rig.joints.get(pre + "Hand");
                float[] hb = rig.bounds.get(pre + "Hand");
                if (sh != null && el != null && wr != null && hb != null) {
                    float ux = wr[0] - sh[0], uy = wr[1] - sh[1], uz = wr[2] - sh[2];
                    float ul = (float) Math.sqrt(ux * ux + uy * uy + uz * uz) + 1e-6f;
                    float far = 0;
                    for (int c = 0; c < 8; c++) {
                        float x = hb[(c & 1) == 0 ? 0 : 3], y = hb[(c & 2) == 0 ? 1 : 4], z = hb[(c & 4) == 0 ? 2 : 5];
                        far = Math.max(far, ((x - sh[0]) * ux + (y - sh[1]) * uy + (z - sh[2]) * uz) / ul);
                    }
                    float r = ((el[0] - sh[0]) * ux + (el[1] - sh[1]) * uy + (el[2] - sh[2]) * uz) / ul / Math.max(1e-4f, far);
                    if (r > 0.25f && r < 0.75f) {
                        t = r * len;
                        how = "glTF";
                    }
                }
            }
            if (t < 0) {
                t = (arm ? 0.47f : 0.5f) * len;
            }
            bendAt[side] = t;
            limbLen[side] = len;
            if (limbRad[side] <= 0) {
                limbRad[side] = 0.12f * (neckY - feet);
            }
            res.log.add((arm ? "codo " : "rodilla ") + PART_NAMES[side] + ": " + how + " (" + Math.round(100 * t / len) + "%)");
        }
        // cada pieza (o triangulo, si el miembro es una sola malla) va arriba o abajo del codo/rodilla
        for (Map.Entry<Group, int[]> e : triPart.entrySet()) {
            Group g = e.getKey();
            int[] p = e.getValue();
            int bi = body.indexOf(g);
            boolean whole = bi >= 0 ? byGroup[Math.max(0, Math.min(PARTS - 1, role[bi]))] : !split.contains(g.name);
            if (whole && p.length > 0) {
                int side = p[0];
                boolean same = true;
                for (int v : p) {
                    same &= v == side;
                }
                if (same && side >= RIGHT_ARM && side <= LEFT_LEG) {
                    float sum = 0;
                    int c = 0;
                    for (int[] t : g.tris) {
                        for (int k = 0; k < 3; k++) {
                            sum += along(V[t[k]], joint[side], limbDir[side]);
                            c++;
                        }
                    }
                    if (sum / c > bendAt[side]) {
                        java.util.Arrays.fill(p, side + 4);
                    }
                    continue;
                }
            }
            for (int i = 0; i < p.length; i++) {
                int side = p[i];
                if (side < RIGHT_ARM || side > LEFT_LEG) {
                    continue;
                }
                int[] t = g.tris.get(i);
                float sp = 0;
                for (int k = 0; k < 3; k++) {
                    sp += along(V[t[k]], joint[side], limbDir[side]) / 3;
                }
                if (sp > bendAt[side]) {
                    p[i] = side + 4;
                }
            }
        }

        // --- pesos de la ropa 3D: cuanto sigue cada vertice a cada parte (suave cerca de las articulaciones)
        Map<Integer, float[]> weights = new HashMap<>();
        float sigma = 0.06f * (neckY - feet);
        // entrepierna: un poco debajo de la cadera; mas abajo cada pierna va sola
        float crotchY = hipY - 0.12f * (hipY - feet);
        float band = 0.03f * (neckY - feet);
        for (Map.Entry<Group, int[]> e : triPart.entrySet()) {
            List<int[]> tris = e.getKey().tris;
            for (int i = 0; i < tris.size(); i++) {
                if (e.getValue()[i] != SKIN) {
                    continue;
                }
                for (int k = 0; k < 3; k++) {
                    int vi = tris.get(i)[k];
                    if (!weights.containsKey(vi)) {
                        weights.put(vi, skinWeights(box, V[vi], sigma, clothAllow.get(e.getKey()), cx, hipY, crotchY, band));
                    }
                }
            }
        }

        // --- pantalones anchos: las piernas se tocan en el medio. Cada vertice va con la pierna del tubo al
        // que pertenece (recorriendo la superficie desde abajo de cada pierna), no con el lado del centro.
        for (Map.Entry<Group, boolean[]> e : clothAllow.entrySet()) {
            boolean[] al = e.getValue();
            if (al[RIGHT_LEG] || al[LEFT_LEG]) {
                legTubes(V, e.getKey(), weights, cx, hipY, crotchY);
            }
        }
        // --- la tela tambien se estira en el codo y la rodilla: el peso de cada miembro se reparte entre
        // la parte de arriba y la de abajo, con una franja suave alrededor de la articulacion
        for (Map.Entry<Integer, float[]> e : weights.entrySet()) {
            // ropa solida: cada vertice queda pegado entero a una sola parte. Debajo de la cadera, a su pierna
            // (la del tubo del pantalon al que pertenece); arriba, al torso o al brazo que tiene mas cerca.
            float[] w6 = e.getValue();
            float[] p = V[e.getKey()];
            int o;
            float legW = w6[RIGHT_LEG] + w6[LEFT_LEG];
            if (p[1] < hipY && legW > 0) {
                o = w6[RIGHT_LEG] > w6[LEFT_LEG] ? RIGHT_LEG : w6[LEFT_LEG] > w6[RIGHT_LEG] ? LEFT_LEG : p[0] >= cx ? RIGHT_LEG : LEFT_LEG;
            } else {
                // torso o brazo: la distancia al brazo se mide a su tubo (el brazo puede venir abierto en diagonal
                // y su caja taparia el costado del torso)
                o = BODY;
                float best = box[BODY] == null ? Float.MAX_VALUE : (float) Math.sqrt(dist(box[BODY], p[0], p[1], p[2]));
                for (int b : new int[]{RIGHT_ARM, LEFT_ARM}) {
                    if (w6[b] <= 0 || limbDir[b] == null) {
                        continue;
                    }
                    float tt = Math.max(0, Math.min(limbLen[b], along(p, joint[b], limbDir[b])));
                    float qx = joint[b][0] + limbDir[b][0] * tt - p[0], qy = joint[b][1] + limbDir[b][1] * tt - p[1], qz = joint[b][2] - p[2];
                    float da = Math.max(0, (float) Math.sqrt(qx * qx + qy * qy + qz * qz) - limbRad[b]);
                    // empate (adentro de los dos): si esta mas afuera que el costado del torso, es manga
                    boolean outside = box[BODY] != null && Math.abs(p[0] - cx) > 0.9f * (box[BODY][3] - box[BODY][0]) / 2;
                    if (da < best || da == best && outside) {
                        best = da;
                        o = b;
                    }
                }
            }
            if (o >= RIGHT_ARM && o <= LEFT_LEG && along(p, joint[o], limbDir[o]) > bendAt[o]) {
                o += 4;
            }
            float[] w = new float[PARTS];
            w[o] = 1;
            e.setValue(w);
        }

        // --- bajar los brazos abiertos (giro alrededor del hombro, en el plano del frente)
        boolean lower = !opt.has("brazos_abajo") || opt.get("brazos_abajo").getAsBoolean();
        Set<Integer> moved = new HashSet<>();
        float[] lowered = new float[PARTS];
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
                        if (e.getValue()[i] == side || e.getValue()[i] == side + 4) {
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
                    turned.put(vi, (float) phi);
                }
                // la ropa 3D se dobla segun cuanto sigue a ese brazo (la manga baja entera, el torso no)
                for (Map.Entry<Integer, float[]> w : weights.entrySet()) {
                    float a = w.getValue()[side] + w.getValue()[side + 4];
                    if (a < 1e-3f) {
                        continue;
                    }
                    float cc = (float) Math.cos(phi * a), ss = (float) Math.sin(phi * a);
                    float[] p = V[w.getKey()];
                    float qx = p[0] - sh[0], qy = p[1] - sh[1];
                    V[w.getKey()] = new float[]{cc * qx - ss * qy + sh[0], ss * qx + cc * qy + sh[1], p[2]};
                    turned.merge(w.getKey(), (float) (phi * a), Float::sum);
                }
                lowered[side] = (float) phi;
                res.log.add(PART_NAMES[side] + " bajado " + Math.round(Math.toDegrees(phi)) + " grados");
            }
        }

        // --- escala y paso al espacio del jugador de Minecraft
        float S = 32f / (headTop - feet);
        for (int i = 0; i < 6; i++) {
            res.joints[i] = toModel(joint[i], cx, cz, feet, S);
        }
        for (int side = RIGHT_ARM; side <= LEFT_LEG; side++) {
            float[] o = joint[side], d = limbDir[side];
            float c = (float) Math.cos(lowered[side]), sn = (float) Math.sin(lowered[side]);
            float dx = c * d[0] - sn * d[1], dy = sn * d[0] + c * d[1];
            res.joints[side + 4] = toModel(new float[]{o[0] + dx * bendAt[side], o[1] + dy * bendAt[side], o[2]}, cx, cz, feet, S);
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
        Map<Integer, FloatList> seamAcc = new HashMap<>();
        List<Map<Integer, FloatList>> acc = new ArrayList<>();
        for (int i = 0; i < PARTS; i++) {
            acc.add(new HashMap<>());
        }
        Set<Group> handleSet = new HashSet<>(handles);
        Map<String, String> accNames = map(opt, "nombres");
        Map<Group, Accessory> accOf = new LinkedHashMap<>();
        Map<Group, Map<Integer, FloatList>> accAcc = new HashMap<>();
        for (Map.Entry<Group, int[]> e : triPart.entrySet()) {
            Group g = e.getKey();
            int[] parts = e.getValue();
            int mi = matIndex.get(g.material == null ? "" : g.material);
            float[][][] normals = smoothNormals(V, g.tris, 55);
            // accesorio rigido: todo el grupo en una sola parte, sin piel
            Map<Integer, FloatList> accTarget = null;
            if (handleSet.contains(g) && !clothAllow.containsKey(g) && !split.contains(g.name) && parts.length > 0 && parts[0] != SKIN) {
                boolean uniform = true;
                for (int pp : parts) {
                    if (pp != parts[0]) {
                        uniform = false;
                        break;
                    }
                }
                if (uniform) {
                    Accessory a = accOf.computeIfAbsent(g, x -> {
                        Accessory na = new Accessory(x.name, parts[0]);
                        na.name = accNames.getOrDefault(x.name, x.name);
                        return na;
                    });
                    accTarget = accAcc.computeIfAbsent(g, x -> new HashMap<>());
                    a.triangles += g.tris.size();
                }
            }
            for (int i = 0; i < g.tris.size(); i++) {
                int[] t = g.tris.get(i);
                int part = parts[i];
                if (part == SKIN) {
                    int p0 = owner(weights.get(t[0])), p1 = owner(weights.get(t[1])), p2 = owner(weights.get(t[2]));
                    if (p0 == p1 && p1 == p2) {
                        part = p0; // entero en una parte: va solido con ella
                    } else if (!canStretch(p0, p1) || !canStretch(p1, p2) || !canStretch(p0, p2)) {
                        // entre las piernas o entre la cadera y una pierna no se estira nada: el triangulo va
                        // entero con la parte de la mayoria (como un pedazo solido), asi las piernas se separan limpias
                        part = p0 == p1 || p0 == p2 ? p0 : p1 == p2 ? p1 : Math.min(p0, Math.min(p1, p2));
                    }
                }
                if (part == SKIN) {
                    FloatList skin = seamAcc.computeIfAbsent(mi, x -> new FloatList());
                    float mu = 0, mv = 0;
                    for (int k = 0; k < 3; k++) {
                        float[] uv = t[3 + k] >= 0 && t[3 + k] < VT.length ? VT[t[3 + k]] : new float[]{0, 0};
                        mu += uv[0] / 3;
                        mv += uv[1] / 3;
                    }
                    float su = (float) Math.floor(mu), sv = (float) Math.floor(mv);
                    for (int k = 0; k < 3; k++) {
                        float[] m = toModel(V[t[k]], cx, cz, feet, S);
                        float[] nn = normalOf(t, k, VN, turned, normals[i][k]);
                        float[] uv = t[3 + k] >= 0 && t[3 + k] < VT.length ? VT[t[3 + k]] : new float[]{0, 0};
                        skin.add(m[0], m[1], m[2], -nn[0], -nn[1], nn[2], uv[0] - su, 1 - (uv[1] - sv), owner(weights.get(t[k])));
                    }
                    res.triangles++;
                    continue;
                }
                FloatList fl = (accTarget != null ? accTarget : acc.get(part)).computeIfAbsent(mi, x -> new FloatList());
                float mu = 0, mv = 0;
                for (int k = 0; k < 3; k++) {
                    float[] uv = t[3 + k] >= 0 && t[3 + k] < VT.length ? VT[t[3 + k]] : new float[]{0, 0};
                    mu += uv[0] / 3;
                    mv += uv[1] / 3;
                }
                float su = (float) Math.floor(mu), sv = (float) Math.floor(mv);
                for (int k = 0; k < 3; k++) {
                    float[] m = toModel(V[t[k]], cx, cz, feet, S);
                    float[] nn = normalOf(t, k, VN, turned, normals[i][k]);
                    float[] uv = t[3 + k] >= 0 && t[3 + k] < VT.length ? VT[t[3 + k]] : new float[]{0, 0};
                    fl.add(m[0], m[1], m[2], -nn[0], -nn[1], nn[2], uv[0] - su, 1 - (uv[1] - sv));
                }
                res.triangles++;
            }
        }
        for (int i = 0; i < PARTS; i++) {
            List<Batch> list = new ArrayList<>();
            for (Map.Entry<Integer, FloatList> e : acc.get(i).entrySet()) {
                list.add(new Batch(e.getKey(), i, e.getValue().toArray()));
            }
            res.parts.add(list);
        }
        for (Map.Entry<Integer, FloatList> e : seamAcc.entrySet()) {
            res.seams.add(new Batch(e.getKey(), -1, e.getValue().toArray()));
        }
        for (Map.Entry<Group, Accessory> e : accOf.entrySet()) {
            Accessory a = e.getValue();
            float[] mn = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE}, mx = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
            for (Map.Entry<Integer, FloatList> b : accAcc.get(e.getKey()).entrySet()) {
                float[] d = b.getValue().toArray();
                a.batches.add(new Batch(b.getKey(), a.part, d));
                for (int o = 0; o < d.length; o += 8) {
                    for (int k = 0; k < 3; k++) {
                        mn[k] = Math.min(mn[k], d[o + k]);
                        mx[k] = Math.max(mx[k], d[o + k]);
                    }
                }
            }
            for (int k = 0; k < 3; k++) {
                a.center[k] = mn[k] <= mx[k] ? (mn[k] + mx[k]) / 2 : 0;
            }
            res.accessories.add(a);
        }
        res.log.add("accesorios intercambiables: " + res.accessories.size());
        for (int m = 0; m < res.materials.size(); m++) {
            res.byMaterial.add(new ArrayList<>());
        }
        for (List<Batch> l : res.parts) {
            for (Batch b : l) {
                res.byMaterial.get(b.material).add(b);
            }
        }
        for (Batch b : res.seams) {
            res.byMaterial.get(b.material).add(b);
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

    /** Si la pieza va pegada al cuerpo: la mayoria de sus vertices a menos de maxD de alguna parte. */
    private static boolean hugs(float[][] V, Group g, float[][] box, float maxD) {
        int near = 0, n = 0;
        float lim = maxD * maxD;
        for (int[] t : g.tris) {
            for (int k = 0; k < 3; k++) {
                float[] p = V[t[k]];
                float best = Float.MAX_VALUE;
                for (int b = 1; b < 6; b++) {
                    if (box[b] != null) {
                        best = Math.min(best, dist(box[b], p[0], p[1], p[2]));
                    }
                }
                if (best <= lim) {
                    near++;
                }
                n++;
            }
        }
        return near >= 0.75f * n;
    }

    /** Pesos de un vertice de ropa: segun lo cerca que esta de cada parte (no la cabeza), suavizado. */
    /**
     * Separa las dos piernas de una prenda por la superficie: desde los vertices de abajo de cada lado se
     * recorre la malla (camino mas corto por las aristas) y cada vertice queda con la pierna que llega
     * primero. Debajo de la entrepierna esa pierna se lleva todo el peso de pierna.
     */
    private static void legTubes(float[][] V, Group g, Map<Integer, float[]> weights, float cx, float hipY, float crotchY) {
        // vertices soldados por posicion (Roblox duplica en las costuras de la textura)
        Map<Long, Integer> idOf = new HashMap<>();
        Map<Integer, Integer> node = new HashMap<>();
        List<float[]> pos = new ArrayList<>();
        for (int[] t : g.tris) {
            for (int k = 0; k < 3; k++) {
                int vi = t[k];
                if (node.containsKey(vi)) {
                    continue;
                }
                long key = key(V[vi]);
                Integer id = idOf.get(key);
                if (id == null) {
                    id = pos.size();
                    idOf.put(key, id);
                    pos.add(V[vi]);
                }
                node.put(vi, id);
            }
        }
        int n = pos.size();
        List<List<int[]>> adj = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            adj.add(new ArrayList<>());
        }
        float minY = Float.MAX_VALUE;
        for (float[] p : pos) {
            minY = Math.min(minY, p[1]);
        }
        for (int[] t : g.tris) {
            for (int k = 0; k < 3; k++) {
                int a = node.get(t[k]), b = node.get(t[(k + 1) % 3]);
                if (a != b) {
                    adj.get(a).add(new int[]{b});
                    adj.get(b).add(new int[]{a});
                }
            }
        }
        // semillas: lo mas bajo de cada lado (los botamangas), bien separado del centro
        float[] dist = new float[n];
        int[] label = new int[n];
        java.util.Arrays.fill(dist, Float.MAX_VALUE);
        java.util.PriorityQueue<float[]> q = new java.util.PriorityQueue<>((x, y) -> Float.compare(x[0], y[0]));
        float span = 0;
        for (float[] p : pos) {
            span = Math.max(span, Math.abs(p[0] - cx));
        }
        float lowBand = minY + 0.2f * (crotchY - minY);
        int seeds = 0;
        for (int i = 0; i < n; i++) {
            float[] p = pos.get(i);
            if (p[1] <= lowBand && Math.abs(p[0] - cx) > 0.3f * span) {
                dist[i] = 0;
                label[i] = p[0] >= cx ? RIGHT_LEG : LEFT_LEG;
                q.add(new float[]{0, i});
                seeds++;
            }
        }
        if (seeds == 0) {
            return;
        }
        while (!q.isEmpty()) {
            float[] top = q.poll();
            int i = (int) top[1];
            if (top[0] > dist[i]) {
                continue;
            }
            float[] p = pos.get(i);
            for (int[] e : adj.get(i)) {
                int j = e[0];
                float[] o = pos.get(j);
                if (o[1] > hipY) {
                    continue; // por encima de la cadera no hace falta (ahi manda el torso)
                }
                float dx = o[0] - p[0], dy = o[1] - p[1], dz = o[2] - p[2];
                float nd = dist[i] + (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (nd < dist[j]) {
                    dist[j] = nd;
                    label[j] = label[i];
                    q.add(new float[]{nd, j});
                }
            }
        }
        for (Map.Entry<Integer, Integer> e : node.entrySet()) {
            int id = e.getValue();
            float[] p = pos.get(id);
            float[] w = weights.get(e.getKey());
            if (w == null || dist[id] == Float.MAX_VALUE || p[1] >= crotchY) {
                continue;
            }
            float leg = w[RIGHT_LEG] + w[LEFT_LEG];
            w[RIGHT_LEG] = label[id] == RIGHT_LEG ? leg : 0;
            w[LEFT_LEG] = label[id] == LEFT_LEG ? leg : 0;
        }
    }

    private static boolean clothAllowArmsBoth(int[] count, int total) {
        return false;
    }

    /**
     * Pesos de un vertice de ropa 3D. Entre el torso y una pierna (o un brazo) se mezcla suave segun la
     * distancia, como la cintura o el hombro de una prenda en Roblox. Entre pierna derecha e izquierda
     * NO se mezcla: cada pierna del pantalon sigue solo a su pierna (salvo una franja finita en el medio
     * de la cintura, arriba de la entrepierna), asi al caminar se separan.
     */
    private static float[] skinWeights(float[][] box, float[] p, float sigma, boolean[] allow, float cx, float hipY, float crotchY,
                                       float band) {
        float[] w = new float[6];
        float s2 = sigma * sigma;
        float dBody = box[BODY] == null ? Float.MAX_VALUE : dist(box[BODY], p[0], p[1], p[2]);
        boolean legs = allow == null || allow[RIGHT_LEG] || allow[LEFT_LEG];
        float dLeg = Float.MAX_VALUE;
        if (legs) {
            dLeg = Math.min(box[RIGHT_LEG] == null ? Float.MAX_VALUE : dist(box[RIGHT_LEG], p[0], p[1], p[2]),
                    box[LEFT_LEG] == null ? Float.MAX_VALUE : dist(box[LEFT_LEG], p[0], p[1], p[2]));
        }
        float[] d = new float[6];
        java.util.Arrays.fill(d, Float.MAX_VALUE);
        d[BODY] = dBody;
        for (int b : new int[]{RIGHT_ARM, LEFT_ARM}) {
            if (box[b] != null && (allow == null || allow[b])) {
                d[b] = dist(box[b], p[0], p[1], p[2]);
            }
        }
        float min = Math.min(dBody, dLeg);
        for (int b : new int[]{RIGHT_ARM, LEFT_ARM}) {
            min = Math.min(min, d[b]);
        }
        float wb = dBody == Float.MAX_VALUE ? 0 : (float) Math.exp(-(dBody - min) / s2);
        float wl = dLeg == Float.MAX_VALUE ? 0 : (float) Math.exp(-(dLeg - min) / s2);
        // debajo de la cadera no hay torso: la pierna manda (si no, el pantalon quedaria pegado al cuerpo)
        if (legs && p[1] < hipY) {
            float k = Math.min(1, (hipY - p[1]) / Math.max(1e-3f, hipY - crotchY));
            wb *= 1 - k;
        }
        w[BODY] = wb;
        for (int b : new int[]{RIGHT_ARM, LEFT_ARM}) {
            w[b] = d[b] == Float.MAX_VALUE ? 0 : (float) Math.exp(-(d[b] - min) / s2);
        }
        // de que lado: Roblox mira a -z, su derecha es +x
        float side;
        if (p[1] < crotchY) {
            side = p[0] >= cx ? 1 : 0;
        } else {
            side = Math.max(0, Math.min(1, 0.5f + (p[0] - cx) / (2 * band)));
        }
        w[RIGHT_LEG] = wl * side;
        w[LEFT_LEG] = wl * (1 - side);
        float sum = 0;
        for (int b = 1; b < 6; b++) {
            sum += w[b];
        }
        if (sum <= 0) {
            w[BODY] = 1;
            return w;
        }
        for (int b = 1; b < 6; b++) {
            w[b] /= sum;
            if (w[b] < 0.02f) {
                w[b] = 0;
            }
        }
        sum = 0;
        for (int b = 1; b < 6; b++) {
            sum += w[b];
        }
        for (int b = 1; b < 6; b++) {
            w[b] /= sum;
        }
        return w;
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

    /**
     * Si la tela puede estirarse entre dos partes: dentro de un mismo brazo o pierna (codo, rodilla) y
     * entre el torso y un brazo (hombro). Nunca entre piernas ni entre la cadera y una pierna.
     */
    private static boolean canStretch(int a, int b) {
        if (a == b) {
            return true;
        }
        int la = a >= RIGHT_FOREARM ? a - 4 : a, lb = b >= RIGHT_FOREARM ? b - 4 : b;
        if (la == lb && la >= RIGHT_ARM) {
            return true;
        }
        return (a == BODY && (b == RIGHT_ARM || b == LEFT_ARM)) || (b == BODY && (a == RIGHT_ARM || a == LEFT_ARM));
    }

    /** La parte que se lleva un vertice de ropa 3D: la de mas peso (la ropa va solida, sin mezclar). */
    private static int owner(float[] w) {
        int best = BODY;
        for (int i = 0; i < w.length; i++) {
            if (w[i] > w[best]) {
                best = i;
            }
        }
        return best;
    }

    /** Distancia a lo largo de un miembro (en el plano del frente), desde su articulacion. */
    private static float along(float[] p, float[] o, float[] d) {
        return (p[0] - o[0]) * d[0] + (p[1] - o[1]) * d[1];
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
            case "antebrazo_der", "mano_der", "rightlowerarm", "righthand" -> RIGHT_FOREARM;
            case "antebrazo_izq", "mano_izq", "leftlowerarm", "lefthand" -> LEFT_FOREARM;
            case "pantorrilla_der", "pie_der", "rightlowerleg", "rightfoot" -> RIGHT_SHIN;
            case "pantorrilla_izq", "pie_izq", "leftlowerleg", "leftfoot" -> LEFT_SHIN;
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

    /** La normal que trae el archivo (como la sombrea Roblox), girada si ese vertice se giro al bajar el brazo. */
    private static float[] normalOf(int[] t, int k, float[][] VN, Map<Integer, Float> turned, float[] fallback) {
        float[] n = t.length > 6 && t[6 + k] >= 0 && t[6 + k] < VN.length ? unit(VN[t[6 + k]]) : fallback;
        if (n[0] == 0 && n[1] == 0 && n[2] == 0) {
            n = fallback;
        }
        Float a = turned.get(t[k]);
        if (a == null || a == 0) {
            return n;
        }
        float c = (float) Math.cos(a), s = (float) Math.sin(a);
        return new float[]{c * n[0] - s * n[1], s * n[0] + c * n[1], n[2]};
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
