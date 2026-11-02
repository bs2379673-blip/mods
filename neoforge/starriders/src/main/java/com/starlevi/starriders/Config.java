package com.starlevi.starriders;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** config/starriders/config.json (se relee con /starriders recargar). */
public final class Config {
    /** Probabilidad por jugador y por minuto de que aparezca una montura salvaje cerca (0.0002 = muy muy rara). */
    public static double wildChancePerMinute = 0.0002;
    /** Minutos que se queda la salvaje antes de irse volando. */
    public static int wildMinutes = 5;
    /** Manzanas doradas que hay que darle (en promedio) para domarla. */
    public static int tameFeedings = 4;
    /** Velocidad de vuelo (bloques por tick): crucero y con sprint. */
    public static double flySpeed = 0.9, flyBoost = 1.45;
    /** Minutos sin jinete despues de los cuales la montura vuelve al silbato. */
    public static int idleMinutes = 10;

    private Config() {
    }

    public static Path folder() {
        return FMLPaths.CONFIGDIR.get().resolve(StarRiders.MOD_ID);
    }

    public static void load() {
        Path f = folder().resolve("config.json");
        try {
            Files.createDirectories(folder());
            if (!Files.exists(f)) {
                JsonObject o = new JsonObject();
                o.addProperty("probabilidad_salvaje_por_minuto", wildChancePerMinute);
                o.addProperty("minutos_salvaje", wildMinutes);
                o.addProperty("manzanas_para_domar", tameFeedings);
                o.addProperty("velocidad_vuelo", flySpeed);
                o.addProperty("velocidad_vuelo_sprint", flyBoost);
                o.addProperty("minutos_sin_jinete", idleMinutes);
                Files.writeString(f, new GsonBuilder().setPrettyPrinting().create().toJson(o), StandardCharsets.UTF_8);
            }
            JsonObject o = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
            wildChancePerMinute = get(o, "probabilidad_salvaje_por_minuto", wildChancePerMinute);
            wildMinutes = (int) get(o, "minutos_salvaje", wildMinutes);
            tameFeedings = Math.max(1, (int) get(o, "manzanas_para_domar", tameFeedings));
            flySpeed = get(o, "velocidad_vuelo", flySpeed);
            flyBoost = get(o, "velocidad_vuelo_sprint", flyBoost);
            idleMinutes = Math.max(1, (int) get(o, "minutos_sin_jinete", idleMinutes));
        } catch (Exception e) {
            StarRiders.LOGGER.warn("StarRiders: error en {}: {}", f, e.toString());
        }
    }

    private static double get(JsonObject o, String k, double def) {
        try {
            return o.has(k) ? o.get(k).getAsDouble() : def;
        } catch (Exception e) {
            return def;
        }
    }
}
