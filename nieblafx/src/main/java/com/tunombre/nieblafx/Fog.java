package com.tunombre.nieblafx;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Estado de la niebla (lo guarda y lo manda el servidor). */
public final class Fog {
    public static class Zone {
        public String id = "";
        public String dim = "minecraft:overworld";
        public double x, y, z;
        /** radio horizontal */
        public float radius = 40F;
        /** semialtura */
        public float height = 12F;
        /** densidad por bloque */
        public float density = 0.08F;
        public String color = "#9aa0a6";
        public boolean on = true;
    }

    public static class State {
        public boolean on = false;
        public String color = "#9aa0a6";
        /** densidad por bloque (0.012 = niebla clara a 128 bloques) */
        public float density = 0.012F;
        /** distancia maxima que se calcula */
        public float maxDist = 128F;
        /** altura a partir de la cual la niebla se adelgaza (sin uso si caida = 0) */
        public float baseY = 64F;
        /** adelgazamiento por bloque sobre baseY (0 = uniforme) */
        public float falloff = 0F;
        /** cuanto se nota el ruido (0 = niebla lisa, 1 = muy irregular) */
        public float noise = 0.85F;
        /** tamano de las nubes de niebla en bloques */
        public float scale = 24F;
        public float wind = 0.3F;
        /** cuanta niebla se ve sobre el cielo/horizonte (0..1) */
        public float sky = 0.35F;
        /** segundos de fundido al cambiar */
        public float fade = 4F;
        public List<Zone> zones = new ArrayList<>();
    }

    static volatile State S = new State();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Gson COMPACT = new Gson();

    private Fog() { }

    static Path file() { return FMLPaths.CONFIGDIR.get().resolve("nieblafx.json"); }

    public static synchronized void load() {
        try {
            Path f = file();
            if (Files.exists(f)) {
                State s = GSON.fromJson(Files.readString(f), State.class);
                if (s != null) { S = s; fix(); }
            }
        } catch (Exception e) {
            NieblaFX.LOGGER.error("No se pudo leer nieblafx.json", e);
        }
    }

    public static synchronized void save() {
        try {
            Files.createDirectories(file().getParent());
            Files.writeString(file(), GSON.toJson(S));
        } catch (Exception e) {
            NieblaFX.LOGGER.error("No se pudo guardar nieblafx.json", e);
        }
    }

    static void fix() {
        if (S.zones == null) S.zones = new ArrayList<>();
        if (S.color == null) S.color = "#9aa0a6";
        for (Zone z : S.zones) { if (z.color == null) z.color = "#9aa0a6"; if (z.dim == null) z.dim = "minecraft:overworld"; if (z.id == null) z.id = ""; }
    }

    public static String json() { return COMPACT.toJson(S); }

    /** reemplaza todo el estado (lo usa Obscure UI); devuelve null si va bien */
    public static synchronized String apply(String json) {
        try {
            State s = COMPACT.fromJson(json, State.class);
            if (s == null) return "vacio";
            S = s; fix();
            save();
            Net.sendAll(json());
            return null;
        } catch (Exception e) {
            return "JSON invalido: " + e.getMessage();
        }
    }

    public static int parseColor(String s) {
        try {
            String h = s.startsWith("#") ? s.substring(1) : s;
            return (int) Long.parseLong(h, 16) & 0xFFFFFF;
        } catch (Exception e) { return 0x9AA0A6; }
    }

    public static Zone zone(String id) {
        for (Zone z : S.zones) if (z.id.equalsIgnoreCase(id)) return z;
        return null;
    }

    /** cambia un parametro global; devuelve un error o null */
    public static synchronized String set(String key, String v) {
        try {
            switch (key.toLowerCase(Locale.ROOT)) {
                case "on" -> S.on = Boolean.parseBoolean(v);
                case "color" -> { if (!v.matches("#?[0-9a-fA-F]{6}")) return "color invalido, usa #rrggbb"; S.color = v.startsWith("#") ? v : "#" + v; }
                case "densidad" -> S.density = Math.max(0F, Float.parseFloat(v));
                case "distancia" -> S.maxDist = Math.max(8F, Math.min(512F, Float.parseFloat(v)));
                case "base" -> S.baseY = Float.parseFloat(v);
                case "caida" -> S.falloff = Math.max(0F, Float.parseFloat(v));
                case "escala" -> S.scale = Math.max(2F, Math.min(200F, Float.parseFloat(v)));
                case "ruido" -> S.noise = Math.max(0F, Math.min(1F, Float.parseFloat(v)));
                case "viento" -> S.wind = Float.parseFloat(v);
                case "cielo" -> S.sky = Math.max(0F, Math.min(1F, Float.parseFloat(v)));
                case "fade", "transicion" -> S.fade = Math.max(0F, Float.parseFloat(v));
                default -> { return "parametro desconocido: " + key; }
            }
        } catch (NumberFormatException e) { return "valor invalido: " + v; }
        return null;
    }

    public static synchronized String setZone(String id, String key, String v) {
        Zone z = zone(id);
        if (z == null) return "no existe la zona " + id;
        try {
            switch (key.toLowerCase(Locale.ROOT)) {
                case "radio" -> z.radius = Math.max(1F, Float.parseFloat(v));
                case "alto" -> z.height = Math.max(1F, Float.parseFloat(v));
                case "densidad" -> z.density = Math.max(0F, Float.parseFloat(v));
                case "color" -> { if (!v.matches("#?[0-9a-fA-F]{6}")) return "color invalido, usa #rrggbb"; z.color = v.startsWith("#") ? v : "#" + v; }
                case "on" -> z.on = Boolean.parseBoolean(v);
                default -> { return "propiedad desconocida: " + key; }
            }
        } catch (NumberFormatException e) { return "valor invalido: " + v; }
        return null;
    }
}
