package com.starlevi.starscenes.server;

import com.starlevi.starscenes.StarScenes;
import com.starlevi.starscenes.model.BBModel;
import com.starlevi.starscenes.net.Net;
import net.fabricmc.loader.api.FabricLoader;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
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

/**
 * Los modelos y skins del servidor: config/starscenes/modelos/*.bbmodel y config/starscenes/skins/*.png
 * (tambien en subcarpetas). Se guardan comprimidos y se mandan a los jugadores que no los tengan.
 */
public final class SceneLibrary {
    /** Lo que el servidor necesita saber de un modelo (el resto lo arma cada cliente). */
    public record Info(String id, float hitWidth, float hitHeight, float tagY, List<String> anims) {
    }

    private static final Map<String, Net.FileRef> MODELS = new LinkedHashMap<>();
    private static final Map<String, Net.FileRef> SKINS = new LinkedHashMap<>();
    private static final Map<String, Info> INFO = new HashMap<>();
    private static final Map<String, byte[]> BY_HASH = new HashMap<>();

    private SceneLibrary() {
    }

    public static Path root() {
        return FabricLoader.getInstance().getConfigDir().resolve("starscenes");
    }

    public static synchronized byte[] byHash(String hash) {
        return BY_HASH.get(hash);
    }

    public static synchronized Net.Index index() {
        return new Net.Index(new ArrayList<>(MODELS.values()), new ArrayList<>(SKINS.values()));
    }

    public static synchronized Info info(String id) {
        return INFO.get(id);
    }

    public static synchronized List<String> models() {
        return new ArrayList<>(MODELS.keySet());
    }

    public static synchronized List<String> skins() {
        return new ArrayList<>(SKINS.keySet());
    }

    public static String cleanId(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        int dot = n.lastIndexOf('.');
        if (dot > 0) {
            n = n.substring(0, dot);
        }
        return n.replaceAll("[^a-z0-9_\\-]", "_");
    }

    public static synchronized String scan() {
        MODELS.clear();
        SKINS.clear();
        INFO.clear();
        BY_HASH.clear();
        Path models = root().resolve("modelos"), skins = root().resolve("skins");
        int bad = 0;
        try {
            Files.createDirectories(models);
            Files.createDirectories(skins);
            for (Path p : files(models, ".bbmodel")) {
                String id = cleanId(p.getFileName().toString());
                try {
                    byte[] data = Files.readAllBytes(p);
                    BBModel m = BBModel.parse(new String(data, StandardCharsets.UTF_8));
                    INFO.put(id, new Info(id, m.hitWidth, m.hitHeight, (m.tagY >= 0 ? m.tagY : m.maxY + 4) / 16f, new ArrayList<>(m.anims.keySet())));
                    MODELS.put(id, store(id, data));
                    for (String w : m.warnings) {
                        StarScenes.LOGGER.warn("StarScenes [{}] {}", id, w);
                    }
                } catch (Exception e) {
                    bad++;
                    StarScenes.LOGGER.error("StarScenes: no se pudo leer {}: {}", p.getFileName(), e.toString());
                }
            }
            for (Path p : files(skins, ".png")) {
                String id = cleanId(p.getFileName().toString());
                SKINS.put(id, store(id, Files.readAllBytes(p)));
            }
        } catch (Exception e) {
            StarScenes.LOGGER.error("StarScenes: no se pudo leer {}", root(), e);
        }
        String msg = MODELS.size() + " modelos y " + SKINS.size() + " skins" + (bad > 0 ? " (" + bad + " con error, ver el log)" : "");
        StarScenes.LOGGER.info("StarScenes: {}", msg);
        return msg;
    }

    private static List<Path> files(Path dir, String ext) throws java.io.IOException {
        try (Stream<Path> s = Files.walk(dir)) {
            return s.filter(Files::isRegularFile).filter(f -> f.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(ext)).sorted().toList();
        }
    }

    private static Net.FileRef store(String id, byte[] data) throws java.io.IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(data.length / 3 + 64);
        try (GZIPOutputStream g = new GZIPOutputStream(out)) {
            g.write(data);
        }
        byte[] gz = out.toByteArray();
        String hash = sha1(gz);
        BY_HASH.put(hash, gz);
        return new Net.FileRef(id, hash, gz.length);
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
