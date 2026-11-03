package com.starlevi.starspirits.client;

import net.minecraft.client.model.geom.PartPose;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Modelos .obj subidos al servidor (por ejemplo, exportados de Roblox Studio o de Blender). Cada
 * grupo ("g") pasa a ser una pieza que se anima segun su nombre: head/cabeza, body/torso/cuerpo,
 * arm l / left arm (brazo izquierdo), arm r, leg, ear/oreja, hair/pelo, tail/cola. Se centra, se
 * apoya en el piso y se escala a la altura de un espiritu.
 */
public final class ObjModel {
    /** Altura final del modelo, en pixeles del juego (antes de la "escala" del espiritu). */
    private static final float HEIGHT = 16f;

    private ObjModel() {
    }

    private record Group(String name, List<float[][]> faces) {
    }

    public static SpiritRig parse(byte[] data) {
        List<float[]> v = new ArrayList<>(), vt = new ArrayList<>(), vn = new ArrayList<>();
        Map<String, Group> groups = new LinkedHashMap<>();
        Group cur = null;
        for (String raw : new String(data, StandardCharsets.UTF_8).split("\n")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] t = line.split("\\s+");
            switch (t[0]) {
                case "v" -> v.add(new float[]{f(t[1]), f(t[2]), f(t[3])});
                case "vt" -> vt.add(new float[]{f(t[1]), t.length > 2 ? f(t[2]) : 0});
                case "vn" -> vn.add(new float[]{f(t[1]), f(t[2]), f(t[3])});
                case "g", "o" -> {
                    String name = t.length > 1 ? t[1] : "cuerpo";
                    cur = groups.computeIfAbsent(name, n -> new Group(n, new ArrayList<>()));
                }
                case "f" -> {
                    if (cur == null) {
                        cur = groups.computeIfAbsent("cuerpo", n -> new Group(n, new ArrayList<>()));
                    }
                    int n = t.length - 1;
                    float[][] corners = new float[n][];
                    for (int i = 0; i < n; i++) {
                        String[] idx = t[i + 1].split("/");
                        float[] p = v.get(index(idx[0], v.size()));
                        float[] uv = idx.length > 1 && !idx[1].isEmpty() ? vt.get(index(idx[1], vt.size())) : new float[]{0, 0};
                        float[] nr = idx.length > 2 && !idx[2].isEmpty() ? vn.get(index(idx[2], vn.size())) : new float[]{0, 1, 0};
                        corners[i] = new float[]{p[0], p[1], p[2], uv[0], uv[1], nr[0], nr[1], nr[2]};
                    }
                    // en abanico: triangulos (los cuadros de mas lados se parten)
                    for (int i = 1; i + 1 < n; i++) {
                        cur.faces.add(new float[][]{corners[0], corners[i], corners[i + 1]});
                    }
                }
                default -> {
                }
            }
        }
        // caja del modelo entero: se apoya en el piso, centrado y a la altura de un espiritu
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        for (float[] p : v) {
            minX = Math.min(minX, p[0]);
            minY = Math.min(minY, p[1]);
            minZ = Math.min(minZ, p[2]);
            maxX = Math.max(maxX, p[0]);
            maxY = Math.max(maxY, p[1]);
            maxZ = Math.max(maxZ, p[2]);
        }
        float k = HEIGHT / Math.max(1e-4f, maxY - minY);
        float cx = (minX + maxX) / 2, cz = (minZ + maxZ) / 2, fy = minY;
        // (archivo: y arriba, frente hacia -z) -> modelo del juego (y abajo, pies en 24, sin espejar)
        Map<String, float[]> box = new HashMap<>();
        Map<String, List<float[][]>> conv = new LinkedHashMap<>();
        for (Group g : groups.values()) {
            float[] b = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
            List<float[][]> faces = new ArrayList<>();
            for (float[][] tri : g.faces) {
                float[][] o = new float[3][];
                for (int i = 0; i < 3; i++) {
                    float[] c = tri[i];
                    float x = -(c[0] - cx) * k, y = 24 - (c[1] - fy) * k, z = (c[2] - cz) * k;
                    o[i] = new float[]{x, y, z, c[3], 1 - c[4], -c[5], -c[6], c[7]};
                    b[0] = Math.min(b[0], x);
                    b[1] = Math.min(b[1], y);
                    b[2] = Math.min(b[2], z);
                    b[3] = Math.max(b[3], x);
                    b[4] = Math.max(b[4], y);
                    b[5] = Math.max(b[5], z);
                }
                faces.add(o);
            }
            if (!faces.isEmpty()) {
                box.put(g.name, b);
                conv.put(g.name, faces);
            }
        }
        // a que pieza va cada grupo, y su punto de giro
        Map<String, String> role = new LinkedHashMap<>();
        for (String n : conv.keySet()) {
            role.put(n, roleOf(n));
        }
        String bodyGroup = null, headGroup = null;
        for (Map.Entry<String, String> e : role.entrySet()) {
            String low = e.getKey().toLowerCase(Locale.ROOT);
            if (e.getValue().equals("cuerpo") && (bodyGroup == null || low.contains("body"))) {
                bodyGroup = e.getKey();
            }
            if (e.getValue().equals("cabeza") && (headGroup == null || (low.contains("head") && !headGroup.toLowerCase(Locale.ROOT).contains("head")))) {
                headGroup = e.getKey();
            }
        }
        // accesorios: van pegados a la pieza que los contiene (cabeza, una mano) o al cuerpo
        for (String n : new ArrayList<>(role.keySet())) {
            if (!role.get(n).equals("extra")) {
                continue;
            }
            float[] b = box.get(n);
            float x = (b[0] + b[3]) / 2, y = (b[1] + b[4]) / 2, z = (b[2] + b[5]) / 2;
            String host = null;
            float best = Float.MAX_VALUE;
            for (Map.Entry<String, String> e : role.entrySet()) {
                String r = e.getValue();
                if (!(e.getKey().equals(headGroup) || r.equals("brazo_izq") || r.equals("brazo_der"))) {
                    continue;
                }
                float[] h = box.get(e.getKey());
                float mx = (h[3] - h[0]) * 0.25f + 1, my = (h[4] - h[1]) * 0.25f + 1, mz = (h[5] - h[2]) * 0.25f + 1;
                if (x >= h[0] - mx && x <= h[3] + mx && y >= h[1] - my && y <= h[4] + my && z >= h[2] - mz && z <= h[5] + mz) {
                    float vol = (h[3] - h[0]) * (h[4] - h[1]) * (h[5] - h[2]);
                    if (vol < best) {
                        best = vol;
                        host = e.getKey();
                    }
                }
            }
            if (host == null) {
                role.put(n, "cuerpo");
            } else if (host.equals(headGroup)) {
                role.put(n, "cabeza");
            } else {
                conv.get(host).addAll(conv.remove(n));
                role.remove(n);
            }
        }
        float[] bb = bodyGroup != null ? box.get(bodyGroup) : new float[]{-4, 8, -4, 4, 24, 4};
        float[] bodyPivot = {(bb[0] + bb[3]) / 2, bb[4], (bb[2] + bb[5]) / 2};
        float[] hb = headGroup != null ? box.get(headGroup) : null;
        float[] headPivot = hb == null ? null : new float[]{(hb[0] + hb[3]) / 2, hb[4], (hb[2] + hb[5]) / 2};
        Mesh bodyMesh = new Mesh(), headMesh = new Mesh();
        SoftForms.Node root = new SoftForms.Node("raiz", null, PartPose.ZERO);
        SoftForms.Node body = root.add(new SoftForms.Node("cuerpo", bodyMesh, PartPose.offset(bodyPivot[0], bodyPivot[1], bodyPivot[2])));
        SoftForms.Node head = hb == null ? null : body.add(new SoftForms.Node("cabeza", headMesh,
                PartPose.offset(headPivot[0] - bodyPivot[0], headPivot[1] - bodyPivot[1], headPivot[2] - bodyPivot[2])));
        Map<String, Integer> used = new HashMap<>();
        for (Map.Entry<String, List<float[][]>> e : conv.entrySet()) {
            String r = role.get(e.getKey());
            float[] b = box.get(e.getKey());
            switch (r) {
                case "cuerpo" -> add(bodyMesh, e.getValue(), bodyPivot);
                case "cabeza" -> add(head != null ? headMesh : bodyMesh, e.getValue(), head != null ? headPivot : bodyPivot);
                default -> {
                    boolean onHead = head != null && (r.startsWith("oreja") || r.startsWith("pelo"));
                    boolean leg = r.startsWith("pierna");
                    // punto de giro: brazos y piernas arriba; orejas abajo; cola del lado del cuerpo
                    float px = (b[0] + b[3]) / 2, py = (b[1] + b[4]) / 2, pz = (b[2] + b[5]) / 2;
                    if (r.startsWith("brazo") || leg) {
                        py = b[1];
                    } else if (r.startsWith("oreja")) {
                        py = b[4];
                    } else if (r.startsWith("cola")) {
                        pz = Math.abs(b[2]) < Math.abs(b[5]) ? b[2] : b[5];
                    }
                    Mesh m = new Mesh();
                    add(m, e.getValue(), new float[]{px, py, pz});
                    int n = used.merge(r, 1, Integer::sum);
                    String name = n == 1 ? r : r + "_" + n;
                    SoftForms.Node parent = leg ? root : onHead ? head : body;
                    float[] ref = leg ? new float[]{0, 0, 0} : onHead ? headPivot : bodyPivot;
                    parent.add(new SoftForms.Node(name, m, PartPose.offset(px - ref[0], py - ref[1], pz - ref[2])));
                }
            }
        }
        Map<String, List<String>> paths = new HashMap<>();
        SpiritRig rig = new SpiritRig(root.bake(new ArrayList<>(), paths), paths);
        rig.topY = HEIGHT;
        rig.chibi = true;
        return rig;
    }

    private static void add(Mesh m, List<float[][]> faces, float[] pivot) {
        for (float[][] tri : faces) {
            float[][] t = new float[3][];
            for (int i = 0; i < 3; i++) {
                float[] c = tri[i].clone();
                c[0] -= pivot[0];
                c[1] -= pivot[1];
                c[2] -= pivot[2];
                t[i] = c;
            }
            m.triangle(t[0], t[1], t[2]);
        }
    }

    private static String roleOf(String group) {
        String n = group.toLowerCase(Locale.ROOT).replaceAll("[0-9_ .-]", "").replaceAll("mtl$", "").replaceAll("material$", "");
        if (n.contains("head") || n.contains("cabeza") || n.contains("face") || n.contains("glass") || n.contains("hat")
                || n.contains("gorro") || n.contains("lente")) {
            return "cabeza";
        }
        if (n.contains("ear") || n.contains("oreja")) {
            return n.endsWith("l") || n.contains("left") || n.contains("izq") ? "oreja_izq" : "oreja_der";
        }
        if (n.contains("hair") || n.contains("pelo")) {
            return "pelo";
        }
        if (n.contains("tail") || n.contains("cola")) {
            return "cola";
        }
        if (n.contains("arm") || n.contains("brazo") || n.contains("hand")) {
            return n.endsWith("l") || n.contains("left") || n.contains("izq") ? "brazo_izq" : "brazo_der";
        }
        if (n.contains("leg") || n.contains("pierna") || n.contains("foot")) {
            return n.endsWith("l") || n.contains("left") || n.contains("izq") ? "pierna_izq" : "pierna_der";
        }
        if (n.contains("body") || n.contains("torso") || n.contains("cuerpo")) {
            return "cuerpo";
        }
        // accesorio (anteojos, microfono, arma, anillo...): va con la pieza que lo tiene puesto
        return "extra";
    }

    private static float f(String s) {
        return Float.parseFloat(s);
    }

    private static int index(String s, int size) {
        int i = Integer.parseInt(s);
        return i < 0 ? size + i : i - 1;
    }
}
