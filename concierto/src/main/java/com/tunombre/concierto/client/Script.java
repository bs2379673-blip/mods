package com.tunombre.concierto.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tunombre.concierto.Show;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * guion de una cancion: una lista de ordenes con su segundo. Los cambios de "look" quedan puestos hasta el siguiente cambio;
 * los efectos ("fx") se disparan una vez al pasar por su segundo.
 * Se lee de config/concierto/shows/<cancion>.json (si existe, para editar sin recompilar) o de dentro del mod.
 */
public final class Script {
    public static final class Cue {
        public final float t;
        public final JsonObject o;
        Cue(float t, JsonObject o) { this.t = t; this.o = o; }
    }

    /** lo que se ve en cada momento */
    public static final class Look {
        public int scene = 8;
        public String floor = "off", beams = "off", swap = "off";
        public float eq = 0F;
        public boolean ring = false;
        public float[][] col = {{1F, 0.42F, 0.6F}, {1F, 0.6F, 0.42F}, {1F, 0.82F, 0.88F}};
        public float kick = 0.6F, snare = 0.3F, accent = 0.8F;

        void apply(JsonObject o) {
            if (o.has("scene")) {
                int i = Show.SCENES.indexOf(o.get("scene").getAsString());
                if (i >= 0) scene = i;
            }
            if (o.has("floor")) floor = o.get("floor").getAsString();
            if (o.has("beams")) beams = o.get("beams").getAsString();
            if (o.has("eq")) eq = o.get("eq").getAsFloat();
            if (o.has("ring")) ring = o.get("ring").getAsBoolean();
            if (o.has("swap")) swap = o.get("swap").getAsString();
            if (o.has("col") && o.get("col").isJsonArray()) {
                JsonArray a = o.getAsJsonArray("col");
                for (int i = 0; i < 3 && i < a.size(); i++) col[i] = hex(a.get(i).getAsString());
            }
            if (o.has("react") && o.get("react").isJsonObject()) {
                JsonObject r = o.getAsJsonObject("react");
                if (r.has("kick")) kick = r.get("kick").getAsFloat();
                if (r.has("snare")) snare = r.get("snare").getAsFloat();
                if (r.has("accent")) accent = r.get("accent").getAsFloat();
            }
        }
    }

    public final List<Cue> cues = new ArrayList<>();
    private int next;
    public final Look look = new Look();

    public static float[] hex(String h) {
        String c = h.startsWith("#") ? h.substring(1) : h;
        int v = Integer.parseInt(c, 16);
        return new float[]{((v >> 16) & 255) / 255F, ((v >> 8) & 255) / 255F, (v & 255) / 255F};
    }

    /** null si no hay guion para esa cancion */
    public static Script load(String key) {
        try {
            Path p = FMLPaths.CONFIGDIR.get().resolve("concierto").resolve("shows").resolve(key + ".json");
            JsonElement root;
            if (Files.isRegularFile(p)) {
                try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) { root = JsonParser.parseReader(r); }
            } else {
                var res = Minecraft.getInstance().getResourceManager().getResource(new ResourceLocation("concierto", "shows/" + key + ".json"));
                if (res.isEmpty()) return null;
                try (InputStream in = res.get().open(); Reader r = new java.io.InputStreamReader(in, StandardCharsets.UTF_8)) { root = JsonParser.parseReader(r); }
            }
            Script s = new Script();
            for (JsonElement e : root.getAsJsonObject().getAsJsonArray("cues")) {
                JsonObject o = e.getAsJsonObject();
                s.cues.add(new Cue(o.get("t").getAsFloat(), o));
            }
            s.cues.sort((a, b) -> Float.compare(a.t, b.t));
            return s;
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * avanza el guion hasta el instante t. Devuelve los efectos que se cruzaron (vacio si no hubo ninguno).
     * Al engancharse tarde (saltar a la mitad) los efectos viejos no se disparan.
     */
    public List<JsonObject> advance(float t) {
        List<JsonObject> fx = new ArrayList<>();
        while (next < cues.size() && cues.get(next).t <= t) {
            Cue c = cues.get(next++);
            look.apply(c.o);
            if (c.o.has("fx") && t - c.t < 0.6F) fx.add(c.o);
        }
        return fx;
    }
}
