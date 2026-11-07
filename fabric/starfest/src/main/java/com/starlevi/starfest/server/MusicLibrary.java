package com.starlevi.starfest.server;

import com.starlevi.starfest.StarFest;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * Canciones del servidor: los .ogg de config/starfest/musica. La carpeta se revisa cada dos segundos,
 * asi que basta con copiar o borrar un archivo: no hace falta reiniciar ni recargar nada.
 * Propiedad de Starlevi.
 */
public final class MusicLibrary {
    public static final long MAX_BYTES = 30L * 1024 * 1024;

    public record Track(String name, Path file, String hash, int bytes, long durationMs) {
    }

    private static final Map<String, Track> TRACKS = new TreeMap<>();
    private static final Map<String, Track> BY_HASH = new HashMap<>();
    private static final Map<Path, Long> STAMPS = new HashMap<>();

    private MusicLibrary() {
    }

    public static Path folder() {
        return FabricLoader.getInstance().getConfigDir().resolve("starfest").resolve("musica");
    }

    /** Nombre de cancion a partir del archivo: minusculas, sin extension, sin espacios. */
    public static String nameOf(Path file) {
        String n = file.getFileName().toString();
        n = n.substring(0, n.length() - 4).toLowerCase(Locale.ROOT).trim();
        return n.replaceAll("[^a-z0-9_.-]+", "_");
    }

    public static synchronized List<String> names() {
        return new ArrayList<>(TRACKS.keySet());
    }

    public static synchronized Track get(String name) {
        return name == null ? null : TRACKS.get(name.toLowerCase(Locale.ROOT));
    }

    public static synchronized Track byHash(String hash) {
        return BY_HASH.get(hash);
    }

    /** Revisa la carpeta: canciones nuevas, cambiadas o borradas. */
    public static synchronized void scan() {
        Path dir = folder();
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            return;
        }
        Map<Path, Long> seen = new HashMap<>();
        try (Stream<Path> files = Files.list(dir)) {
            for (Path f : (Iterable<Path>) files::iterator) {
                if (!Files.isRegularFile(f) || !f.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".ogg")) {
                    continue;
                }
                long stamp = Files.getLastModifiedTime(f).toMillis() ^ (Files.size(f) << 20);
                seen.put(f, stamp);
            }
        } catch (IOException e) {
            return;
        }
        boolean changed = !seen.keySet().equals(STAMPS.keySet());
        for (Map.Entry<Path, Long> e : seen.entrySet()) {
            if (!e.getValue().equals(STAMPS.get(e.getKey()))) {
                changed = true;
            }
        }
        if (!changed) {
            return;
        }
        Map<String, Track> old = new HashMap<>(TRACKS);
        TRACKS.clear();
        BY_HASH.clear();
        for (Map.Entry<Path, Long> e : seen.entrySet()) {
            Path f = e.getKey();
            String name = nameOf(f);
            Track prev = old.get(name);
            if (prev != null && prev.file().equals(f) && e.getValue().equals(STAMPS.get(f))) {
                TRACKS.put(name, prev);
                BY_HASH.put(prev.hash(), prev);
                continue;
            }
            try {
                long size = Files.size(f);
                if (size > MAX_BYTES || size < 64) {
                    StarFest.LOGGER.warn("StarFest: {} no se usa (pesa {} MB; maximo {} MB)", f.getFileName(), size >> 20, MAX_BYTES >> 20);
                    continue;
                }
                byte[] data = Files.readAllBytes(f);
                long ms = OggInfo.durationMs(data);
                if (ms <= 0) {
                    StarFest.LOGGER.warn("StarFest: {} no parece un .ogg valido (Vorbis)", f.getFileName());
                    continue;
                }
                Track t = new Track(name, f, sha1(data), data.length, ms);
                TRACKS.put(name, t);
                BY_HASH.put(t.hash(), t);
                if (prev == null) {
                    StarFest.LOGGER.info("StarFest: cancion nueva '{}' ({} s)", name, ms / 1000);
                }
            } catch (IOException ex) {
                StarFest.LOGGER.warn("StarFest: no se pudo leer {}: {}", f.getFileName(), ex.toString());
            }
        }
        STAMPS.clear();
        STAMPS.putAll(seen);
    }

    public static byte[] read(Track t) throws IOException {
        return Files.readAllBytes(t.file());
    }

    private static String sha1(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(data));
        } catch (Exception e) {
            return Integer.toHexString(java.util.Arrays.hashCode(data)) + "_" + data.length;
        }
    }
}
