package com.starlevi.starscenes.emf;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Un modelo de EMF / OptiFine CEM (.jem, con sus .jpm): piezas con cajas, que se enganchan a las
 * partes del modelo de Minecraft (head, body, right_arm...), y las formulas que lo animan.
 * Lector propio de StarScenes; las reglas de coordenadas son las mismas que usa EMF.
 */
public final class JemModel {
    /** Una cara: 4 esquinas (x,y,z en pixeles) con su UV (0..1), en el orden de Minecraft. */
    public static final class Face {
        public final float[] xyz = new float[12], uv = new float[8];
        public float nx, ny, nz;
    }

    public static final class Part {
        public String id = "", vanilla;
        public boolean attach;
        public float x, y, z, rx, ry, rz, scale = 1;
        public final List<Face> faces = new ArrayList<>();
        public final List<Part> children = new ArrayList<>();
    }

    /** Una linea de animacion: "head.rx" = formula. */
    public record Line(String target, String channel, Expr expr, String text) {
    }

    public final List<Part> parts = new ArrayList<>();
    public final List<Line> lines = new ArrayList<>();
    public final List<String> warnings = new ArrayList<>();
    public String texture;
    public int texW = 64, texH = 64;

    private JemModel() {
    }

    /** Lee un .jem; jpm devuelve el contenido de un .jpm referido por nombre (o null). */
    public static JemModel parse(JsonObject root, Function<String, JsonObject> jpm) {
        JemModel m = new JemModel();
        if (root.has("texture")) {
            m.texture = root.get("texture").getAsString();
        }
        int[] ts = size(root, new int[]{64, 64});
        m.texW = ts[0];
        m.texH = ts[1];
        if (root.has("models")) {
            for (JsonElement e : root.getAsJsonArray("models")) {
                JsonObject o = e.getAsJsonObject();
                if (o.has("model") && jpm != null) {
                    JsonObject ext = jpm.apply(o.get("model").getAsString());
                    if (ext != null) {
                        // lo del .jem pisa lo del .jpm
                        JsonObject merged = ext.deepCopy();
                        for (Map.Entry<String, JsonElement> k : o.entrySet()) {
                            merged.add(k.getKey(), k.getValue());
                        }
                        o = merged;
                    } else {
                        m.warnings.add("falta " + o.get("model").getAsString());
                    }
                }
                if (!o.has("part")) {
                    continue;
                }
                Part p = m.part(o, ts, true);
                p.vanilla = o.get("part").getAsString();
                if (p.id.isEmpty()) {
                    p.id = p.vanilla;
                }
                m.parts.add(p);
                m.animations(o);
            }
        }
        return m;
    }

    private static int[] size(JsonObject o, int[] def) {
        if (o.has("textureSize")) {
            JsonArray a = o.getAsJsonArray("textureSize");
            return new int[]{a.get(0).getAsInt(), a.get(1).getAsInt()};
        }
        return def;
    }

    private static float[] floats(JsonObject o, String key, int n) {
        float[] f = new float[n];
        if (o.has(key) && o.get(key).isJsonArray()) {
            JsonArray a = o.getAsJsonArray(key);
            for (int i = 0; i < Math.min(n, a.size()); i++) {
                f[i] = a.get(i).getAsFloat();
            }
        }
        return f;
    }

    private void animations(JsonObject o) {
        if (!o.has("animations")) {
            return;
        }
        for (JsonElement ae : o.getAsJsonArray("animations")) {
            for (Map.Entry<String, JsonElement> en : ae.getAsJsonObject().entrySet()) {
                String key = en.getKey();
                int dot = key.lastIndexOf('.');
                if (dot <= 0) {
                    continue;
                }
                try {
                    String text = en.getValue().isJsonPrimitive() ? en.getValue().getAsString() : en.getValue().toString();
                    this.lines.add(new Line(key.substring(0, dot), key.substring(dot + 1), Expr.parse(text), text));
                } catch (Exception ex) {
                    this.warnings.add(key + ": " + ex.getMessage());
                }
            }
        }
    }

    private Part part(JsonObject o, int[] parentSize, boolean top) {
        Part p = new Part();
        p.id = o.has("id") ? o.get("id").getAsString() : "";
        p.attach = o.has("attach") && o.get("attach").getAsBoolean();
        String inv = o.has("invertAxis") ? o.get("invertAxis").getAsString() : "";
        boolean ix = inv.contains("x"), iy = inv.contains("y"), iz = inv.contains("z");
        float[] t = floats(o, "translate", 3), r = floats(o, "rotate", 3);
        p.x = ix ? -t[0] : t[0];
        p.y = iy ? -t[1] : t[1];
        p.z = iz ? -t[2] : t[2];
        p.rx = (float) Math.toRadians(ix ? -r[0] : r[0]);
        p.ry = (float) Math.toRadians(iy ? -r[1] : r[1]);
        p.rz = (float) Math.toRadians(iz ? -r[2] : r[2]);
        p.scale = o.has("scale") ? o.get("scale").getAsFloat() : 1;
        int[] ts = size(o, parentSize);
        String mirror = o.has("mirrorTexture") ? o.get("mirrorTexture").getAsString() : "";
        boolean mu = mirror.contains("u"), mv = mirror.contains("v");
        if (o.has("boxes")) {
            for (JsonElement be : o.getAsJsonArray("boxes")) {
                box(p, be.getAsJsonObject(), ix, iy, iz, ts, mu, mv);
            }
        }
        for (String key : new String[]{"submodels", "submodel"}) {
            if (!o.has(key)) {
                continue;
            }
            JsonElement s = o.get(key);
            if (s.isJsonArray()) {
                for (JsonElement se : s.getAsJsonArray()) {
                    p.children.add(part(se.getAsJsonObject(), ts, false));
                    animations(se.getAsJsonObject());
                }
            } else if (s.isJsonObject()) {
                p.children.add(part(s.getAsJsonObject(), ts, false));
                animations(s.getAsJsonObject());
            }
        }
        return p;
    }

    /** Un cubo de caja (sin ejes invertidos, textura de 64x64). */
    static void boxInto(Part p, JsonObject b) {
        new JemModel().box(p, b, false, false, false, new int[]{64, 64}, false, false);
    }

    private void box(Part p, JsonObject b, boolean ix, boolean iy, boolean iz, int[] ts, boolean mu, boolean mv) {
        float[] c = floats(b, "coordinates", 6);
        float add = b.has("sizeAdd") ? b.get("sizeAdd").getAsFloat() : 0;
        float ax = add, ay = add, az = add;
        if (b.has("sizesAdd")) {
            float[] s = floats(b, "sizesAdd", 3);
            ax = s[0];
            ay = s[1];
            az = s[2];
        }
        if (ix) {
            c[0] = -c[0] - c[3];
        }
        if (iy) {
            c[1] = -c[1] - c[4];
        }
        if (iz) {
            c[2] = -c[2] - c[5];
        }
        float x0 = c[0] - ax, y0 = c[1] - ay, z0 = c[2] - az, x1 = c[0] + c[3] + ax, y1 = c[1] + c[4] + ay, z1 = c[2] + c[5] + az;
        float[][] v = {null, {x0, y0, z0}, {x1, y0, z0}, {x1, y1, z0}, {x0, y1, z0}, {x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}, {x0, y1, z1}};
        float tw = ts[0], th = ts[1];
        if (b.has("textureOffset")) {
            float[] to = floats(b, "textureOffset", 2);
            float sx = c[3], sy = c[4], sz = c[5];
            float j = to[0], k = j + sz, l = k + sx, m = l + sx, n = l + sz, o = n + sx;
            float pp = to[1], q = pp + sz, r = q + sy;
            // como los cubos de Minecraft (con espejo opcional), igual que EMF
            face(p, mv ? new float[][]{v[3], v[4], v[8], v[7]} : new float[][]{v[6], v[5], v[1], v[2]}, mu ? l : k, mv ? q : pp, mu ? k : l, mv ? pp : q, tw, th);
            face(p, mv ? new float[][]{v[6], v[5], v[1], v[2]} : new float[][]{v[3], v[4], v[8], v[7]}, mu ? m : l, mv ? pp : q, mu ? l : m, mv ? q : pp, tw, th);
            face(p, mu ? new float[][]{v[6], v[2], v[3], v[7]} : new float[][]{v[1], v[5], v[8], v[4]}, mu ? k : j, mv ? r : q, mu ? j : k, mv ? q : r, tw, th);
            face(p, new float[][]{v[2], v[1], v[4], v[3]}, mu ? l : k, mv ? r : q, mu ? k : l, mv ? q : r, tw, th);
            face(p, mu ? new float[][]{v[1], v[5], v[8], v[4]} : new float[][]{v[6], v[2], v[3], v[7]}, mu ? n : l, mv ? r : q, mu ? l : n, mv ? q : r, tw, th);
            face(p, new float[][]{v[5], v[6], v[7], v[8]}, mu ? o : n, mv ? r : q, mu ? n : o, mv ? q : r, tw, th);
        } else {
            // UV por cara (uvNorth, uvSouth...), igual que EMF (sin espejo)
            float[] up = floats(b, "uvUp", 4), down = floats(b, "uvDown", 4), west = floats(b, "uvWest", 4), north = floats(b, "uvNorth", 4),
                    east = floats(b, "uvEast", 4), south = floats(b, "uvSouth", 4);
            if (b.has("uvUp")) {
                face(p, new float[][]{v[1], v[2], v[6], v[5]}, up[0], up[1], up[2], up[3], tw, th);
            }
            if (b.has("uvDown")) {
                face(p, new float[][]{v[8], v[7], v[3], v[4]}, down[0], down[1], down[2], down[3], tw, th);
            }
            if (b.has("uvWest")) {
                face(p, new float[][]{v[6], v[2], v[3], v[7]}, west[0], west[1], west[2], west[3], tw, th);
            }
            if (b.has("uvNorth")) {
                face(p, new float[][]{v[2], v[1], v[4], v[3]}, north[0], north[1], north[2], north[3], tw, th);
            }
            if (b.has("uvEast")) {
                face(p, new float[][]{v[1], v[5], v[8], v[4]}, east[0], east[1], east[2], east[3], tw, th);
            }
            if (b.has("uvSouth")) {
                face(p, new float[][]{v[5], v[6], v[7], v[8]}, south[0], south[1], south[2], south[3], tw, th);
            }
        }
    }

    /** Una cara como el Polygon de Minecraft: esquina 0 = (u2,v1), 1 = (u1,v1), 2 = (u1,v2), 3 = (u2,v2). */
    static void face(Part p, float[][] vs, float u1, float v1, float u2, float v2, float tw, float th) {
        if (u1 == u2 && v1 == v2) {
            return;
        }
        Face f = new Face();
        float[][] uv = {{u2, v1}, {u1, v1}, {u1, v2}, {u2, v2}};
        for (int i = 0; i < 4; i++) {
            f.xyz[i * 3] = vs[i][0];
            f.xyz[i * 3 + 1] = vs[i][1];
            f.xyz[i * 3 + 2] = vs[i][2];
            f.uv[i * 2] = uv[i][0] / tw;
            f.uv[i * 2 + 1] = uv[i][1] / th;
        }
        // normal (de las esquinas)
        float ax = f.xyz[3] - f.xyz[0], ay = f.xyz[4] - f.xyz[1], az = f.xyz[5] - f.xyz[2];
        float bx = f.xyz[6] - f.xyz[0], by = f.xyz[7] - f.xyz[1], bz = f.xyz[8] - f.xyz[2];
        float nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len > 0) {
            f.nx = nx / len;
            f.ny = ny / len;
            f.nz = nz / len;
        }
        p.faces.add(f);
    }

    /** Todas las piezas propias por id (para las formulas). */
    public Map<String, Part> byId() {
        Map<String, Part> out = new LinkedHashMap<>();
        for (Part p : this.parts) {
            collect(p, out);
        }
        return out;
    }

    private static void collect(Part p, Map<String, Part> out) {
        if (!p.id.isEmpty()) {
            out.putIfAbsent(p.id, p);
        }
        for (Part c : p.children) {
            collect(c, out);
        }
    }
}
