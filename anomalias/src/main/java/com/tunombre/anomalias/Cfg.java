package com.tunombre.anomalias;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Mensajes y comandos de las anomalias, editables desde Obscure UI (config/anomalias.json). */
public final class Cfg {
    private Cfg() { }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static final String[] MSG_KEYS = {"detectada", "expansion", "activa", "extra", "retirada", "neutralizada", "chat"};
    public static final String[] CMD_KEYS = {"cmdInicio", "cmdActiva", "cmdFin"};

    public static final class Data {
        public Map<String, String> global = new LinkedHashMap<>();
        public Map<String, Map<String, String>> tipos = new LinkedHashMap<>();
    }

    private static Data data = defaults();

    private static Data defaults() {
        Data d = new Data();
        d.global.put("detectada", "ANOMALÍA DETECTADA|MIRA HACIA ARRIBA");
        d.global.put("expansion", "ANOMALÍA EN EXPANSIÓN|ESPORAS DETECTADAS EN LA ATMÓSFERA");
        d.global.put("activa", "ANOMALÍA {COLOR} ACTIVA|NIVEL DE PELIGRO: {PELIGRO}");
        d.global.put("extra", "");
        d.global.put("retirada", "ANOMALÍA EN RETIRADA|EL CIELO VUELVE A LA NORMALIDAD");
        d.global.put("neutralizada", "ANOMALÍA NEUTRALIZADA|EL CIELO VUELVE A LA NORMALIDAD");
        d.global.put("chat", "");
        for (Tipo t : Tipo.values()) d.tipos.put(t.id, new LinkedHashMap<>());
        return d;
    }

    private static Path file() { return FMLPaths.CONFIGDIR.get().resolve("anomalias.json"); }

    public static synchronized void load() {
        try {
            Path f = file();
            if (Files.exists(f)) {
                Data d = GSON.fromJson(Files.readString(f), Data.class);
                if (d != null) {
                    Data base = defaults();
                    if (d.global != null) base.global.putAll(d.global);
                    if (d.tipos != null) for (var e : d.tipos.entrySet()) base.tipos.computeIfAbsent(e.getKey(), k -> new LinkedHashMap<>()).putAll(e.getValue());
                    data = base;
                }
            } else save();
        } catch (Exception e) { Anomalias.LOGGER.warn("No se pudo leer anomalias.json: {}", e.toString()); }
    }

    public static synchronized void save() {
        try { Files.writeString(file(), GSON.toJson(data)); } catch (Exception e) { Anomalias.LOGGER.warn("No se pudo guardar anomalias.json: {}", e.toString()); }
    }

    public static synchronized String json() { return GSON.toJson(data); }

    public static synchronized String apply(String json) {
        try {
            Data d = GSON.fromJson(json, Data.class);
            if (d == null) return "JSON vacio";
            Data base = defaults();
            if (d.global != null) base.global.putAll(d.global);
            if (d.tipos != null) for (var e : d.tipos.entrySet()) base.tipos.computeIfAbsent(e.getKey(), k -> new LinkedHashMap<>()).putAll(e.getValue());
            data = base;
            save();
            return "";
        } catch (Exception e) { return e.getMessage(); }
    }

    /** Texto del tipo; si esta vacio usa el global. Reemplaza {COLOR} y {PELIGRO}. */
    public static synchronized String get(Tipo t, String key) {
        String s = data.tipos.getOrDefault(t.id, Map.of()).get(key);
        if (s == null || s.isBlank()) s = data.global.getOrDefault(key, "");
        return s.replace("{COLOR}", t.nombre).replace("{PELIGRO}", t.peligro);
    }

    /** Solo el del tipo (para comandos: no hay valor global). */
    public static synchronized String own(Tipo t, String key) {
        String s = data.tipos.getOrDefault(t.id, Map.of()).get(key);
        return s == null ? "" : s.replace("{COLOR}", t.nombre).replace("{PELIGRO}", t.peligro);
    }

}
