package com.annis.npc.client.importer;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lector de movimientos MMD (.vmd). Usa un esqueleto de reposo estandar (proporciones tipo Miku)
 * porque el .vmd no trae el modelo; las piernas con IK se resuelven apuntando al objetivo del pie.
 */
final class VmdReader {
    private record Key(int frame, double[] t, double[] q) {
    }

    private record Bone(String parent, double[] rest) {
    }

    private static final Map<String, Bone> REST = new LinkedHashMap<>();
    private static final Map<String, String> ALIASES = Map.of(
            "左肘", "左ひじ", "右肘", "右ひじ", "左足IK", "左足ＩＫ", "右足IK", "右足ＩＫ", "上半身２", "上半身2");

    private static void bone(String name, String parent, double x, double y, double z) {
        REST.put(name, new Bone(parent, V.v(x, y, z)));
    }

    static {
        bone("全ての親", null, 0, 0, 0);
        bone("センター", "全ての親", 0, 8.0, 0);
        bone("グルーブ", "センター", 0, 8.2, 0);
        bone("腰", "グルーブ", 0, 11.0, 0.4);
        bone("上半身", "腰", 0, 11.6, 0.1);
        bone("上半身2", "上半身", 0, 12.6, 0.2);
        bone("首", "上半身2", 0, 15.3, 0.4);
        bone("頭", "首", 0, 16.0, 0.2);
        bone("下半身", "腰", 0, 11.6, 0.1);
        for (int s = 0; s < 2; s++) {
            String side = s == 0 ? "左" : "右";
            double sx = s == 0 ? 1 : -1;
            bone(side + "肩", "上半身2", 0.35 * sx, 14.7, 0.6);
            bone(side + "腕", side + "肩", 1.35 * sx, 14.6, 0.6);
            bone(side + "腕捩", side + "腕", 2.4 * sx, 13.7, 0.62);
            bone(side + "ひじ", side + "腕捩", 3.4 * sx, 12.8, 0.65);
            bone(side + "手捩", side + "ひじ", 4.3 * sx, 12.0, 0.6);
            bone(side + "手首", side + "手捩", 5.3 * sx, 11.1, 0.5);
            bone(side + "足", "下半身", 0.95 * sx, 10.6, 0.25);
            bone(side + "ひざ", side + "足", 1.05 * sx, 6.0, -0.1);
            bone(side + "足首", side + "ひざ", 1.15 * sx, 1.3, 0.55);
            bone(side + "足IK親", "全ての親", 1.15 * sx, 0, 0.55);
            bone(side + "足ＩＫ", side + "足IK親", 1.15 * sx, 1.3, 0.55);
        }
    }

    private final Map<String, List<Key>> keys = new HashMap<>();
    int lastFrame;

    static VmdReader read(Path file) throws Exception {
        VmdReader r = new VmdReader();
        r.parse(Files.readAllBytes(file));
        return r;
    }

    private void parse(byte[] bytes) {
        ByteBuffer b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        String header = new String(bytes, 0, Math.min(30, bytes.length), Charset.forName("windows-31j"));
        if (!header.startsWith("Vocaloid Motion Data")) {
            throw new IllegalArgumentException("No es un archivo .vmd de MMD");
        }
        int nameLen = header.startsWith("Vocaloid Motion Data 0002") ? 20 : 10;
        b.position(30 + nameLen);
        int n = b.getInt();
        Charset sjis = Charset.forName("windows-31j");
        byte[] nb = new byte[15];
        for (int i = 0; i < n; i++) {
            b.get(nb);
            int len = 0;
            while (len < 15 && nb[len] != 0) {
                len++;
            }
            String name = new String(nb, 0, len, sjis);
            name = ALIASES.getOrDefault(name, name);
            int frame = b.getInt();
            double[] t = V.v(b.getFloat(), b.getFloat(), b.getFloat());
            double[] q = {b.getFloat(), b.getFloat(), b.getFloat(), b.getFloat()};
            b.position(b.position() + 64);
            if (REST.containsKey(name)) {
                keys.computeIfAbsent(name, k -> new ArrayList<>()).add(new Key(frame, t, q));
                lastFrame = Math.max(lastFrame, frame);
            }
        }
        for (List<Key> list : keys.values()) {
            list.sort(Comparator.comparingInt(Key::frame));
        }
        if (!keys.containsKey("左腕") && !keys.containsKey("右腕") && !keys.containsKey("センター")) {
            throw new IllegalArgumentException("El .vmd no tiene huesos de cuerpo (¿es una camara o expresiones?)");
        }
    }

    private Key sample(String name, double f) {
        List<Key> ks = keys.get(name);
        if (ks == null || ks.isEmpty()) {
            return null;
        }
        if (f <= ks.get(0).frame) {
            return ks.get(0);
        }
        if (f >= ks.get(ks.size() - 1).frame) {
            return ks.get(ks.size() - 1);
        }
        int lo = 0;
        int hi = ks.size() - 1;
        while (hi - lo > 1) {
            int m = (lo + hi) >>> 1;
            if (ks.get(m).frame <= f) {
                lo = m;
            } else {
                hi = m;
            }
        }
        Key a = ks.get(lo);
        Key c = ks.get(hi);
        double t = (f - a.frame) / Math.max(1.0, c.frame - a.frame);
        return new Key((int) f, V.add(a.t, V.mul(V.sub(c.t, a.t), t)), V.slerp(a.q, c.q, t));
    }

    Pose pose(double f) {
        Map<String, double[]> gp = new HashMap<>();
        Map<String, double[]> gr = new HashMap<>();
        for (Map.Entry<String, Bone> e : REST.entrySet()) {
            String name = e.getKey();
            Bone bn = e.getValue();
            Key k = sample(name, f);
            double[] tr = k == null ? V.v(0, 0, 0) : k.t;
            double[] r = k == null ? V.identity() : V.quat(k.q[0], k.q[1], k.q[2], k.q[3]);
            if (bn.parent == null) {
                gp.put(name, V.add(bn.rest, tr));
                gr.put(name, r);
            } else {
                double[] prest = REST.get(bn.parent).rest;
                gp.put(name, V.add(gp.get(bn.parent), V.mv(gr.get(bn.parent), V.add(V.sub(bn.rest, prest), tr))));
                gr.put(name, V.mm(gr.get(bn.parent), r));
            }
        }
        Pose p = new Pose();
        p.pos.put("legL", gp.get("左足"));
        p.pos.put("legR", gp.get("右足"));
        p.pos.put("armL", gp.get("左腕"));
        p.pos.put("armR", gp.get("右腕"));
        p.pos.put("handL", gp.get("左手首"));
        p.pos.put("handR", gp.get("右手首"));
        p.pos.put("neck", gp.get("首"));
        p.pos.put("head", gp.get("頭"));
        p.pos.put("spine", gp.get("上半身")); // la espalda de MMD se dobla en "上半身", justo encima de la cadera
        p.pos.put("elbowL", gp.get("左ひじ"));
        p.pos.put("elbowR", gp.get("右ひじ"));
        double[] lowerFwd = V.mv(gr.get("下半身"), V.v(0, 0, -1));
        for (String side : new String[]{"左", "右"}) {
            String key = side.equals("左") ? "L" : "R";
            if (keys.containsKey(side + "足ＩＫ")) {
                // pierna con IK: tobillo en el objetivo y rodilla resuelta (2 huesos) hacia delante
                double[] hip = gp.get(side + "足");
                double[] target = gp.get(side + "足ＩＫ");
                double l1 = V.len(V.sub(REST.get(side + "ひざ").rest, REST.get(side + "足").rest));
                double l2 = V.len(V.sub(REST.get(side + "足首").rest, REST.get(side + "ひざ").rest));
                double[] toT = V.sub(target, hip);
                double d = Math.min(V.len(toT), (l1 + l2) * 0.9999);
                double[] u = V.norm(toT);
                double a = (l1 * l1 - l2 * l2 + d * d) / (2 * d);
                double h = Math.sqrt(Math.max(0, l1 * l1 - a * a));
                double[] pole = V.sub(lowerFwd, V.mul(u, V.dot(lowerFwd, u)));
                pole = V.len(pole) < 1e-6 ? V.v(0, 0, -1) : V.norm(pole);
                double[] knee = V.add(hip, V.add(V.mul(u, a), V.mul(pole, h)));
                p.pos.put("knee" + key, knee);
                p.pos.put("foot" + key, V.add(hip, V.mul(u, d)));
            } else {
                p.pos.put("knee" + key, gp.get(side + "ひざ"));
                p.pos.put("foot" + key, gp.get(side + "足首"));
            }
        }
        p.headRot = gr.get("頭");
        return p;
    }
}
