package com.annis.npc.client.punchy;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Una animacion en formato Bedrock/Blockbench (el que exporta Punchy Studio): largo, si se repite,
 * y por hueso canales de giro (grados), posicion (pixeles) y escala, con fotogramas clave
 * lineales, suaves (catmullrom) o de golpe (step), y valores fijos o formulas Molang.
 */
public final class BedrockClip {
    public enum Loop { ONCE, LOOP, HOLD }

    enum Lerp { LINEAR, SMOOTH, STEP }

    static final class Key {
        final float time;
        final Molang.Expr[] pre;
        final Molang.Expr[] post;
        final Lerp lerp;

        Key(float time, Molang.Expr[] pre, Molang.Expr[] post, Lerp lerp) {
            this.time = time;
            this.pre = pre;
            this.post = post;
            this.lerp = lerp;
        }
    }

    /** Un canal: o un valor fijo/formula, o fotogramas clave ordenados por tiempo. */
    static final class Channel {
        Molang.Expr[] value;
        final List<Key> keys = new ArrayList<>();

        boolean sample(float time, float life, float[] out) {
            if (this.value != null) {
                eval(this.value, time, life, out);
                return true;
            }
            if (this.keys.isEmpty()) {
                return false;
            }
            Key first = this.keys.get(0);
            if (time <= first.time) {
                eval(first.pre, time, life, out);
                return true;
            }
            Key last = this.keys.get(this.keys.size() - 1);
            if (time >= last.time) {
                eval(last.post, time, life, out);
                return true;
            }
            int k = 0;
            while (k < this.keys.size() - 1 && this.keys.get(k + 1).time <= time) {
                k++;
            }
            Key a = this.keys.get(k), b = this.keys.get(k + 1);
            float u = (time - a.time) / Math.max(1.0e-5f, b.time - a.time);
            float[] va = new float[3], vb = new float[3];
            eval(a.post, time, life, va);
            eval(b.pre, time, life, vb);
            if (a.lerp == Lerp.STEP) {
                System.arraycopy(va, 0, out, 0, 3);
                return true;
            }
            if (a.lerp == Lerp.SMOOTH || b.lerp == Lerp.SMOOTH) {
                float[] p0 = new float[3], p3 = new float[3];
                if (k > 0) {
                    eval(this.keys.get(k - 1).post, time, life, p0);
                } else {
                    System.arraycopy(va, 0, p0, 0, 3);
                }
                if (k + 2 < this.keys.size()) {
                    eval(this.keys.get(k + 2).pre, time, life, p3);
                } else {
                    System.arraycopy(vb, 0, p3, 0, 3);
                }
                for (int c = 0; c < 3; c++) {
                    out[c] = catmull(p0[c], va[c], vb[c], p3[c], u);
                }
                return true;
            }
            for (int c = 0; c < 3; c++) {
                out[c] = va[c] + (vb[c] - va[c]) * u;
            }
            return true;
        }
    }

    static float catmull(float p0, float p1, float p2, float p3, float t) {
        float t2 = t * t, t3 = t2 * t;
        return 0.5f * ((2 * p1) + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 + (-p0 + 3 * p1 - 3 * p2 + p3) * t3);
    }

    static void eval(Molang.Expr[] v, float time, float life, float[] out) {
        for (int c = 0; c < 3; c++) {
            out[c] = v[c].eval(time, life);
        }
    }

    /** Huesos de un cuerpo de jugador. */
    public static final class Bone {
        public Channel rotation;
        public Channel position;
        public Channel scale;
    }

    public final String name;
    public final float length;
    public final Loop loop;
    public final Map<String, Bone> bones = new HashMap<>();

    private BedrockClip(String name, float length, Loop loop) {
        this.name = name;
        this.length = length;
        this.loop = loop;
    }

    /** Segundos de la animacion para un tiempo transcurrido (repite, se queda en el ultimo o termina). */
    public float clipTime(float seconds) {
        if (this.length <= 0) {
            return seconds;
        }
        if (this.loop == Loop.LOOP) {
            return seconds % this.length;
        }
        return Math.min(seconds, this.length);
    }

    /** Nombre normalizado del hueso: head, body, right_arm, left_arm, right_leg, left_leg, root u otro. */
    static String boneKey(String raw) {
        String k = raw.toLowerCase(Locale.ROOT).replace("_", "").replace(" ", "");
        return switch (k) {
            case "head" -> "head";
            case "body", "torso", "chest" -> "body";
            case "rightarm" -> "right_arm";
            case "leftarm" -> "left_arm";
            case "rightleg" -> "right_leg";
            case "leftleg" -> "left_leg";
            case "root", "waist", "player" -> "root";
            default -> k;
        };
    }

    // ------------------------------------------------------------------ lectura

    private static Molang.Expr scalar(JsonElement e) {
        if (e == null || e.isJsonNull()) {
            return Molang.constant(0);
        }
        if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) {
            return Molang.constant(e.getAsFloat());
        }
        if (e.isJsonPrimitive()) {
            String t = e.getAsString().trim();
            try {
                return Molang.constant(Float.parseFloat(t));
            } catch (NumberFormatException ex) {
                return Molang.parse(t);
            }
        }
        return Molang.constant(0);
    }

    private static Molang.Expr[] vec(JsonElement e) {
        if (e != null && e.isJsonObject() && e.getAsJsonObject().has("vector")) {
            e = e.getAsJsonObject().get("vector");
        }
        if (e != null && e.isJsonArray()) {
            JsonArray a = e.getAsJsonArray();
            Molang.Expr x = a.size() > 0 ? scalar(a.get(0)) : Molang.constant(0);
            Molang.Expr y = a.size() > 1 ? scalar(a.get(1)) : x;
            Molang.Expr z = a.size() > 2 ? scalar(a.get(2)) : x;
            return new Molang.Expr[]{x, y, z};
        }
        Molang.Expr s = scalar(e);
        return new Molang.Expr[]{s, s, s};
    }

    private static boolean isTimeKey(String k) {
        try {
            Float.parseFloat(k);
            return true;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private static Channel channel(JsonElement e) {
        if (e == null) {
            return null;
        }
        Channel ch = new Channel();
        if (e.isJsonObject()) {
            JsonObject o = e.getAsJsonObject();
            boolean timed = !o.isEmpty() && o.keySet().stream().allMatch(BedrockClip::isTimeKey);
            if (timed) {
                for (Map.Entry<String, JsonElement> en : o.entrySet()) {
                    float time = Float.parseFloat(en.getKey());
                    JsonElement v = en.getValue();
                    Molang.Expr[] pre, post;
                    Lerp lerp = Lerp.LINEAR;
                    if (v.isJsonObject() && (v.getAsJsonObject().has("pre") || v.getAsJsonObject().has("post")
                            || v.getAsJsonObject().has("lerp_mode"))) {
                        JsonObject ko = v.getAsJsonObject();
                        post = vec(ko.has("post") ? ko.get("post") : ko.get("pre"));
                        pre = ko.has("pre") ? vec(ko.get("pre")) : post;
                        if (ko.has("lerp_mode")) {
                            String m = ko.get("lerp_mode").getAsString().toLowerCase(Locale.ROOT);
                            lerp = m.contains("catmull") || m.contains("smooth") || m.contains("bezier") ? Lerp.SMOOTH
                                    : m.contains("step") ? Lerp.STEP : Lerp.LINEAR;
                        }
                    } else {
                        pre = post = vec(v);
                    }
                    ch.keys.add(new Key(time, pre, post, lerp));
                }
                ch.keys.sort((a, b) -> Float.compare(a.time, b.time));
                return ch;
            }
        }
        ch.value = vec(e);
        return ch;
    }

    /** Lee una animacion; null si no lo es. */
    public static BedrockClip read(String name, JsonObject o) {
        if (!o.has("bones") && !o.has("animation_length")) {
            return null;
        }
        float length = o.has("animation_length") ? o.get("animation_length").getAsFloat() : 0;
        Loop loop = Loop.ONCE;
        if (o.has("loop")) {
            JsonElement l = o.get("loop");
            if (l.isJsonPrimitive() && l.getAsJsonPrimitive().isBoolean()) {
                loop = l.getAsBoolean() ? Loop.LOOP : Loop.ONCE;
            } else if (l.isJsonPrimitive()) {
                String s = l.getAsString();
                loop = s.contains("hold") ? Loop.HOLD : "true".equals(s) ? Loop.LOOP : Loop.ONCE;
            }
        }
        BedrockClip clip = new BedrockClip(name, length, loop);
        if (o.has("bones") && o.get("bones").isJsonObject()) {
            float maxKey = 0;
            for (Map.Entry<String, JsonElement> en : o.getAsJsonObject("bones").entrySet()) {
                if (!en.getValue().isJsonObject()) {
                    continue;
                }
                JsonObject bo = en.getValue().getAsJsonObject();
                Bone b = new Bone();
                b.rotation = channel(bo.get("rotation"));
                b.position = channel(bo.get("position"));
                b.scale = channel(bo.get("scale"));
                for (Channel c : new Channel[]{b.rotation, b.position, b.scale}) {
                    if (c != null && !c.keys.isEmpty()) {
                        maxKey = Math.max(maxKey, c.keys.get(c.keys.size() - 1).time);
                    }
                }
                clip.bones.put(boneKey(en.getKey()), b);
            }
            if (clip.length <= 0 && maxKey > 0) {
                return new BedrockClip(name, maxKey, loop).copyBones(clip);
            }
        }
        return clip;
    }

    private BedrockClip copyBones(BedrockClip from) {
        this.bones.putAll(from.bones);
        return this;
    }
}
