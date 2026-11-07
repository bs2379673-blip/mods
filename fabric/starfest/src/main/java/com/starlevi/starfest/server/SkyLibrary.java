package com.starlevi.starfest.server;

import com.starlevi.starfest.StarFest;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * Cielos del servidor: config/starfest/cielos/<nombre>.json y sus imagenes (PNG o GIF) en
 * config/starfest/cielos/imagenes. Igual que la musica: se revisa la carpeta sola, sin reiniciar.
 * La primera vez se escriben unos cielos de ejemplo.
 */
public final class SkyLibrary {
    public static final long MAX_IMAGE = 8L * 1024 * 1024;

    /** Un cielo: su texto JSON, una firma para saber si cambio, y las imagenes que usa (nombre -> hash). */
    public record Sky(String name, String json, String version, Map<String, String> images) {
    }

    public record Image(String name, String hash, byte[] data) {
    }

    private static final Map<String, Sky> SKIES = new TreeMap<>();
    private static final Map<String, Image> IMAGES = new HashMap<>();
    private static final Map<String, Image> BY_HASH = new HashMap<>();
    private static final Map<Path, Long> STAMPS = new HashMap<>();
    private static boolean examples;

    private SkyLibrary() {
    }

    public static Path folder() {
        return FabricLoader.getInstance().getConfigDir().resolve("starfest").resolve("cielos");
    }

    public static synchronized List<String> names() {
        return new ArrayList<>(SKIES.keySet());
    }

    public static synchronized Sky get(String name) {
        return name == null ? null : SKIES.get(name.toLowerCase(Locale.ROOT));
    }

    public static synchronized byte[] imageByHash(String hash) {
        Image i = BY_HASH.get(hash);
        return i == null ? null : i.data();
    }

    /** Revisa la carpeta; devuelve true si algo cambio. */
    public static synchronized boolean scan() {
        Path dir = folder(), imgDir = dir.resolve("imagenes");
        try {
            Files.createDirectories(imgDir);
            if (!examples) {
                examples = true;
                writeExamples(dir, imgDir);
            }
        } catch (IOException e) {
            return false;
        }
        Map<Path, Long> seen = new HashMap<>();
        collect(dir, seen, ".json");
        collect(imgDir, seen, ".png", ".gif");
        if (seen.equals(STAMPS)) {
            return false;
        }
        STAMPS.clear();
        STAMPS.putAll(seen);
        IMAGES.clear();
        BY_HASH.clear();
        SKIES.clear();
        for (Path f : seen.keySet()) {
            String file = f.getFileName().toString();
            String lower = file.toLowerCase(Locale.ROOT);
            if (!lower.endsWith(".png") && !lower.endsWith(".gif")) {
                continue;
            }
            try {
                if (Files.size(f) > MAX_IMAGE) {
                    StarFest.LOGGER.warn("StarFest: la imagen {} pesa mas de 8 MB y no se usa", file);
                    continue;
                }
                byte[] data = Files.readAllBytes(f);
                Image img = new Image(lower, sha1(data), data);
                IMAGES.put(lower, img);
                BY_HASH.put(img.hash(), img);
            } catch (IOException ignored) {
                // se reintenta en la proxima revision
            }
        }
        for (Path f : seen.keySet()) {
            String file = f.getFileName().toString();
            if (!file.toLowerCase(Locale.ROOT).endsWith(".json")) {
                continue;
            }
            String name = file.substring(0, file.length() - 5).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]+", "_");
            try {
                String json = Files.readString(f, StandardCharsets.UTF_8);
                JsonElement root = JsonParser.parseString(json);
                if (!root.isJsonObject()) {
                    throw new IllegalStateException("no es un objeto JSON");
                }
                Map<String, String> used = new LinkedHashMap<>();
                findImages(root, used);
                StringBuilder v = new StringBuilder(sha1(json.getBytes(StandardCharsets.UTF_8)));
                used.values().forEach(v::append);
                SKIES.put(name, new Sky(name, json, sha1(v.toString().getBytes(StandardCharsets.UTF_8)), used));
            } catch (Exception ex) {
                StarFest.LOGGER.warn("StarFest: el cielo {} tiene un error: {}", file, ex.getMessage());
            }
        }
        StarFest.LOGGER.info("StarFest: {} cielos, {} imagenes", SKIES.size(), IMAGES.size());
        return true;
    }

    /** Cada "archivo": "x.png" del cielo, con su hash si la imagen existe. */
    private static void findImages(JsonElement e, Map<String, String> out) {
        if (e.isJsonObject()) {
            for (Map.Entry<String, JsonElement> en : e.getAsJsonObject().entrySet()) {
                if (en.getKey().equals("archivo") && en.getValue().isJsonPrimitive()) {
                    String f = en.getValue().getAsString().toLowerCase(Locale.ROOT);
                    Image img = IMAGES.get(f);
                    if (img != null) {
                        out.put(f, img.hash());
                    } else {
                        StarFest.LOGGER.warn("StarFest: falta la imagen {} en {}", f, folder().resolve("imagenes"));
                    }
                } else {
                    findImages(en.getValue(), out);
                }
            }
        } else if (e.isJsonArray()) {
            for (JsonElement x : e.getAsJsonArray()) {
                findImages(x, out);
            }
        }
    }

    private static void collect(Path dir, Map<Path, Long> out, String... ext) {
        try (Stream<Path> files = Files.list(dir)) {
            for (Path f : (Iterable<Path>) files::iterator) {
                String n = f.getFileName().toString().toLowerCase(Locale.ROOT);
                for (String e : ext) {
                    if (Files.isRegularFile(f) && n.endsWith(e)) {
                        out.put(f, Files.getLastModifiedTime(f).toMillis() ^ Files.size(f) << 20);
                    }
                }
            }
        } catch (IOException ignored) {
            // carpeta vacia o inaccesible
        }
    }

    /** Cielos de ejemplo (solo si no existen): se pueden editar o copiar para hacer otros. */
    private static void writeExamples(Path dir, Path imgDir) {
        for (String n : new String[]{"noche_neon", "aurora", "galaxia", "fiesta"}) {
            Path f = dir.resolve(n + ".json");
            if (Files.exists(f)) {
                continue;
            }
            try (InputStream in = SkyLibrary.class.getResourceAsStream("/assets/starfest/cielos/" + n + ".json")) {
                if (in != null) {
                    Files.write(f, in.readAllBytes());
                }
            } catch (IOException ignored) {
                // sin ejemplos
            }
        }
        for (String n : new String[]{"corazon.gif"}) {
            Path f = imgDir.resolve(n);
            if (Files.exists(f)) {
                continue;
            }
            try (InputStream in = SkyLibrary.class.getResourceAsStream("/assets/starfest/cielos/imagenes/" + n)) {
                if (in != null) {
                    Files.write(f, in.readAllBytes());
                }
            } catch (IOException ignored) {
                // sin ejemplos
            }
        }
        Path help = dir.resolve("LEEME.txt");
        if (!Files.exists(help)) {
            try (InputStream in = SkyLibrary.class.getResourceAsStream("/assets/starfest/cielos/LEEME.txt")) {
                if (in != null) {
                    Files.write(help, in.readAllBytes());
                }
            } catch (IOException ignored) {
                // sin ayuda
            }
        }
    }

    static String sha1(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(data));
        } catch (Exception e) {
            return Integer.toHexString(java.util.Arrays.hashCode(data));
        }
    }

    static JsonObject parse(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }
}
