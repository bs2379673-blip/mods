package com.starlevi.starspirits;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;

/** config/starspirits/config.json: probabilidades por rareza y tiempos. Se relee con /starspirits recargar. */
public final class Config {
    public static final Map<Rarity, Integer> WEIGHTS = new EnumMap<>(Rarity.class);
    /** Minutos que tarda un punto de extraccion en volver a tener un espiritu. */
    public static int rechargeMinutes = 20;
    /** Duracion de la animacion de extraccion (ticks). */
    public static int extractTicks = 70;
    /** Cuantos espiritus como maximo corren atras de cada jugador (los demas quedan guardados). */
    public static int maxFollowers = 30;

    private Config() {
    }

    public static Path folder() {
        return FMLPaths.CONFIGDIR.get().resolve(StarSpirits.MOD_ID);
    }

    public static void load() {
        for (Rarity r : Rarity.values()) {
            WEIGHTS.put(r, r.defaultWeight);
        }
        Path f = folder().resolve("config.json");
        try {
            Files.createDirectories(folder());
            if (!Files.exists(f)) {
                Files.writeString(f, defaults(), StandardCharsets.UTF_8);
            }
            JsonObject o = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
            if (o.has("probabilidades")) {
                JsonObject p = o.getAsJsonObject("probabilidades");
                for (Rarity r : Rarity.values()) {
                    if (p.has(r.key())) {
                        WEIGHTS.put(r, Math.max(0, p.get(r.key()).getAsInt()));
                    }
                }
            }
            if (o.has("recarga_minutos")) {
                rechargeMinutes = Math.max(0, o.get("recarga_minutos").getAsInt());
            }
            if (o.has("duracion_extraccion_ticks")) {
                extractTicks = Math.max(30, Math.min(400, o.get("duracion_extraccion_ticks").getAsInt()));
            }
            if (o.has("max_siguiendo")) {
                maxFollowers = Math.max(1, Math.min(200, o.get("max_siguiendo").getAsInt()));
            }
        } catch (Exception e) {
            StarSpirits.LOGGER.warn("StarSpirits: error en {}: {}", f, e.toString());
        }
    }

    private static String defaults() {
        Gson g = new GsonBuilder().setPrettyPrinting().create();
        JsonObject o = new JsonObject();
        JsonObject p = new JsonObject();
        for (Rarity r : Rarity.values()) {
            p.addProperty(r.key(), r.defaultWeight);
        }
        o.add("probabilidades", p);
        o.addProperty("recarga_minutos", rechargeMinutes);
        o.addProperty("duracion_extraccion_ticks", extractTicks);
        o.addProperty("max_siguiendo", maxFollowers);
        return g.toJson(o);
    }
}
