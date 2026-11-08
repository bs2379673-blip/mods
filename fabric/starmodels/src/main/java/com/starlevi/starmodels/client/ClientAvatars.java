package com.starlevi.starmodels.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import com.starlevi.starmodels.StarModels;
import com.starlevi.starmodels.model.AvatarBuilder;
import com.starlevi.starmodels.net.Net;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.zip.GZIPInputStream;

/**
 * Lo que el cliente sabe de los personajes: la lista del servidor, el atuendo de cada jugador y los
 * modelos ya armados (cada uno en calidad completa y en una version liviana para verlo de lejos). Los
 * archivos se bajan una sola vez (quedan en starmodels_cache) y solo los de los personajes que se usan.
 */
public final class ClientAvatars {
    /** Un personaje listo para dibujar. */
    public static final class Ready {
        public final AvatarBuilder.Result model;
        /** El mismo personaje con menos triangulos (o null), con las mismas texturas. */
        public final AvatarBuilder.Result lod;
        public final Identifier[] textures;

        Ready(AvatarBuilder.Result model, AvatarBuilder.Result lod, Identifier[] textures) {
            this.model = model;
            this.lod = lod;
            this.textures = textures;
        }

        public AvatarBuilder.Result pick(boolean far) {
            return far && this.lod != null ? this.lod : this.model;
        }
    }

    /** Un accesorio de otro personaje puesto encima, con su ajuste (pixeles y tamano). */
    public record Added(String from, String acc, float x, float y, float z, float s) {
    }

    /** Un atuendo: personaje base, accesorios propios que se saco y accesorios agregados. */
    public record Look(String base, Set<String> without, List<Added> with) {
        public static Look parse(String json) {
            if (json == null || json.isBlank()) {
                return null;
            }
            try {
                JsonObject o = JsonParser.parseString(json).getAsJsonObject();
                String base = o.has("base") ? o.get("base").getAsString() : "";
                if (base.isEmpty()) {
                    return null;
                }
                Set<String> without = new HashSet<>();
                if (o.has("sin")) {
                    for (JsonElement e : o.getAsJsonArray("sin")) {
                        without.add(e.getAsString());
                    }
                }
                List<Added> with = new ArrayList<>();
                if (o.has("con")) {
                    for (JsonElement e : o.getAsJsonArray("con")) {
                        JsonObject a = e.getAsJsonObject();
                        with.add(new Added(a.get("de").getAsString(), a.get("acc").getAsString(), f(a, "x", 0), f(a, "y", 0), f(a, "z", 0),
                                f(a, "s", 1)));
                    }
                }
                return new Look(base, without, with);
            } catch (Exception e) {
                return null;
            }
        }

        private static float f(JsonObject o, String k, float def) {
            return o.has(k) ? o.get(k).getAsFloat() : def;
        }

        public String toJson() {
            JsonObject o = new JsonObject();
            o.addProperty("base", this.base);
            if (!this.without.isEmpty()) {
                JsonArray a = new JsonArray();
                this.without.forEach(a::add);
                o.add("sin", a);
            }
            if (!this.with.isEmpty()) {
                JsonArray a = new JsonArray();
                for (Added ad : this.with) {
                    JsonObject x = new JsonObject();
                    x.addProperty("de", ad.from());
                    x.addProperty("acc", ad.acc());
                    x.addProperty("x", ad.x());
                    x.addProperty("y", ad.y());
                    x.addProperty("z", ad.z());
                    x.addProperty("s", ad.s());
                    a.add(x);
                }
                o.add("con", a);
            }
            return o.toString();
        }
    }

    private static final Map<String, Net.Avatar> INDEX = new LinkedHashMap<>();
    private static final Map<UUID, Look> OUTFITS = new HashMap<>();
    private static final Map<UUID, String> RAW = new HashMap<>();
    private static final Map<String, Ready> READY = new HashMap<>();
    private static final Set<String> LOADING = new HashSet<>();
    private static final Map<String, Pending> DOWNLOADS = new HashMap<>();
    /** Lo que se esta probando en el workshop (solo lo ve uno mismo hasta guardar). */
    private static Look preview;
    private static boolean previewing;
    private static int textureSeq;

    private static final class Pending {
        final byte[] buf;
        final List<Consumer<byte[]>> waiters = new ArrayList<>();

        Pending(int size) {
            this.buf = new byte[size];
        }
    }

    private ClientAvatars() {
    }

    public static List<Net.Avatar> index() {
        return new ArrayList<>(INDEX.values());
    }

    public static Net.Avatar info(String id) {
        return INDEX.get(id);
    }

    public static void receive(Net.Index i) {
        INDEX.clear();
        for (Net.Avatar a : i.avatars()) {
            INDEX.put(a.id(), a);
        }
        READY.keySet().removeIf(id -> !INDEX.containsKey(id));
        LOADING.clear();
    }

    public static void outfit(Net.Outfit o) {
        Look l = Look.parse(o.json());
        if (l == null) {
            OUTFITS.remove(o.player());
            RAW.remove(o.player());
        } else {
            OUTFITS.put(o.player(), l);
            RAW.put(o.player(), o.json());
        }
    }

    public static String rawOutfit(UUID player) {
        return RAW.getOrDefault(player, "");
    }

    public static void preview(Look l) {
        preview = l;
        previewing = true;
    }

    public static void endPreview() {
        preview = null;
        previewing = false;
    }

    public static Look look(UUID player) {
        Minecraft mc = Minecraft.getInstance();
        if (previewing && mc.player != null && mc.player.getUUID().equals(player)) {
            return preview;
        }
        return OUTFITS.get(player);
    }

    /** El personaje armado (o null, y si falta lo empieza a bajar). */
    public static Ready get(String id) {
        Ready r = READY.get(id);
        if (r == null) {
            load(id);
        }
        return r;
    }

    private static void load(String id) {
        Net.Avatar a = INDEX.get(id);
        if (a == null || LOADING.contains(id)) {
            return;
        }
        LOADING.add(id);
        Map<String, byte[]> files = new LinkedHashMap<>();
        int[] left = {a.files().size()};
        for (Net.FileRef f : a.files()) {
            request(f.hash(), f.bytes(), gz -> {
                files.put(f.file(), gz);
                if (--left[0] == 0) {
                    build(a, files);
                }
            });
        }
    }

    /** Triangulos de la version de lejos. */
    private static final int LOD_TRIANGLES = 3500;

    private static String lodJson(String json) {
        JsonObject o;
        try {
            o = JsonParser.parseString(json == null || json.isBlank() ? "{}" : json).getAsJsonObject();
        } catch (Exception e) {
            o = new JsonObject();
        }
        o.addProperty("triangulos_max", LOD_TRIANGLES);
        return o.toString();
    }

    /** Se arma en otro hilo; las texturas se suben despues en el hilo del juego. */
    private static void build(Net.Avatar a, Map<String, byte[]> gzFiles) {
        CompletableFuture.supplyAsync(() -> {
            Map<String, byte[]> files = new LinkedHashMap<>();
            for (Map.Entry<String, byte[]> e : gzFiles.entrySet()) {
                try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(e.getValue()))) {
                    files.put(e.getKey(), in.readAllBytes());
                } catch (Exception ex) {
                    throw new RuntimeException(e.getKey() + ": " + ex);
                }
            }
            AvatarBuilder.Result full = AvatarBuilder.build(files, a.json());
            AvatarBuilder.Result lod = null;
            if (full.triangles > LOD_TRIANGLES * 1.5f) {
                try {
                    lod = AvatarBuilder.build(files, lodJson(a.json()));
                    if (lod.materials.size() != full.materials.size()) {
                        lod = null;
                    } else {
                        for (int i = 0; i < lod.materials.size(); i++) {
                            if (!lod.materials.get(i).name.equals(full.materials.get(i).name)) {
                                lod = null;
                                break;
                            }
                        }
                    }
                    if (lod != null) {
                        for (AvatarBuilder.Material m : lod.materials) {
                            m.argb = null;
                        }
                    }
                } catch (Exception ex) {
                    lod = null;
                }
            }
            return new AvatarBuilder.Result[]{full, lod};
        }).whenComplete((res, err) -> Minecraft.getInstance().execute(() -> {
            if (err != null) {
                StarModels.LOGGER.error("StarModels: no se pudo armar {}: {}", a.id(), err.toString());
                return;
            }
            AvatarBuilder.Result full = res[0];
            for (String l : full.log) {
                StarModels.LOGGER.info("StarModels [{}] {}", a.id(), l);
            }
            if (res[1] != null) {
                StarModels.LOGGER.info("StarModels [{}] de lejos: {} triangulos", a.id(), res[1].triangles);
            }
            Identifier[] tex = new Identifier[full.materials.size()];
            for (int i = 0; i < tex.length; i++) {
                AvatarBuilder.Material m = full.materials.get(i);
                NativeImage img = new NativeImage(m.width, m.height, false);
                for (int y = 0; y < m.height; y++) {
                    for (int x = 0; x < m.width; x++) {
                        img.setPixel(x, y, m.argb[y * m.width + x]);
                    }
                }
                m.argb = null;
                Identifier loc = Identifier.fromNamespaceAndPath(StarModels.MOD_ID, "avatar/" + a.id().toLowerCase().replaceAll("[^a-z0-9_.-]", "_")
                        + "_" + (textureSeq++));
                Minecraft.getInstance().getTextureManager().register(loc, new AvatarTexture(loc.toString(), img));
                tex[i] = loc;
            }
            READY.put(a.id(), new Ready(full, res[1], tex));
        }));
    }

    // ------------------------------------------------------------------ descargas

    private static Path cacheDir() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("starmodels_cache");
    }

    private static void request(String hash, int size, Consumer<byte[]> done) {
        for (Path dir : new Path[]{cacheDir(), Minecraft.getInstance().gameDirectory.toPath().resolve("staravatars_cache")}) {
            Path f = dir.resolve(hash);
            try {
                if (Files.isRegularFile(f) && Files.size(f) == size) {
                    done.accept(Files.readAllBytes(f));
                    return;
                }
            } catch (Exception ignored) {
                // se pide de nuevo
            }
        }
        Pending p = DOWNLOADS.get(hash);
        if (p == null) {
            p = new Pending(size);
            DOWNLOADS.put(hash, p);
            ClientPlayNetworking.send(new Net.Need(hash));
        }
        p.waiters.add(done);
    }

    public static void chunk(Net.Chunk c) {
        Pending p = DOWNLOADS.get(c.hash());
        if (p == null || p.buf.length != c.total()) {
            return;
        }
        System.arraycopy(c.data(), 0, p.buf, c.offset(), Math.min(c.data().length, p.buf.length - c.offset()));
        if (c.offset() + c.data().length < c.total()) {
            return;
        }
        DOWNLOADS.remove(c.hash());
        try {
            Files.createDirectories(cacheDir());
            Files.write(cacheDir().resolve(c.hash()), p.buf);
        } catch (Exception ignored) {
            // queda en memoria
        }
        for (Consumer<byte[]> w : p.waiters) {
            w.accept(p.buf);
        }
    }

    public static void disconnect() {
        OUTFITS.clear();
        RAW.clear();
        DOWNLOADS.clear();
        LOADING.clear();
        endPreview();
    }
}
