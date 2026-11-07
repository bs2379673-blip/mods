package com.starlevi.starscenes.talk;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.starlevi.starscenes.StarScenes;
import com.starlevi.starscenes.server.SceneLibrary;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/** Lee los .json de una carpeta de config/starscenes (con subcarpetas), aceptando comentarios. */
final class JsonFolder {
    private static final Map<String, List<String>> EXAMPLES = Map.of("dialogos", List.of("ejemplo.json"), "misiones", List.of("ejemplo_lena.json"),
            "tiendas", List.of("ejemplo_herrero.json"), "escenas", List.of("ejemplo_escena.json"));

    private JsonFolder() {
    }

    /** id (nombre del archivo, sin extension) -> contenido; los que fallan se anotan en errores. */
    static Map<String, JsonObject> read(String folder, List<String> errors) {
        Map<String, JsonObject> out = new LinkedHashMap<>();
        Path dir = SceneLibrary.root().resolve(folder);
        try {
            if (!Files.exists(dir)) {
                // la primera vez: ejemplos para copiar
                Files.createDirectories(dir);
                for (String name : EXAMPLES.getOrDefault(folder, List.of())) {
                    try (var in = JsonFolder.class.getResourceAsStream("/starscenes_ejemplos/" + folder + "/" + name)) {
                        if (in != null) {
                            Files.write(dir.resolve(name), in.readAllBytes());
                        }
                    }
                }
            }
            try (Stream<Path> s = Files.walk(dir)) {
                for (Path f : s.filter(Files::isRegularFile).filter(p -> p.toString().toLowerCase(Locale.ROOT).endsWith(".json")).sorted().toList()) {
                    String id = SceneLibrary.cleanId(f.getFileName().toString());
                    try {
                        JsonReader r = new JsonReader(new StringReader(Files.readString(f, StandardCharsets.UTF_8)));
                        r.setStrictness(Strictness.LENIENT);
                        JsonElement e = JsonParser.parseReader(r);
                        if (!e.isJsonObject()) {
                            throw new IllegalArgumentException("no es un objeto { }");
                        }
                        out.put(id, e.getAsJsonObject());
                    } catch (Exception e) {
                        errors.add(folder + "/" + f.getFileName() + ": " + e.getMessage());
                        StarScenes.LOGGER.warn("StarScenes: {} {}: {}", folder, f.getFileName(), e.toString());
                    }
                }
            }
        } catch (Exception e) {
            errors.add(folder + ": " + e);
        }
        return out;
    }

    static String str(JsonObject o, String key, String def) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? def : e.isJsonPrimitive() ? e.getAsString() : e.toString();
    }

    static int num(JsonObject o, String key, int def) {
        JsonElement e = o.get(key);
        try {
            return e == null || e.isJsonNull() ? def : e.getAsInt();
        } catch (Exception ex) {
            return def;
        }
    }

    static double dbl(JsonObject o, String key, double def) {
        JsonElement e = o.get(key);
        try {
            return e == null || e.isJsonNull() ? def : e.getAsDouble();
        } catch (Exception ex) {
            return def;
        }
    }

    static boolean bool(JsonObject o, String key, boolean def) {
        JsonElement e = o.get(key);
        try {
            return e == null || e.isJsonNull() ? def : e.getAsBoolean();
        } catch (Exception ex) {
            return def;
        }
    }

    /** Una lista de textos: acepta "uno" o ["uno", "dos"]. */
    static List<String> list(JsonObject o, String key) {
        List<String> out = new ArrayList<>();
        JsonElement e = o.get(key);
        if (e == null || e.isJsonNull()) {
            return out;
        }
        if (e.isJsonArray()) {
            for (JsonElement x : (JsonArray) e) {
                out.add(x.isJsonPrimitive() ? x.getAsString() : x.toString());
            }
        } else {
            out.add(e.getAsString());
        }
        return out;
    }
}
