package com.starlevi.starfest.client.sky;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.starlevi.starfest.client.Beat;
import com.starlevi.starfest.client.Palette;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Un cielo leido de su JSON: color de fondo y una lista de capas que se dibujan en orden. Todo lo
 * que falte toma un valor por defecto, asi un cielo puede ser tan simple o tan cargado como se quiera.
 */
public final class SkyDef {
    public final String name;
    public final float darken;
    public final int top;
    public final int horizon;
    public final int bottom;
    public final List<Layer> layers = new ArrayList<>();

    /** Color: fijo, o de la paleta de la cancion ("paleta", "paleta2"...). */
    public record Color(int rgb, int paletteSlot) {
        static Color parse(String s, int fallback) {
            if (s == null) {
                return new Color(fallback, -1);
            }
            s = s.trim().toLowerCase(Locale.ROOT);
            if (s.startsWith("paleta")) {
                int slot = 0;
                try {
                    slot = s.length() > 6 ? Integer.parseInt(s.substring(6)) - 1 : 0;
                } catch (NumberFormatException ignored) {
                    // paleta 1
                }
                return new Color(fallback, Math.max(0, slot));
            }
            try {
                return new Color(Integer.parseInt(s.replace("#", ""), 16), -1);
            } catch (NumberFormatException e) {
                return new Color(fallback, -1);
            }
        }

        public int get(Beat b) {
            return this.paletteSlot < 0 ? this.rgb : Palette.main(b, this.paletteSlot);
        }
    }

    /** Una capa: tipo y sus ajustes. */
    public static final class Layer {
        public final String type;
        public final JsonObject o;
        public final String reaction;
        public final float min;
        public final float opacity;
        public final boolean additive;
        public final boolean onlyDrop;
        public final float speed;
        public final List<Color> colors = new ArrayList<>();

        Layer(JsonObject o) {
            this.o = o;
            this.type = str("tipo", "estrellas");
            this.reaction = str("reaccion", "nivel");
            this.min = num("minimo", 0.45f);
            this.opacity = num("opacidad", 1.0f);
            this.additive = !o.has("aditivo") || o.get("aditivo").getAsBoolean();
            this.onlyDrop = o.has("solo_fuerte") && o.get("solo_fuerte").getAsBoolean();
            this.speed = num("velocidad", 1.0f);
            if (o.has("colores") && o.get("colores").isJsonArray()) {
                for (JsonElement e : o.getAsJsonArray("colores")) {
                    this.colors.add(Color.parse(e.getAsString(), 0xFFFFFF));
                }
            }
            if (this.colors.isEmpty()) {
                this.colors.add(Color.parse(str("color", "#ffffff"), 0xFFFFFF));
            }
        }

        public String str(String k, String def) {
            return this.o.has(k) && this.o.get(k).isJsonPrimitive() ? this.o.get(k).getAsString() : def;
        }

        public float num(String k, float def) {
            try {
                return this.o.has(k) ? this.o.get(k).getAsFloat() : def;
            } catch (Exception e) {
                return def;
            }
        }

        public int integer(String k, int def) {
            return Math.round(num(k, def));
        }

        public Color color(int i) {
            return this.colors.get(Math.floorMod(i, this.colors.size()));
        }

        /** Cuanto reacciona ahora (0..1) segun "reaccion". */
        public float react(Beat b) {
            if (!b.playing()) {
                return 0;
            }
            return switch (this.reaction) {
                case "graves" -> b.bass();
                case "medios" -> b.mid();
                case "agudos" -> b.high();
                case "golpe" -> b.pulse();
                case "bombo" -> b.kick();
                case "intensidad" -> Math.max(0, Math.min(1, (b.intensity() - 0.6f) / 0.8f));
                case "nada" -> 1;
                default -> b.level();
            };
        }

        /** Brillo final de la capa: entre "minimo" y 1 segun la musica (sin musica: "minimo"). */
        public float strength(Beat b) {
            if (this.onlyDrop && !(b.playing() && b.drop())) {
                return 0;
            }
            float r = Math.min(1, react(b));
            return this.opacity * (this.min + (1 - this.min) * r);
        }
    }

    public SkyDef(String name, String json) {
        this.name = name;
        JsonObject o = JsonParser.parseString(json).getAsJsonObject();
        this.darken = o.has("oscurecer") ? o.get("oscurecer").getAsFloat() : 0.9f;
        this.top = Color.parse(o.has("arriba") ? o.get("arriba").getAsString() : null, 0x02010A).rgb();
        this.horizon = Color.parse(o.has("horizonte") ? o.get("horizonte").getAsString() : null, 0x140828).rgb();
        this.bottom = Color.parse(o.has("abajo") ? o.get("abajo").getAsString() : null, 0x000000).rgb();
        if (o.has("capas") && o.get("capas").isJsonArray()) {
            JsonArray a = o.getAsJsonArray("capas");
            for (JsonElement e : a) {
                if (e.isJsonObject()) {
                    this.layers.add(new Layer(e.getAsJsonObject()));
                }
            }
        }
    }
}
