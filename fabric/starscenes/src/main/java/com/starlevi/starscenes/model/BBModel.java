package com.starlevi.starscenes.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Un modelo de Blockbench (.bbmodel, formato libre o de entidad) listo para animar, sin nada de
 * Minecraft. Lee los huesos (grupos), los cubos y mallas con sus caras y UV, las texturas que vienen
 * adentro del archivo y las animaciones con sus cuadros clave.
 * <p>
 * Todo queda en el espacio de Blockbench: pixeles, y hacia arriba, frente hacia el norte (-z). Las
 * animaciones se aplican como las muestra Blockbench (rotacion en orden Z, Y, X; en X e Y con el
 * signo invertido, igual que la posicion en X).
 */
public final class BBModel {
    public static final int ROT = 0, POS = 1, SCALE = 2;

    public static final class Quad {
        public final int texture;
        /** 4 vertices (x, y, z); un triangulo repite el ultimo. */
        public final float[] xyz = new float[12];
        public final float[] uv = new float[8];
        public float nx, ny, nz;

        Quad(int texture) {
            this.texture = texture;
        }
    }

    public static final class Bone {
        public final String name;
        public final int parent;
        public final float[] origin;
        public final float[] rotation;
        public final List<Quad> quads = new ArrayList<>();
        /** No se dibuja (la caja de golpe, por ejemplo). */
        public boolean hidden;

        Bone(String name, int parent, float[] origin, float[] rotation) {
            this.name = name;
            this.parent = parent;
            this.origin = origin;
            this.rotation = rotation;
        }
    }

    public static final class Texture {
        public final String name;
        public final byte[] png;

        Texture(String name, byte[] png) {
            this.name = name;
            this.png = png;
        }
    }

    public static final class Key {
        final float time;
        final boolean catmull, step;
        final float[] pre, post;

        Key(float time, boolean catmull, boolean step, float[] pre, float[] post) {
            this.time = time;
            this.catmull = catmull;
            this.step = step;
            this.pre = pre;
            this.post = post;
        }
    }

    public static final class Anim {
        public final String name;
        public final float length;
        /** "loop", "once" o "hold". */
        public final String loop;
        /** [hueso][canal] -> cuadros clave (null si ese hueso no se mueve en ese canal). */
        final Key[][][] keys;

        /** Si esta animacion mueve (rota) ese hueso. */
        public boolean rotates(int bone) {
            return bone < this.keys.length && this.keys[bone][ROT] != null;
        }

        /** Si esta animacion mueve (desplaza) ese hueso. */
        public boolean moves(int bone) {
            return bone < this.keys.length && this.keys[bone][POS] != null;
        }

        Anim(String name, float length, String loop, int bones) {
            this.name = name;
            this.length = length;
            this.loop = loop;
            this.keys = new Key[bones][3][];
        }

        public boolean loops() {
            return "loop".equals(this.loop);
        }
    }

    public final List<Bone> bones = new ArrayList<>();
    public final List<Texture> textures = new ArrayList<>();
    public final Map<String, Anim> anims = new LinkedHashMap<>();
    /** Caja de golpe en bloques (del hueso "hitbox"). */
    public float hitWidth = 0.6f, hitHeight = 1.8f;
    /** Altura del nombre en pixeles (hueso "tag_..."), o -1. */
    public float tagY = -1;
    public float maxY;
    public int headBone = -1;
    public final List<String> warnings = new ArrayList<>();

    private BBModel() {
    }

    /**
     * Una copia de este modelo (mismas piezas) que ademas tiene las animaciones de otros modelos con
     * huesos del mismo nombre. Las de este modelo tienen prioridad, salvo las de las fuentes marcadas en
     * override (esas reemplazan a las que se llaman igual: asi tus animaciones pisan a las que trae el mod).
     */
    public BBModel withAnimationsFrom(List<BBModel> sources) {
        return withAnimationsFrom(sources, java.util.Set.of());
    }

    public BBModel withAnimationsFrom(List<BBModel> sources, java.util.Set<BBModel> override) {
        BBModel c = new BBModel();
        c.bones.addAll(this.bones);
        c.textures.addAll(this.textures);
        c.anims.putAll(this.anims);
        c.hitWidth = this.hitWidth;
        c.hitHeight = this.hitHeight;
        c.tagY = this.tagY;
        c.maxY = this.maxY;
        c.headBone = this.headBone;
        for (BBModel src : sources) {
            int[] map = new int[src.bones.size()];
            for (int i = 0; i < map.length; i++) {
                map[i] = c.boneIndex(src.bones.get(i).name);
            }
            for (Anim a : src.anims.values()) {
                if (c.anims.containsKey(a.name) && !override.contains(src)) {
                    continue;
                }
                Anim n = new Anim(a.name, a.length, a.loop, c.bones.size());
                for (int i = 0; i < map.length; i++) {
                    if (map[i] > 0 && i < a.keys.length) {
                        n.keys[map[i]] = a.keys[i];
                    }
                }
                c.anims.put(a.name, n);
            }
        }
        return c;
    }

    // ------------------------------------------------------------------ lectura

    public static BBModel parse(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        BBModel m = new BBModel();
        float resW = 16, resH = 16;
        if (root.has("resolution")) {
            JsonObject r = root.getAsJsonObject("resolution");
            resW = r.get("width").getAsFloat();
            resH = r.get("height").getAsFloat();
        }
        // texturas (vienen adentro, en base64)
        List<float[]> texUv = new ArrayList<>();
        Map<String, Integer> texByUuid = new HashMap<>();
        if (root.has("textures")) {
            for (JsonElement te : root.getAsJsonArray("textures")) {
                JsonObject t = te.getAsJsonObject();
                String name = t.has("name") ? t.get("name").getAsString() : "textura";
                byte[] png = null;
                if (t.has("source")) {
                    String src = t.get("source").getAsString();
                    int comma = src.indexOf(',');
                    if (src.startsWith("data:") && comma > 0) {
                        try {
                            png = Base64.getDecoder().decode(src.substring(comma + 1));
                        } catch (Exception ignored) {
                            m.warnings.add("textura " + name + " ilegible");
                        }
                    }
                }
                if (t.has("uuid")) {
                    texByUuid.put(t.get("uuid").getAsString(), m.textures.size());
                }
                m.textures.add(new Texture(name, png));
                float uw = t.has("uv_width") ? t.get("uv_width").getAsFloat() : resW;
                float uh = t.has("uv_height") ? t.get("uv_height").getAsFloat() : resH;
                texUv.add(new float[]{uw, uh});
            }
        }
        // elementos por uuid
        Map<String, JsonObject> elements = new HashMap<>();
        if (root.has("elements")) {
            for (JsonElement ee : root.getAsJsonArray("elements")) {
                JsonObject e = ee.getAsJsonObject();
                if (e.has("uuid")) {
                    elements.put(e.get("uuid").getAsString(), e);
                }
            }
        }
        // huesos: el arbol (outliner), padres antes que hijos
        Map<String, Integer> boneByUuid = new HashMap<>();
        Bone top = new Bone("_raiz", -1, new float[3], new float[3]);
        m.bones.add(top);
        // Blockbench 5: los datos de cada grupo (nombre, pivote, giro) estan aparte, en "groups"
        if (root.has("groups") && root.get("groups").isJsonArray()) {
            for (JsonElement ge : root.getAsJsonArray("groups")) {
                if (ge.isJsonObject() && ge.getAsJsonObject().has("uuid")) {
                    m.groupInfo.put(ge.getAsJsonObject().get("uuid").getAsString(), ge.getAsJsonObject());
                }
            }
        }
        if (root.has("outliner")) {
            m.readGroups(root.getAsJsonArray("outliner"), 0, elements, boneByUuid, texUv, texByUuid);
        } else {
            for (JsonObject e : elements.values()) {
                m.addElement(0, e, texUv, texByUuid);
            }
        }
        boneByUuid.put("_raiz", 0);
        // animaciones
        if (root.has("animations")) {
            for (JsonElement ae : root.getAsJsonArray("animations")) {
                JsonObject a = ae.getAsJsonObject();
                String name = a.has("name") ? a.get("name").getAsString() : "anim";
                float len = a.has("length") ? a.get("length").getAsFloat() : 0;
                String loop = a.has("loop") ? a.get("loop").getAsString() : "once";
                Anim an = new Anim(name, len, loop, m.bones.size());
                if (a.has("animators")) {
                    for (Map.Entry<String, JsonElement> ent : a.getAsJsonObject("animators").entrySet()) {
                        JsonObject animator = ent.getValue().getAsJsonObject();
                        Integer bi = boneByUuid.get(ent.getKey());
                        if (bi == null && animator.has("name")) {
                            bi = m.boneIndex(animator.get("name").getAsString());
                        }
                        if (bi == null || bi < 0 || !animator.has("keyframes")) {
                            continue;
                        }
                        List<List<Key>> lists = List.of(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
                        for (JsonElement ke : animator.getAsJsonArray("keyframes")) {
                            JsonObject k = ke.getAsJsonObject();
                            String ch = k.has("channel") ? k.get("channel").getAsString() : "";
                            int c = switch (ch) {
                                case "rotation" -> ROT;
                                case "position" -> POS;
                                case "scale" -> SCALE;
                                default -> -1;
                            };
                            if (c < 0 || !k.has("data_points")) {
                                continue;
                            }
                            float def = c == SCALE ? 1 : 0;
                            JsonArray dps = k.getAsJsonArray("data_points");
                            float[] pre = point(dps.get(0).getAsJsonObject(), def, m.warnings);
                            float[] post = dps.size() > 1 ? point(dps.get(1).getAsJsonObject(), def, m.warnings) : pre;
                            String interp = k.has("interpolation") ? k.get("interpolation").getAsString() : "linear";
                            lists.get(c).add(new Key(k.get("time").getAsFloat(), interp.equals("catmullrom"), interp.equals("step"), pre, post));
                        }
                        for (int c = 0; c < 3; c++) {
                            List<Key> l = lists.get(c);
                            if (!l.isEmpty()) {
                                l.sort((x, y) -> Float.compare(x.time, y.time));
                                an.keys[bi][c] = l.toArray(new Key[0]);
                            }
                        }
                    }
                }
                m.anims.put(name, an);
            }
        }
        for (int i = 0; i < m.bones.size(); i++) {
            String n = m.bones.get(i).name.toLowerCase(Locale.ROOT);
            if (m.headBone < 0 && n.equals("head")) {
                m.headBone = i;
            }
        }
        return m;
    }

    /** Datos de los grupos por uuid (Blockbench 5); solo se usa al leer. */
    private final transient Map<String, JsonObject> groupInfo = new java.util.HashMap<>();

    public int boneIndex(String name) {
        for (int i = 0; i < this.bones.size(); i++) {
            if (this.bones.get(i).name.equals(name)) {
                return i;
            }
        }
        return -1;
    }

    private void readGroups(JsonArray list, int parent, Map<String, JsonObject> elements, Map<String, Integer> boneByUuid, List<float[]> texUv,
                            Map<String, Integer> texByUuid) {
        for (JsonElement c : list) {
            if (c.isJsonPrimitive()) {
                JsonObject e = elements.get(c.getAsString());
                if (e != null) {
                    addElement(parent, e, texUv, texByUuid);
                }
                continue;
            }
            JsonObject g = c.getAsJsonObject();
            if (!g.has("name") && g.has("uuid") && this.groupInfo.containsKey(g.get("uuid").getAsString())) {
                // formato nuevo: se juntan los datos del grupo con sus hijos
                JsonObject info = this.groupInfo.get(g.get("uuid").getAsString()).deepCopy();
                if (g.has("children")) {
                    info.add("children", g.get("children"));
                }
                g = info;
            }
            String name = g.has("name") ? g.get("name").getAsString() : "grupo";
            Bone b = new Bone(name, parent, vec(g, "origin", 0), vec(g, "rotation", 0));
            int idx = this.bones.size();
            this.bones.add(b);
            if (g.has("uuid")) {
                boneByUuid.put(g.get("uuid").getAsString(), idx);
            }
            String low = name.toLowerCase(Locale.ROOT);
            if (low.equals("hitbox") || low.equals("shadow")) {
                b.hidden = true;
            }
            if (low.startsWith("tag_")) {
                this.tagY = b.origin[1];
            }
            if (g.has("children")) {
                readGroups(g.getAsJsonArray("children"), idx, elements, boneByUuid, texUv, texByUuid);
            }
            if (low.equals("hitbox")) {
                // la caja de golpe: lo que ocupan sus cubos
                float x0 = Float.MAX_VALUE, x1 = -Float.MAX_VALUE, y0 = Float.MAX_VALUE, y1 = -Float.MAX_VALUE, z0 = Float.MAX_VALUE, z1 = -Float.MAX_VALUE;
                for (Quad q : b.quads) {
                    for (int k = 0; k < 4; k++) {
                        x0 = Math.min(x0, q.xyz[k * 3]);
                        x1 = Math.max(x1, q.xyz[k * 3]);
                        y0 = Math.min(y0, q.xyz[k * 3 + 1]);
                        y1 = Math.max(y1, q.xyz[k * 3 + 1]);
                        z0 = Math.min(z0, q.xyz[k * 3 + 2]);
                        z1 = Math.max(z1, q.xyz[k * 3 + 2]);
                    }
                }
                if (x0 < x1) {
                    this.hitWidth = Math.max(0.2f, Math.max(x1 - x0, z1 - z0) / 16f);
                    this.hitHeight = Math.max(0.2f, (y1 - Math.min(0, y0)) / 16f);
                }
            }
        }
    }

    private void addElement(int bone, JsonObject e, List<float[]> texUv, Map<String, Integer> texByUuid) {
        String type = e.has("type") ? e.get("type").getAsString() : "cube";
        if (e.has("visibility") && !e.get("visibility").getAsBoolean() && !this.bones.get(bone).name.equalsIgnoreCase("hitbox")) {
            // Blockbench lo oculta: igual se dibuja (ModelEngine ignora la visibilidad del editor)
        }
        float[] origin = vec(e, "origin", 0);
        float[] rot = vec(e, "rotation", 0);
        Matrix4f m = new Matrix4f().translate(origin[0], origin[1], origin[2]).rotateZYX((float) Math.toRadians(rot[2]),
                (float) Math.toRadians(rot[1]), (float) Math.toRadians(rot[0])).translate(-origin[0], -origin[1], -origin[2]);
        Bone b = this.bones.get(bone);
        if (type.equals("cube")) {
            float inf = e.has("inflate") ? e.get("inflate").getAsFloat() : 0;
            float[] f = vec(e, "from", 0), t = vec(e, "to", 0);
            float x0 = f[0] - inf, y0 = f[1] - inf, z0 = f[2] - inf, x1 = t[0] + inf, y1 = t[1] + inf, z1 = t[2] + inf;
            if (!e.has("faces")) {
                return;
            }
            JsonObject faces = e.getAsJsonObject("faces");
            // esquinas: arriba-izquierda, abajo-izquierda, abajo-derecha, arriba-derecha (como Minecraft)
            String[] names = {"north", "south", "east", "west", "up", "down"};
            float[][][] corners = {
                    {{x1, y1, z0}, {x1, y0, z0}, {x0, y0, z0}, {x0, y1, z0}},
                    {{x0, y1, z1}, {x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}},
                    {{x1, y1, z1}, {x1, y0, z1}, {x1, y0, z0}, {x1, y1, z0}},
                    {{x0, y1, z0}, {x0, y0, z0}, {x0, y0, z1}, {x0, y1, z1}},
                    {{x0, y1, z0}, {x0, y1, z1}, {x1, y1, z1}, {x1, y1, z0}},
                    {{x0, y0, z1}, {x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}},
            };
            for (int fi = 0; fi < 6; fi++) {
                if (!faces.has(names[fi])) {
                    continue;
                }
                JsonObject face = faces.getAsJsonObject(names[fi]);
                int tex = texIndex(face, texByUuid);
                if (tex < 0 || !face.has("uv")) {
                    continue;
                }
                JsonArray uv = face.getAsJsonArray("uv");
                float[] tu = tex < texUv.size() ? texUv.get(tex) : new float[]{16, 16};
                float u0 = uv.get(0).getAsFloat() / tu[0], v0 = uv.get(1).getAsFloat() / tu[1], u1 = uv.get(2).getAsFloat() / tu[0],
                        v1 = uv.get(3).getAsFloat() / tu[1];
                // UV de cada esquina (arriba-izq, abajo-izq, abajo-der, arriba-der), girada si la cara lo pide
                float[][] cuv = {{u0, v0}, {u0, v1}, {u1, v1}, {u1, v0}};
                int r = face.has("rotation") ? (face.get("rotation").getAsInt() / 90) & 3 : 0;
                Quad q = new Quad(tex);
                for (int k = 0; k < 4; k++) {
                    Vector3f p = m.transformPosition(new Vector3f(corners[fi][k][0], corners[fi][k][1], corners[fi][k][2]));
                    q.xyz[k * 3] = p.x;
                    q.xyz[k * 3 + 1] = p.y;
                    q.xyz[k * 3 + 2] = p.z;
                    float[] c = cuv[(k + 4 - r) % 4];
                    q.uv[k * 2] = c[0];
                    q.uv[k * 2 + 1] = c[1];
                }
                normal(q);
                b.quads.add(q);
                this.maxY = Math.max(this.maxY, Math.max(q.xyz[1], Math.max(q.xyz[4], Math.max(q.xyz[7], q.xyz[10]))));
            }
        } else if (type.equals("mesh") && e.has("vertices") && e.has("faces")) {
            JsonObject verts = e.getAsJsonObject("vertices");
            for (Map.Entry<String, JsonElement> fe : e.getAsJsonObject("faces").entrySet()) {
                JsonObject face = fe.getValue().getAsJsonObject();
                int tex = texIndex(face, texByUuid);
                if (tex < 0 || !face.has("vertices")) {
                    continue;
                }
                JsonArray vk = face.getAsJsonArray("vertices");
                if (vk.size() < 3 || vk.size() > 4) {
                    continue;
                }
                float[] tu = tex < texUv.size() ? texUv.get(tex) : new float[]{16, 16};
                JsonObject uvs = face.has("uv") ? face.getAsJsonObject("uv") : new JsonObject();
                Quad q = new Quad(tex);
                // Blockbench guarda las caras de malla en el otro sentido: se recorren al reves
                int n = vk.size();
                for (int k = 0; k < 4; k++) {
                    String key = vk.get(Math.max(0, n - 1 - Math.min(k, n - 1))).getAsString();
                    JsonArray p = verts.getAsJsonArray(key);
                    Vector3f v = m.transformPosition(new Vector3f(p.get(0).getAsFloat() + origin[0], p.get(1).getAsFloat() + origin[1],
                            p.get(2).getAsFloat() + origin[2]));
                    q.xyz[k * 3] = v.x;
                    q.xyz[k * 3 + 1] = v.y;
                    q.xyz[k * 3 + 2] = v.z;
                    JsonArray u = uvs.has(key) ? uvs.getAsJsonArray(key) : null;
                    q.uv[k * 2] = u == null ? 0 : u.get(0).getAsFloat() / tu[0];
                    q.uv[k * 2 + 1] = u == null ? 0 : u.get(1).getAsFloat() / tu[1];
                }
                normal(q);
                b.quads.add(q);
            }
        }
    }

    private static int texIndex(JsonObject face, Map<String, Integer> texByUuid) {
        if (!face.has("texture") || face.get("texture").isJsonNull()) {
            return -1;
        }
        JsonElement t = face.get("texture");
        if (t.isJsonPrimitive() && t.getAsJsonPrimitive().isNumber()) {
            return t.getAsInt();
        }
        if (t.isJsonPrimitive() && t.getAsJsonPrimitive().isBoolean()) {
            return t.getAsBoolean() ? 0 : -1;
        }
        Integer i = texByUuid.get(t.getAsString());
        return i == null ? 0 : i;
    }

    private static void normal(Quad q) {
        float ax = q.xyz[3] - q.xyz[0], ay = q.xyz[4] - q.xyz[1], az = q.xyz[5] - q.xyz[2];
        float bx = q.xyz[6] - q.xyz[0], by = q.xyz[7] - q.xyz[1], bz = q.xyz[8] - q.xyz[2];
        float nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
        float l = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (l < 1e-9f) {
            q.ny = 1;
            return;
        }
        q.nx = nx / l;
        q.ny = ny / l;
        q.nz = nz / l;
    }

    private static float[] vec(JsonObject o, String key, float def) {
        float[] v = {def, def, def};
        if (o.has(key) && o.get(key).isJsonArray()) {
            JsonArray a = o.getAsJsonArray(key);
            for (int i = 0; i < 3 && i < a.size(); i++) {
                v[i] = a.get(i).getAsFloat();
            }
        }
        return v;
    }

    private static float[] point(JsonObject p, float def, List<String> warn) {
        return new float[]{num(p.get("x"), def, warn), num(p.get("y"), def, warn), num(p.get("z"), def, warn)};
    }

    private static float num(JsonElement e, float def, List<String> warn) {
        if (e == null || e.isJsonNull()) {
            return def;
        }
        String s = e.getAsString().trim();
        if (s.isEmpty()) {
            return def;
        }
        try {
            return Float.parseFloat(s);
        } catch (NumberFormatException ex) {
            Float v = Expr.constant(s);
            if (v != null) {
                return v;
            }
            if (warn.size() < 20) {
                warn.add("expresion no soportada: " + s);
            }
            return def;
        }
    }

    // ------------------------------------------------------------------ animacion

    /**
     * Pose de una animacion en un momento (segundos): por hueso, rotacion (grados), posicion (pixeles) y
     * escala, tal como estan en Blockbench. out tiene 9 numeros por hueso.
     */
    public void sample(Anim a, float t, float[] out) {
        for (int b = 0; b < this.bones.size(); b++) {
            int o = b * 9;
            for (int c = 0; c < 3; c++) {
                Key[] ks = a == null ? null : a.keys[b][c];
                float def = c == SCALE ? 1 : 0;
                if (ks == null) {
                    out[o + c * 3] = def;
                    out[o + c * 3 + 1] = def;
                    out[o + c * 3 + 2] = def;
                } else {
                    channel(ks, t, out, o + c * 3);
                }
            }
        }
    }

    private static void channel(Key[] ks, float t, float[] out, int o) {
        if (t <= ks[0].time) {
            System.arraycopy(ks[0].pre, 0, out, o, 3);
            return;
        }
        Key last = ks[ks.length - 1];
        if (t >= last.time) {
            System.arraycopy(last.post, 0, out, o, 3);
            return;
        }
        int i = 0;
        while (i + 1 < ks.length && ks[i + 1].time <= t) {
            i++;
        }
        Key a = ks[i], b = ks[i + 1];
        float span = b.time - a.time;
        float f = span <= 1e-6f ? 1 : (t - a.time) / span;
        if (a.step) {
            System.arraycopy(a.post, 0, out, o, 3);
            return;
        }
        boolean cat = a.catmull || b.catmull;
        float[] p0 = i > 0 ? ks[i - 1].post : a.post, p3 = i + 2 < ks.length ? ks[i + 2].pre : b.pre;
        for (int k = 0; k < 3; k++) {
            float y1 = a.post[k], y2 = b.pre[k];
            if (cat) {
                float y0 = p0[k], y3 = p3[k];
                float f2 = f * f, f3 = f2 * f;
                out[o + k] = 0.5f * (2 * y1 + (-y0 + y2) * f + (2 * y0 - 5 * y1 + 4 * y2 - y3) * f2 + (-y0 + 3 * y1 - 3 * y2 + y3) * f3);
            } else {
                out[o + k] = y1 + (y2 - y1) * f;
            }
        }
    }

    /**
     * Matriz de cada hueso (pixeles del modelo -> pixeles del modelo) para una pose. headYaw y headPitch
     * (radianes) se suman a la cabeza, para que mire a alguien.
     */
    public void matrices(float[] pose, Matrix4f[] out, float headYaw, float headPitch) {
        for (int b = 0; b < this.bones.size(); b++) {
            Bone bone = this.bones.get(b);
            int o = b * 9;
            float rx = (float) Math.toRadians(bone.rotation[0] - pose[o]);
            float ry = (float) Math.toRadians(bone.rotation[1] - pose[o + 1]);
            float rz = (float) Math.toRadians(bone.rotation[2] + pose[o + 2]);
            if (b == this.headBone) {
                rx += headPitch;
                ry += headYaw;
            }
            Matrix4f m = out[b];
            if (bone.parent >= 0) {
                m.set(out[bone.parent]);
            } else {
                m.identity();
            }
            float[] p = bone.origin;
            m.translate(p[0] - pose[o + 3], p[1] + pose[o + 4], p[2] + pose[o + 5]).rotateZYX(rz, ry, rx)
                    .scale(pose[o + 6], pose[o + 7], pose[o + 8]).translate(-p[0], -p[1], -p[2]);
        }
    }

    /** Mezcla dos poses (para pasar de una animacion a otra sin saltos). */
    public static void blend(float[] from, float[] to, float f, float[] out) {
        for (int i = 0; i < out.length; i++) {
            out[i] = from[i] + (to[i] - from[i]) * f;
        }
    }
}
