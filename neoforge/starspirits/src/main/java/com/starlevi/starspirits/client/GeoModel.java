package com.starlevi.starspirits.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Modelos de Blockbench (formato Bedrock, "geo.json", con UV de caja) subidos al servidor. Cada
 * hueso pasa a ser una pieza; los cubos girados van en una pieza propia. Los nombres de los huesos
 * deciden la animacion (ver {@link SpiritRig}).
 */
public final class GeoModel {
    private GeoModel() {
    }

    private static float[] vec(JsonObject o, String k, float... def) {
        if (!o.has(k) || !o.get(k).isJsonArray()) {
            return def;
        }
        JsonArray a = o.getAsJsonArray(k);
        float[] out = new float[a.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = a.get(i).getAsFloat();
        }
        return out;
    }

    public static SpiritRig parse(byte[] data) {
        JsonObject root = JsonParser.parseString(new String(data, StandardCharsets.UTF_8)).getAsJsonObject();
        JsonObject geo = root.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
        JsonObject desc = geo.has("description") ? geo.getAsJsonObject("description") : new JsonObject();
        int tw = desc.has("texture_width") ? desc.get("texture_width").getAsInt() : 64;
        int th = desc.has("texture_height") ? desc.get("texture_height").getAsInt() : 64;
        Map<String, JsonObject> bones = new LinkedHashMap<>();
        for (JsonElement e : geo.getAsJsonArray("bones")) {
            JsonObject b = e.getAsJsonObject();
            bones.put(b.get("name").getAsString().toLowerCase(Locale.ROOT), b);
        }
        Forms.Builder fb = new Forms.Builder();
        Map<String, PartDefinition> made = new HashMap<>();
        float[] top = {24};
        for (String name : bones.keySet()) {
            make(name, bones, made, fb, top);
        }
        SpiritRig rig = fb.bake(tw, th);
        rig.topY = 24 - top[0];
        return rig;
    }

    private static PartDefinition make(String name, Map<String, JsonObject> bones, Map<String, PartDefinition> made,
                                       Forms.Builder fb, float[] top) {
        PartDefinition have = made.get(name);
        if (have != null) {
            return have;
        }
        JsonObject b = bones.get(name);
        String parentName = b.has("parent") ? b.get("parent").getAsString().toLowerCase(Locale.ROOT) : null;
        PartDefinition parent = parentName != null && bones.containsKey(parentName) ? make(parentName, bones, made, fb, top) : fb.root();
        float[] pp = parentName != null && bones.containsKey(parentName) ? vec(bones.get(parentName), "pivot", 0, 0, 0) : new float[]{0, 0, 0};
        float[] piv = vec(b, "pivot", 0, 0, 0);
        float[] rot = vec(b, "rotation", 0, 0, 0);
        // Bedrock (y arriba, pies en 0) -> modelo del juego (y abajo, pies en 24), relativo al padre
        float ox = piv[0] - pp[0], oy = -(piv[1] - pp[1]), oz = piv[2] - pp[2];
        if (parentName == null || !bones.containsKey(parentName)) {
            oy = 24 - piv[1];
        }
        CubeListBuilder cubes = CubeListBuilder.create();
        Map<String, Object[]> rotated = new LinkedHashMap<>();
        int extra = 0;
        if (b.has("cubes")) {
            for (JsonElement ce : b.getAsJsonArray("cubes")) {
                JsonObject c = ce.getAsJsonObject();
                float[] o = vec(c, "origin", 0, 0, 0);
                float[] s = vec(c, "size", 1, 1, 1);
                float[] uv = c.has("uv") && c.get("uv").isJsonArray() ? vec(c, "uv", 0, 0) : new float[]{0, 0};
                float inflate = c.has("inflate") ? c.get("inflate").getAsFloat() : 0;
                boolean mirror = c.has("mirror") && c.get("mirror").getAsBoolean();
                top[0] = Math.min(top[0], 24 - (o[1] + s[1]));
                if (c.has("rotation")) {
                    // cubo girado: pieza propia con su pivote
                    float[] cp = vec(c, "pivot", o[0] + s[0] / 2, o[1] + s[1] / 2, o[2] + s[2] / 2);
                    float[] cr = vec(c, "rotation", 0, 0, 0);
                    CubeListBuilder one = CubeListBuilder.create().texOffs((int) uv[0], (int) uv[1]).mirror(mirror)
                            .addBox(o[0] - cp[0], cp[1] - o[1] - s[1], o[2] - cp[2], s[0], s[1], s[2], new CubeDeformation(inflate));
                    rotated.put(name + "_cubo" + extra, new Object[]{one, cp, cr});
                    extra++;
                    continue;
                }
                cubes.texOffs((int) uv[0], (int) uv[1]).mirror(mirror)
                        .addBox(o[0] - piv[0], piv[1] - o[1] - s[1], o[2] - piv[2], s[0], s[1], s[2], new CubeDeformation(inflate));
            }
        }
        PartDefinition d = fb.add(parent, name, cubes, PartPose.offsetAndRotation(ox, oy, oz,
                -rot[0] * Mth.DEG_TO_RAD, -rot[1] * Mth.DEG_TO_RAD, rot[2] * Mth.DEG_TO_RAD));
        made.put(name, d);
        for (Map.Entry<String, Object[]> r : rotated.entrySet()) {
            Object[] v = r.getValue();
            float[] cp = (float[]) v[1], cr = (float[]) v[2];
            fb.add(d, r.getKey(), (CubeListBuilder) v[0], PartPose.offsetAndRotation(cp[0] - piv[0], piv[1] - cp[1], cp[2] - piv[2],
                    -cr[0] * Mth.DEG_TO_RAD, -cr[1] * Mth.DEG_TO_RAD, cr[2] * Mth.DEG_TO_RAD));
        }
        return d;
    }
}
