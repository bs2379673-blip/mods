package com.tunombre.jefes.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;

/** Definicion de un jefe (assets/jefes/bosses/<id>.json o config/jefes/bosses/<id>.json): cuerpo, velocidades, huesos, sonidos, QTE. */
public final class Spec {
    public final String id, name, title;
    public final float scale, width, height, eye, cameraDist;
    public final float walk, sprint, sneak, jump, turn, stride;   // velocidades en bloques por tick
    public final float reach, damage, knock, leapFwd, leapUp;
    public final boolean blind;
    public final JsonObject bones, grab, anims, sounds, screamer;
    public final List<JsonObject> qte = new ArrayList<>();
    public final JsonObject raw;

    public Spec(String json) {
        JsonObject j = JsonParser.parseString(json).getAsJsonObject();
        raw = j;
        id = j.get("id").getAsString();
        name = j.get("name").getAsString();
        title = str(j, "title", name.toUpperCase());
        scale = f(j, "scale", 1F); width = f(j, "width", 1.4F); height = f(j, "height", 5F); eye = f(j, "eye", height * 0.9F);
        cameraDist = f(j, "cameraDist", 9F);
        JsonObject sp = obj(j, "speed");
        walk = f(sp, "walk", 0.09F); sprint = f(sp, "sprint", 0.22F); sneak = f(sp, "sneak", 0.045F); jump = f(sp, "jump", 0.55F); turn = f(sp, "turn", 8F); stride = f(sp, "stride", 2.4F);
        JsonObject lp = obj(j, "leap"); leapFwd = f(lp, "fwd", 1.6F); leapUp = f(lp, "up", 0.95F);
        reach = f(j, "reach", 4F); damage = f(j, "damage", 8F); knock = f(j, "knock", 1.1F);
        blind = j.has("blind") && j.get("blind").getAsBoolean();
        bones = obj(j, "bones"); grab = obj(j, "grab"); anims = obj(j, "anims"); sounds = obj(j, "sounds"); screamer = obj(j, "screamer");
        if (j.has("qte")) for (JsonElement e : j.getAsJsonArray("qte")) qte.add(e.getAsJsonObject());
    }

    public int bone(String k) { return bones.get(k).getAsInt(); }

    public int[] boneList(String k) {
        JsonArray a = bones.getAsJsonArray(k);
        int[] r = new int[a.size()];
        for (int i = 0; i < r.length; i++) r[i] = a.get(i).getAsInt();
        return r;
    }

    public String anim(String k, String def) { return anims.has(k) ? anims.get(k).getAsString() : def; }

    private static JsonObject obj(JsonObject j, String k) { return j.has(k) ? j.getAsJsonObject(k) : new JsonObject(); }

    private static float f(JsonObject j, String k, float d) { return j.has(k) ? j.get(k).getAsFloat() : d; }

    private static String str(JsonObject j, String k, String d) { return j.has(k) ? j.get(k).getAsString() : d; }
}
