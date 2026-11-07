package com.starlevi.starvillagers.client.library;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import com.starlevi.starvillagers.StarVillagers;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * La biblioteca publica de skins de Minecraft Comes Alive (mca.conczin.net): ropa y pelo que sube la
 * comunidad. Solo se mira y se baja (sin cuenta). Lo bajado queda en config/starvillagers/biblioteca.
 */
public final class Library {
    public static final String BASE = "https://mca.conczin.net";
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NORMAL).build();

    /** Una skin de la lista. */
    public record Entry(int id, String title, String author, int likes, Set<String> tags, int version) {
        public boolean hair() {
            return this.tags.contains("hair");
        }
    }

    private static final Map<String, NativeImage> IMAGES = new ConcurrentHashMap<>();
    private static final Map<String, Identifier> TEXTURES = new ConcurrentHashMap<>();
    private static final Set<String> LOADING = ConcurrentHashMap.newKeySet();
    private static final Set<String> FAILED = ConcurrentHashMap.newKeySet();
    private static int serial;

    private Library() {
    }

    private static Path dir() {
        return FabricLoader.getInstance().getConfigDir().resolve("starvillagers").resolve("biblioteca");
    }

    private static String get(String url) throws Exception {
        HttpRequest r = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20)).header("Accept", "application/json")
                .header("User-Agent", "StarVillagers").GET().build();
        HttpResponse<String> res = HTTP.send(r, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (res.statusCode() / 100 != 2) {
            throw new IllegalStateException("HTTP " + res.statusCode());
        }
        return res.body();
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    /** Una pagina de la lista. kind: "clothing" o "hair"; order: "likes" o "date". */
    public static CompletableFuture<List<Entry>> list(String kind, String search, String order, int page, int perPage) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String black = "invalid," + (kind.equals("hair") ? "clothing" : "hair");
                String url = BASE + "/v2/content/mca?whitelist=" + enc(search == null ? "" : search.trim()) + "&blacklist=" + enc(black) + "&order=" + order
                        + "&descending=true&offset=" + page * perPage + "&limit=" + perPage + "&moderator=false";
                JsonObject o = JsonParser.parseString(get(url)).getAsJsonObject();
                List<Entry> out = new ArrayList<>();
                JsonArray a = o.getAsJsonArray("contents");
                if (a != null) {
                    for (var x : a) {
                        JsonObject c = x.getAsJsonObject();
                        Set<String> tags = new HashSet<>();
                        if (c.has("tags") && c.get("tags").isJsonArray()) {
                            c.getAsJsonArray("tags").forEach(t -> tags.add(t.getAsString()));
                        }
                        out.add(new Entry(c.get("contentid").getAsInt(), c.has("title") ? c.get("title").getAsString() : "?",
                                c.has("username") ? c.get("username").getAsString() : "?", c.has("likes") ? c.get("likes").getAsInt() : 0, tags,
                                c.has("version") ? c.get("version").getAsInt() : 0));
                    }
                }
                return out;
            } catch (Exception e) {
                StarVillagers.LOGGER.warn("StarVillagers: no pude leer la biblioteca: {}", e.toString());
                return null;
            }
        });
    }

    private static String key(int id, int version) {
        return id + "_" + version;
    }

    /** La imagen (64x64) de una skin; null si todavia se esta bajando (o no se pudo). */
    public static NativeImage image(int id, int version) {
        String k = key(id, version);
        NativeImage img = IMAGES.get(k);
        if (img != null || FAILED.contains(k)) {
            return img;
        }
        Path f = dir().resolve(k + ".png");
        if (Files.exists(f)) {
            try {
                img = NativeImage.read(Files.readAllBytes(f));
                IMAGES.put(k, img);
                return img;
            } catch (Exception ignored) {
            }
        }
        if (LOADING.add(k)) {
            CompletableFuture.runAsync(() -> {
                try {
                    JsonObject o = JsonParser.parseString(get(BASE + "/v1/content/mca/" + id + "?version=" + version)).getAsJsonObject();
                    byte[] png = Base64.getDecoder().decode(o.getAsJsonObject("content").get("data").getAsString());
                    NativeImage.read(new ByteArrayInputStream(png)).close(); // que sea una imagen valida
                    Files.createDirectories(dir());
                    Files.write(f, png);
                } catch (Exception e) {
                    StarVillagers.LOGGER.warn("StarVillagers: no pude bajar la skin {}: {}", id, e.toString());
                    FAILED.add(k);
                } finally {
                    LOADING.remove(k);
                }
            });
        }
        return null;
    }

    public static boolean failed(int id, int version) {
        return FAILED.contains(key(id, version));
    }

    /** La imagen como textura (para las vistas previas). */
    public static Identifier texture(int id, int version) {
        String k = key(id, version);
        Identifier t = TEXTURES.get(k);
        if (t != null) {
            return t;
        }
        NativeImage img = image(id, version);
        if (img == null) {
            return null;
        }
        NativeImage copy = new NativeImage(img.getWidth(), img.getHeight(), true);
        copy.copyFrom(img);
        Identifier tid = StarVillagers.id("biblioteca/" + (serial++));
        Minecraft.getInstance().getTextureManager().register(tid, new DynamicTexture(tid::toString, copy));
        TEXTURES.put(k, tid);
        return tid;
    }

    /** "biblioteca:123:4" -> {123, 4}, o null. */
    public static int[] parse(String layer) {
        if (layer == null || !layer.startsWith("biblioteca:")) {
            return null;
        }
        String[] p = layer.split(":");
        try {
            return new int[]{Integer.parseInt(p[1]), p.length > 2 ? Integer.parseInt(p[2]) : 0};
        } catch (Exception e) {
            return null;
        }
    }
}
