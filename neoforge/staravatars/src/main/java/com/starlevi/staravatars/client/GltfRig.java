package com.starlevi.staravatars.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Esqueleto R15 de un export glTF de Roblox Studio (sin nada de Minecraft). No arma las mallas: solo
 * donde esta cada articulacion (cadera, rodilla, hombro, codo...) y la caja que ocupa cada parte del
 * cuerpo (de los vertices de sus mallas o de sus jaulas). Un archivo puede tener varios personajes; se elige por nombre.
 */
final class GltfRig {
    static final Set<String> BONES = Set.of("LowerTorso", "UpperTorso", "Head", "LeftUpperArm", "LeftLowerArm", "LeftHand", "RightUpperArm",
            "RightLowerArm", "RightHand", "LeftUpperLeg", "LeftLowerLeg", "LeftFoot", "RightUpperLeg", "RightLowerLeg", "RightFoot");

    final String name;
    /** Posicion de cada hueso (su articulacion con el padre), en studs de Roblox. */
    final Map<String, float[]> joints = new HashMap<>();
    /** Caja (minx, miny, minz, maxx, maxy, maxz) de cada parte, de sus mallas o jaulas. */
    final Map<String, float[]> bounds = new HashMap<>();

    private GltfRig(String name) {
        this.name = name;
    }

    /** @param want nombre del personaje (el del usuario de Roblox); si hay uno solo, se usa ese. */
    static GltfRig read(byte[] data, String want, List<String> log) {
        JsonObject g = JsonParser.parseString(json(data)).getAsJsonObject();
        byte[] bin = glbBin(data);
        Map<Integer, byte[]> bufCache = new HashMap<>();
        JsonArray nodes = g.getAsJsonArray("nodes");
        if (nodes == null) {
            return null;
        }
        int n = nodes.size();
        int[] parent = new int[n];
        java.util.Arrays.fill(parent, -1);
        for (int i = 0; i < n; i++) {
            JsonObject o = nodes.get(i).getAsJsonObject();
            if (o.has("children")) {
                for (JsonElement c : o.getAsJsonArray("children")) {
                    parent[c.getAsInt()] = i;
                }
            }
        }
        double[][] world = new double[n][];
        for (int i = 0; i < n; i++) {
            world(i, nodes, parent, world);
        }
        // personajes: el padre de cada nodo "Root" que tiene huesos adentro
        List<Integer> chars = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if ("Root".equals(name(nodes, i)) && parent[i] >= 0 && !chars.contains(parent[i])) {
                chars.add(parent[i]);
            }
        }
        int pick = -1;
        for (int c : chars) {
            if (want != null && name(nodes, c).equalsIgnoreCase(want)) {
                pick = c;
            }
        }
        if (pick < 0 && chars.size() == 1) {
            pick = chars.get(0);
        }
        if (pick < 0) {
            List<String> names = new ArrayList<>();
            for (int c : chars) {
                names.add(name(nodes, c));
            }
            log.add("glTF: no encontre a " + want + " (trae " + names + ")");
            return null;
        }
        GltfRig rig = new GltfRig(name(nodes, pick));
        JsonArray meshes = g.getAsJsonArray("meshes");
        JsonArray accessors = g.getAsJsonArray("accessors");
        for (int i = 0; i < n; i++) {
            if (!inside(i, pick, parent)) {
                continue;
            }
            JsonObject o = nodes.get(i).getAsJsonObject();
            String nm = name(nodes, i);
            if (!o.has("mesh") && BONES.contains(nm)) {
                double[] w = world[i];
                rig.joints.put(nm, new float[]{(float) w[12], (float) w[13], (float) w[14]});
            }
            if (o.has("mesh") && meshes != null && accessors != null) {
                int us = nm.indexOf('_');
                String part = us < 0 ? nm : nm.substring(0, us);
                if (!BONES.contains(part) || !(nm.endsWith("_Geo") || nm.endsWith("_OuterCage"))) {
                    continue;
                }
                JsonObject mesh = meshes.get(o.get("mesh").getAsInt()).getAsJsonObject();
                for (JsonElement pe : mesh.getAsJsonArray("primitives")) {
                    JsonObject attr = pe.getAsJsonObject().getAsJsonObject("attributes");
                    if (attr == null || !attr.has("POSITION")) {
                        continue;
                    }
                    float[] pts = positions(g, attr.get("POSITION").getAsInt(), bin, bufCache);
                    for (int c = 0; c + 2 < pts.length; c += 3) {
                        double x = pts[c], y = pts[c + 1], z = pts[c + 2];
                        double[] w = world[i];
                        float px = (float) (w[0] * x + w[4] * y + w[8] * z + w[12]);
                        float py = (float) (w[1] * x + w[5] * y + w[9] * z + w[13]);
                        float pz = (float) (w[2] * x + w[6] * y + w[10] * z + w[14]);
                        float[] b = rig.bounds.computeIfAbsent(part, k -> new float[]{Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE,
                                -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE});
                        b[0] = Math.min(b[0], px);
                        b[1] = Math.min(b[1], py);
                        b[2] = Math.min(b[2], pz);
                        b[3] = Math.max(b[3], px);
                        b[4] = Math.max(b[4], py);
                        b[5] = Math.max(b[5], pz);
                    }
                }
            }
        }
        log.add("glTF: " + rig.name + ", " + rig.joints.size() + " huesos, " + rig.bounds.size() + " partes medidas");
        return rig.joints.isEmpty() ? null : rig;
    }

    private static String json(byte[] data) {
        if (data.length >= 20 && data[0] == 'g' && data[1] == 'l' && data[2] == 'T' && data[3] == 'F') {
            ByteBuffer b = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
            int len = b.getInt(12);
            return new String(data, 20, len, StandardCharsets.UTF_8);
        }
        return new String(data, StandardCharsets.UTF_8);
    }

    /** El bloque binario de un .glb (o null). */
    private static byte[] glbBin(byte[] data) {
        if (data.length < 20 || data[0] != 'g' || data[1] != 'l' || data[2] != 'T' || data[3] != 'F') {
            return null;
        }
        ByteBuffer b = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        int off = 20 + b.getInt(12);
        if (off + 8 > data.length) {
            return null;
        }
        int len = b.getInt(off);
        return java.util.Arrays.copyOfRange(data, off + 8, Math.min(data.length, off + 8 + len));
    }

    /** Las posiciones (x, y, z...) de un accessor de floats. */
    private static float[] positions(JsonObject g, int ai, byte[] bin, Map<Integer, byte[]> cache) {
        JsonObject a = g.getAsJsonArray("accessors").get(ai).getAsJsonObject();
        if (!a.has("bufferView") || a.get("componentType").getAsInt() != 5126) {
            return new float[0];
        }
        JsonObject bv = g.getAsJsonArray("bufferViews").get(a.get("bufferView").getAsInt()).getAsJsonObject();
        int bi = bv.get("buffer").getAsInt();
        byte[] buf = cache.computeIfAbsent(bi, k -> {
            JsonObject b = g.getAsJsonArray("buffers").get(k).getAsJsonObject();
            if (!b.has("uri")) {
                return bin == null ? new byte[0] : bin;
            }
            String uri = b.get("uri").getAsString();
            int comma = uri.indexOf(',');
            return uri.startsWith("data:") && comma > 0 ? java.util.Base64.getDecoder().decode(uri.substring(comma + 1)) : new byte[0];
        });
        int count = a.get("count").getAsInt();
        int off = (bv.has("byteOffset") ? bv.get("byteOffset").getAsInt() : 0) + (a.has("byteOffset") ? a.get("byteOffset").getAsInt() : 0);
        int stride = bv.has("byteStride") ? bv.get("byteStride").getAsInt() : 12;
        if (off + (long) (count - 1) * stride + 12 > buf.length) {
            return new float[0];
        }
        ByteBuffer b = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN);
        float[] out = new float[count * 3];
        for (int i = 0; i < count; i++) {
            for (int c = 0; c < 3; c++) {
                out[i * 3 + c] = b.getFloat(off + i * stride + c * 4);
            }
        }
        return out;
    }

    private static String name(JsonArray nodes, int i) {
        JsonObject o = nodes.get(i).getAsJsonObject();
        return o.has("name") ? o.get("name").getAsString() : "";
    }

    private static boolean inside(int i, int root, int[] parent) {
        while (i >= 0) {
            if (i == root) {
                return true;
            }
            i = parent[i];
        }
        return false;
    }

    /** Matriz del mundo (columna mayor, como glTF). */
    private static double[] world(int i, JsonArray nodes, int[] parent, double[][] cache) {
        if (cache[i] != null) {
            return cache[i];
        }
        double[] local = local(nodes.get(i).getAsJsonObject());
        double[] w = parent[i] < 0 ? local : mul(world(parent[i], nodes, parent, cache), local);
        cache[i] = w;
        return w;
    }

    private static double[] local(JsonObject o) {
        if (o.has("matrix")) {
            double[] m = new double[16];
            JsonArray a = o.getAsJsonArray("matrix");
            for (int k = 0; k < 16; k++) {
                m[k] = a.get(k).getAsDouble();
            }
            return m;
        }
        double[] t = vec(o, "translation", new double[]{0, 0, 0});
        double[] r = vec(o, "rotation", new double[]{0, 0, 0, 1});
        double[] s = vec(o, "scale", new double[]{1, 1, 1});
        double x = r[0], y = r[1], z = r[2], w = r[3];
        double[] m = new double[16];
        m[0] = (1 - 2 * (y * y + z * z)) * s[0];
        m[1] = (2 * (x * y + z * w)) * s[0];
        m[2] = (2 * (x * z - y * w)) * s[0];
        m[4] = (2 * (x * y - z * w)) * s[1];
        m[5] = (1 - 2 * (x * x + z * z)) * s[1];
        m[6] = (2 * (y * z + x * w)) * s[1];
        m[8] = (2 * (x * z + y * w)) * s[2];
        m[9] = (2 * (y * z - x * w)) * s[2];
        m[10] = (1 - 2 * (x * x + y * y)) * s[2];
        m[12] = t[0];
        m[13] = t[1];
        m[14] = t[2];
        m[15] = 1;
        return m;
    }

    private static double[] vec(JsonObject o, String k, double[] def) {
        if (!o.has(k)) {
            return def;
        }
        JsonArray a = o.getAsJsonArray(k);
        double[] v = new double[def.length];
        for (int i = 0; i < v.length; i++) {
            v[i] = a.get(i).getAsDouble();
        }
        return v;
    }

    private static double[] mul(double[] a, double[] b) {
        double[] r = new double[16];
        for (int c = 0; c < 4; c++) {
            for (int row = 0; row < 4; row++) {
                double s = 0;
                for (int k = 0; k < 4; k++) {
                    s += a[k * 4 + row] * b[c * 4 + k];
                }
                r[c * 4 + row] = s;
            }
        }
        return r;
    }
}
