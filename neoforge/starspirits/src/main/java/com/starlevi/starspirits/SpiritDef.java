package com.starlevi.starspirits;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Un espiritu definido en el servidor (config/starspirits/espiritus/&lt;id&gt;.json). Lo leen el servidor
 * (rareza, beneficios, biomas) y los clientes (forma, textura, modelo, escala, particulas).
 */
public final class SpiritDef {
    /** Formas que trae el mod (modelos propios, animados). Con "modelo" se usa uno de Blockbench. */
    public static final List<String> FORMS = List.of("hongo", "flor", "loto", "arbusto", "bonsai", "campana", "rosa");

    public final String id;
    public final String json;
    public final String name;
    public final Rarity rarity;
    public final String form;
    public final String modelFile;
    public final String textureFile;
    public final String glowFile;
    /** Textura con los ojos cerrados (para parpadear y dormir), o null. */
    public final String blinkFile;
    public final float scale;
    /** Giro del modelo en grados (para modelos que vienen mirando de costado). */
    public final float turn;
    public final int weight;
    public final boolean enabled;
    public final String particle;
    public final int tint;
    public final List<String> biomes = new ArrayList<>();
    public final JsonArray benefits;
    public final String description;
    /** Animaciones especiales que hace de vez en cuando: vueltita, saludo, baile, volar, corazon. */
    public final List<String> specials = new ArrayList<>();
    /** Piezas que aletean como alas (por nombre: "pelo", "ala"...). */
    public final List<String> wings = new ArrayList<>();

    public SpiritDef(String id, String json) {
        this.id = id;
        this.json = json;
        JsonObject o = JsonParser.parseString(json).getAsJsonObject();
        this.name = str(o, "nombre", id);
        this.rarity = Rarity.parse(str(o, "rareza", "comun"));
        String f = str(o, "forma", "hongo").toLowerCase(Locale.ROOT);
        this.form = FORMS.contains(f) ? f : "hongo";
        this.modelFile = lower(str(o, "modelo", null));
        this.textureFile = lower(str(o, "textura", null));
        this.glowFile = lower(str(o, "brillo", null));
        this.blinkFile = lower(str(o, "textura_parpadeo", null));
        this.scale = o.has("escala") ? Math.max(0.3f, Math.min(3f, o.get("escala").getAsFloat())) : 1f;
        this.turn = o.has("giro") ? o.get("giro").getAsFloat() : 0f;
        this.weight = o.has("peso") ? Math.max(0, o.get("peso").getAsInt()) : 1;
        this.enabled = !o.has("habilitado") || o.get("habilitado").getAsBoolean();
        this.particle = str(o, "particulas", null);
        this.tint = o.has("color") ? parseColor(o.get("color").getAsString(), this.rarity.color) : this.rarity.color;
        this.description = str(o, "descripcion", "");
        if (o.has("biomas") && o.get("biomas").isJsonArray()) {
            for (JsonElement e : o.getAsJsonArray("biomas")) {
                this.biomes.add(e.getAsString());
            }
        }
        if (o.has("especiales") && o.get("especiales").isJsonArray()) {
            for (JsonElement e : o.getAsJsonArray("especiales")) {
                this.specials.add(e.getAsString().toLowerCase(Locale.ROOT));
            }
        }
        if (o.has("alas") && o.get("alas").isJsonArray()) {
            for (JsonElement e : o.getAsJsonArray("alas")) {
                this.wings.add(e.getAsString().toLowerCase(Locale.ROOT));
            }
        }
        this.benefits = o.has("beneficios") && o.get("beneficios").isJsonArray() ? o.getAsJsonArray("beneficios") : new JsonArray();
    }

    private static String str(JsonObject o, String k, String def) {
        return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsString() : def;
    }

    private static String lower(String s) {
        return s == null ? null : s.toLowerCase(Locale.ROOT);
    }

    private static int parseColor(String s, int def) {
        try {
            return Integer.parseInt(s.replace("#", ""), 16);
        } catch (Exception e) {
            return def;
        }
    }

    /** Archivos del servidor que usa este espiritu (textura, brillo, modelo). */
    public List<String> files() {
        List<String> out = new ArrayList<>();
        if (this.textureFile != null) {
            out.add(this.textureFile);
        }
        if (this.glowFile != null) {
            out.add(this.glowFile);
        }
        if (this.modelFile != null) {
            out.add(this.modelFile);
        }
        if (this.blinkFile != null) {
            out.add(this.blinkFile);
        }
        return out;
    }
}
