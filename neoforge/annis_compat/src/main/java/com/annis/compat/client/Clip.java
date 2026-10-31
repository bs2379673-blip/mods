package com.annis.compat.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Una animacion en el formato de Punchy Moves / Punchy Studio (Bedrock 1.8.0): huesos con
 * rotacion (grados) y posicion (pixeles), fotogramas lineales o suaves (catmullrom).
 */
public final class Clip {
    public final String name;
    public final float length;
    public final boolean loop;
    final Map<String, Bone> bones = new HashMap<>();

    static final class Key {
        final float time;
        final float[] pre;
        final float[] post;
        final boolean smooth;

        Key(float time, float[] pre, float[] post, boolean smooth) {
            this.time = time;
            this.pre = pre;
            this.post = post;
            this.smooth = smooth;
        }
    }

    static final class Channel {
        final List<Key> keys = new ArrayList<>();

        void sample(float t, float[] out) {
            Key first = this.keys.get(0);
            if (this.keys.size() == 1 || t <= first.time) {
                copy(first.pre, out);
                return;
            }
            Key last = this.keys.get(this.keys.size() - 1);
            if (t >= last.time) {
                copy(last.post, out);
                return;
            }
            int k = 0;
            while (k + 1 < this.keys.size() && this.keys.get(k + 1).time <= t) {
                k++;
            }
            Key a = this.keys.get(k), b = this.keys.get(k + 1);
            float u = b.time > a.time ? (t - a.time) / (b.time - a.time) : 1.0f;
            if (a.smooth || b.smooth) {
                float[] p0 = k > 0 ? this.keys.get(k - 1).post : a.post;
                float[] p3 = k + 2 < this.keys.size() ? this.keys.get(k + 2).pre : b.pre;
                for (int c = 0; c < 3; c++) {
                    out[c] = catmull(p0[c], a.post[c], b.pre[c], p3[c], u);
                }
            } else {
                for (int c = 0; c < 3; c++) {
                    out[c] = a.post[c] + (b.pre[c] - a.post[c]) * u;
                }
            }
        }

        private static void copy(float[] v, float[] out) {
            out[0] = v[0];
            out[1] = v[1];
            out[2] = v[2];
        }

        private static float catmull(float p0, float p1, float p2, float p3, float t) {
            float t2 = t * t, t3 = t2 * t;
            return 0.5f * (2 * p1 + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 + (-p0 + 3 * p1 - 3 * p2 + p3) * t3);
        }
    }

    static final class Bone {
        Channel rotation;
        Channel position;
    }

    private Clip(String name, float length, boolean loop) {
        this.name = name;
        this.length = length;
        this.loop = loop;
    }

    /** Tiempo dentro de la animacion para unos segundos transcurridos. */
    public float time(float seconds) {
        if (this.length <= 0) {
            return 0;
        }
        if (this.loop) {
            float t = seconds % this.length;
            return t < 0 ? t + this.length : t;
        }
        return Math.max(0, Math.min(seconds, this.length));
    }

    /** Rotacion (grados) del hueso en ese tiempo; false si la animacion no lo mueve. */
    public boolean rotation(String bone, float t, float[] out) {
        Bone b = this.bones.get(bone);
        if (b == null || b.rotation == null) {
            return false;
        }
        b.rotation.sample(t, out);
        return true;
    }

    /** Posicion (pixeles, Y hacia arriba) del hueso en ese tiempo; false si no la mueve. */
    public boolean position(String bone, float t, float[] out) {
        Bone b = this.bones.get(bone);
        if (b == null || b.position == null) {
            return false;
        }
        b.position.sample(t, out);
        return true;
    }

    public boolean has(String bone) {
        return this.bones.containsKey(bone);
    }

    static Clip read(String name, JsonObject o) {
        float length = o.has("animation_length") ? o.get("animation_length").getAsFloat() : 0;
        boolean loop = false;
        if (o.has("loop")) {
            JsonElement l = o.get("loop");
            loop = l.isJsonPrimitive() && (l.getAsJsonPrimitive().isBoolean() ? l.getAsBoolean() : "true".equalsIgnoreCase(l.getAsString()));
        }
        Clip clip = new Clip(name, length, loop);
        if (!o.has("bones") || !o.get("bones").isJsonObject()) {
            return clip;
        }
        for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("bones").entrySet()) {
            if (!e.getValue().isJsonObject()) {
                continue;
            }
            JsonObject bo = e.getValue().getAsJsonObject();
            Bone bone = new Bone();
            bone.rotation = channel(bo.get("rotation"));
            bone.position = channel(bo.get("position"));
            if (bone.rotation != null || bone.position != null) {
                clip.bones.put(e.getKey().toLowerCase(Locale.ROOT), bone);
            }
        }
        return clip;
    }

    private static Channel channel(JsonElement e) {
        if (e == null || e.isJsonNull()) {
            return null;
        }
        Channel ch = new Channel();
        if (e.isJsonArray() || e.isJsonPrimitive()) {
            float[] v = vec(e);
            ch.keys.add(new Key(0, v, v, false));
            return ch;
        }
        if (!e.isJsonObject()) {
            return null;
        }
        for (Map.Entry<String, JsonElement> k : e.getAsJsonObject().entrySet()) {
            float time;
            try {
                time = Float.parseFloat(k.getKey());
            } catch (NumberFormatException ex) {
                continue;
            }
            JsonElement v = k.getValue();
            if (v.isJsonObject()) {
                JsonObject ko = v.getAsJsonObject();
                float[] post = vec(ko.has("post") ? ko.get("post") : ko.get("pre"));
                float[] pre = ko.has("pre") ? vec(ko.get("pre")) : post;
                boolean smooth = ko.has("lerp_mode") && ko.get("lerp_mode").getAsString().toLowerCase(Locale.ROOT).contains("catmull");
                ch.keys.add(new Key(time, pre, post, smooth));
            } else {
                float[] f = vec(v);
                ch.keys.add(new Key(time, f, f, false));
            }
        }
        if (ch.keys.isEmpty()) {
            return null;
        }
        ch.keys.sort((a, b) -> Float.compare(a.time, b.time));
        return ch;
    }

    private static float[] vec(JsonElement e) {
        float[] v = new float[3];
        if (e == null) {
            return v;
        }
        if (e.isJsonArray()) {
            JsonArray a = e.getAsJsonArray();
            for (int i = 0; i < 3 && i < a.size(); i++) {
                v[i] = num(a.get(i));
            }
        } else if (e.isJsonPrimitive()) {
            float f = num(e);
            v[0] = v[1] = v[2] = f;
        }
        return v;
    }

    private static float num(JsonElement e) {
        try {
            return e.getAsFloat();
        } catch (Exception ex) {
            // formulas (Molang) no se usan en estas animaciones
            return 0;
        }
    }
}
