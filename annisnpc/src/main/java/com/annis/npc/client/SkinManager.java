package com.annis.npc.client;

import com.annis.npc.AnnisNpc;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Carga skins (PNG de 64x64 o 64x32):
 *  - por nombre, desde config/annisnpc/skins/<nombre>.png
 *  - por URL (http/https), descargada una vez y guardada en config/annisnpc/skins/cache/
 */
public final class SkinManager {
    private static final Map<String, Identifier> CACHE = new HashMap<>();
    private static final Set<String> MISSING = new HashSet<>();
    private static final Set<String> LOADING = ConcurrentHashMap.newKeySet();
    private static final int MAX_BYTES = 2 * 1024 * 1024;

    private SkinManager() {
    }

    public static Path dir() {
        return FabricLoader.getInstance().getConfigDir().resolve("annisnpc").resolve("skins");
    }

    private static Path cacheDir() {
        return dir().resolve("cache");
    }

    public static boolean isUrl(String s) {
        if (s == null) {
            return false;
        }
        String l = s.toLowerCase(Locale.ROOT);
        return l.startsWith("http://") || l.startsWith("https://");
    }

    /** Devuelve la textura de la skin, o null si no existe / aun se descarga (se usa Steve/Alex). */
    public static Identifier get(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        Identifier cached = CACHE.get(name);
        if (cached != null) {
            return cached;
        }
        if (MISSING.contains(name)) {
            return null;
        }
        if (isUrl(name)) {
            return getUrl(name);
        }
        // nombres como "punchy:clip" no son archivos (en Windows ':' rompe la ruta)
        if (!EmoteLibrary.safeFileName(name)) {
            MISSING.add(name);
            return null;
        }
        Path file = dir().resolve(name + ".png");
        if (!Files.isRegularFile(file)) {
            MISSING.add(name);
            return null;
        }
        try (InputStream in = Files.newInputStream(file)) {
            NativeImage image = fixLegacy(NativeImage.read(in));
            return register(name, image);
        } catch (Exception e) {
            AnnisNpc.LOGGER.warn("No se pudo cargar la skin {}", file, e);
            MISSING.add(name);
            return null;
        }
    }

    private static Identifier register(String name, NativeImage image) {
        Identifier id = Identifier.of(AnnisNpc.MOD_ID, "skin/" + sanitize(name));
        MinecraftClient.getInstance().getTextureManager().registerTexture(id, new NativeImageBackedTexture(image));
        CACHE.put(name, id);
        return id;
    }

    // ---- URL ----

    private static Identifier getUrl(String url) {
        Path file = cacheDir().resolve(hash(url) + ".png");
        if (Files.isRegularFile(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                return register(url, fixLegacy(NativeImage.read(in)));
            } catch (Exception e) {
                AnnisNpc.LOGGER.warn("Skin en cache danada {}", file, e);
                try {
                    Files.deleteIfExists(file);
                } catch (Exception ignored) {
                }
            }
        }
        if (LOADING.add(url)) {
            Thread thread = new Thread(() -> download(url, file), "annisnpc-skin-download");
            thread.setDaemon(true);
            thread.start();
        }
        return null;
    }

    private static void download(String url, Path file) {
        try {
            byte[] data = fetch(url);
            NativeImage image;
            try (InputStream in = new ByteArrayInputStream(data)) {
                image = fixLegacy(NativeImage.read(in));
            }
            Files.createDirectories(file.getParent());
            Files.write(file, data);
            final NativeImage finalImage = image;
            MinecraftClient.getInstance().execute(() -> {
                register(url, finalImage);
                LOADING.remove(url);
            });
        } catch (Exception e) {
            AnnisNpc.LOGGER.warn("No se pudo descargar la skin {}: {}", url, e.toString());
            MinecraftClient.getInstance().execute(() -> {
                MISSING.add(url);
                LOADING.remove(url);
            });
        }
    }

    private static byte[] fetch(String url) throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        String current = url;
        for (int hop = 0; hop < 4; hop++) {
            URI uri = URI.create(current);
            if (!isUrl(current) || uri.getHost() == null) {
                throw new IllegalArgumentException("URL no valida");
            }
            for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
                if (address.isLoopbackAddress() || address.isAnyLocalAddress()
                        || address.isSiteLocalAddress() || address.isLinkLocalAddress()) {
                    throw new IllegalArgumentException("Direccion local no permitida");
                }
            }
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(15))
                    .header("User-Agent", "AnnisNpc/0.3")
                    .GET().build();
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            int code = response.statusCode();
            if (code >= 300 && code < 400) {
                String location = response.headers().firstValue("Location")
                        .orElseThrow(() -> new IllegalStateException("Redireccion sin destino"));
                response.body().close();
                current = uri.resolve(location).toString();
                continue;
            }
            if (code != 200) {
                response.body().close();
                throw new IllegalStateException("HTTP " + code);
            }
            try (InputStream in = response.body()) {
                byte[] data = in.readNBytes(MAX_BYTES + 1);
                if (data.length > MAX_BYTES) {
                    throw new IllegalStateException("Archivo demasiado grande");
                }
                return data;
            }
        }
        throw new IllegalStateException("Demasiadas redirecciones");
    }

    /** Convierte skins antiguas de 64x32 a 64x64 como hace el juego. */
    private static NativeImage fixLegacy(NativeImage image) {
        if (image.getWidth() != 64 || (image.getHeight() != 64 && image.getHeight() != 32)) {
            int w = image.getWidth();
            int h = image.getHeight();
            image.close();
            throw new IllegalArgumentException("Tamano de skin no valido: " + w + "x" + h);
        }
        if (image.getHeight() == 64) {
            return image;
        }
        NativeImage out = new NativeImage(64, 64, true);
        out.copyFrom(image);
        out.fillRect(0, 32, 64, 32, 0);
        out.copyRect(4, 16, 16, 32, 4, 4, true, false);
        out.copyRect(8, 16, 16, 32, 4, 4, true, false);
        out.copyRect(0, 20, 24, 32, 4, 12, true, false);
        out.copyRect(4, 20, 16, 32, 4, 12, true, false);
        out.copyRect(8, 20, 8, 32, 4, 12, true, false);
        out.copyRect(12, 20, 16, 32, 4, 12, true, false);
        out.copyRect(44, 16, -8, 32, 4, 4, true, false);
        out.copyRect(48, 16, -8, 32, 4, 4, true, false);
        out.copyRect(40, 20, 0, 32, 4, 12, true, false);
        out.copyRect(44, 20, -8, 32, 4, 12, true, false);
        out.copyRect(48, 20, -16, 32, 4, 12, true, false);
        out.copyRect(52, 20, -8, 32, 4, 12, true, false);
        image.close();
        return out;
    }

    private static String hash(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-1").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(s.hashCode());
        }
    }

    public static void clear() {
        for (Identifier id : CACHE.values()) {
            MinecraftClient.getInstance().getTextureManager().destroyTexture(id);
        }
        CACHE.clear();
        MISSING.clear();
    }

    public static List<String> list() {
        List<String> names = new ArrayList<>();
        Path dir = dir();
        if (Files.isDirectory(dir)) {
            try (Stream<Path> stream = Files.list(dir)) {
                stream.filter(Files::isRegularFile)
                        .map(p -> p.getFileName().toString())
                        .filter(n -> n.toLowerCase(Locale.ROOT).endsWith(".png"))
                        .map(n -> n.substring(0, n.length() - 4))
                        .sorted()
                        .forEach(names::add);
            } catch (Exception e) {
                AnnisNpc.LOGGER.warn("No se pudo listar las skins", e);
            }
        }
        return names;
    }

    private static String sanitize(String name) {
        if (isUrl(name)) {
            return "url/" + hash(name);
        }
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
    }
}
