package com.starlevi.starmodels.server;

import com.starlevi.starmodels.StarModels;
import com.starlevi.starmodels.net.Net;
import net.fabricmc.loader.api.FabricLoader;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Los personajes del servidor: cada carpeta (o .zip) dentro de config/starmodels/personajes es un
 * export de Roblox Studio (.obj + .mtl + imagenes, y opcional avatar.json). Los archivos se guardan
 * comprimidos y se mandan a los jugadores que no los tengan.
 */
public final class AvatarLibrary {
    private static final Map<String, Net.Avatar> AVATARS = new LinkedHashMap<>();
    private static final Map<String, byte[]> BY_HASH = new HashMap<>();

    private AvatarLibrary() {
    }

    public static Path root() {
        return FabricLoader.getInstance().getConfigDir().resolve("starmodels").resolve("personajes");
    }

    public static synchronized byte[] byHash(String hash) {
        return BY_HASH.get(hash);
    }

    public static synchronized List<Net.Avatar> all() {
        return new ArrayList<>(AVATARS.values());
    }

    public static synchronized boolean exists(String id) {
        return AVATARS.containsKey(id);
    }

    public static synchronized Net.Avatar get(String id) {
        return AVATARS.get(id);
    }

    private static boolean wanted(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        return n.endsWith(".obj") || n.endsWith(".mtl") || n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".json")
                || n.endsWith(".gltf") || n.endsWith(".glb");
    }

    /** La carpeta de StarAvatars (NeoForge), por si los personajes ya estaban ahi. */
    public static Path legacy() {
        return FabricLoader.getInstance().getConfigDir().resolve("staravatars").resolve("personajes");
    }

    public static synchronized int scan() {
        AVATARS.clear();
        BY_HASH.clear();
        try {
            Files.createDirectories(root());
        } catch (Exception ignored) {
            // se avisa al leer
        }
        scanDir(legacy());
        scanDir(root());
        StarModels.LOGGER.info("StarModels: {} personajes cargados", AVATARS.size());
        return AVATARS.size();
    }

    private static void scanDir(Path root) {
        if (!Files.isDirectory(root)) {
            return;
        }
        try {
            try (Stream<Path> s = Files.list(root)) {
                for (Path p : s.sorted().toList()) {
                    String name = p.getFileName().toString();
                    Map<String, byte[]> files = new LinkedHashMap<>();
                    String id;
                    if (Files.isDirectory(p)) {
                        id = name;
                        try (Stream<Path> fs = Files.walk(p)) {
                            for (Path f : fs.filter(Files::isRegularFile).toList()) {
                                String fn = f.getFileName().toString();
                                if (wanted(fn)) {
                                    files.put(fn, Files.readAllBytes(f));
                                }
                            }
                        }
                    } else if (name.toLowerCase(Locale.ROOT).endsWith(".zip")) {
                        id = name.substring(0, name.length() - 4);
                        try (ZipInputStream z = new ZipInputStream(Files.newInputStream(p))) {
                            ZipEntry e;
                            while ((e = z.getNextEntry()) != null) {
                                String fn = e.getName().replace('\\', '/');
                                fn = fn.substring(fn.lastIndexOf('/') + 1);
                                if (!e.isDirectory() && wanted(fn)) {
                                    files.put(fn, z.readAllBytes());
                                }
                            }
                        }
                    } else {
                        continue;
                    }
                    id = id.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_\\-]", "_");
                    if (files.keySet().stream().noneMatch(f -> f.toLowerCase(Locale.ROOT).endsWith(".obj"))) {
                        StarModels.LOGGER.warn("StarModels: {} no tiene .obj, se saltea", name);
                        continue;
                    }
                    String json = "{}";
                    List<Net.FileRef> refs = new ArrayList<>();
                    for (Map.Entry<String, byte[]> f : files.entrySet()) {
                        if (f.getKey().equalsIgnoreCase("avatar.json")) {
                            json = new String(f.getValue(), java.nio.charset.StandardCharsets.UTF_8);
                            continue;
                        }
                        byte[] gz = gzip(f.getValue());
                        String hash = sha1(gz);
                        BY_HASH.put(hash, gz);
                        refs.add(new Net.FileRef(f.getKey(), hash, gz.length));
                    }
                    String display = name;
                    try {
                        com.google.gson.JsonObject o = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
                        if (o.has("nombre")) {
                            display = o.get("nombre").getAsString();
                        }
                    } catch (Exception e) {
                        StarModels.LOGGER.warn("StarModels: avatar.json de {} invalido: {}", name, e.toString());
                        json = "{}";
                    }
                    AVATARS.put(id, new Net.Avatar(id, display, json, refs));
                }
            }
        } catch (Exception e) {
            StarModels.LOGGER.error("StarModels: no se pudo leer {}", root, e);
        }
    }

    private static byte[] gzip(byte[] data) throws java.io.IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(data.length / 3 + 64);
        try (GZIPOutputStream g = new GZIPOutputStream(out)) {
            g.write(data);
        }
        return out.toByteArray();
    }

    static String sha1(byte[] data) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-1").digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : d) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(java.util.Arrays.hashCode(data));
        }
    }
}
