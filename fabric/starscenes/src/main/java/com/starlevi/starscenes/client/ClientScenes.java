package com.starlevi.starscenes.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.starlevi.starscenes.StarScenes;
import com.starlevi.starscenes.model.BBModel;
import com.starlevi.starscenes.net.Net;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.zip.GZIPInputStream;

/**
 * Lo que el cliente sabe de los modelos y skins del servidor. Se bajan una sola vez (quedan en
 * starscenes_cache) y solo los que usa algun NPC que se ve.
 */
public final class ClientScenes {
    /** Un modelo listo para dibujar: el modelo y la textura de cada una de sus imagenes. */
    public static final class Ready {
        public final BBModel model;
        public final Identifier[] textures;
        /** Que texturas son la skin (las cambia la skin del NPC). */
        public final boolean[] isSkin;

        Ready(BBModel model, Identifier[] textures, boolean[] isSkin) {
            this.model = model;
            this.textures = textures;
            this.isSkin = isSkin;
        }
    }

    private static final Map<String, Net.FileRef> MODELS = new LinkedHashMap<>();
    private static final Map<String, Net.FileRef> SKINS = new LinkedHashMap<>();
    private static final Map<String, Ready> READY = new HashMap<>();
    private static final Map<String, Identifier> SKIN_TEX = new HashMap<>();
    private static final Set<String> LOADING = new HashSet<>();
    private static final Map<String, Pending> DOWNLOADS = new HashMap<>();
    private static int seq;

    private static final class Pending {
        final byte[] buf;
        final List<Consumer<byte[]>> waiters = new ArrayList<>();

        Pending(int size) {
            this.buf = new byte[size];
        }
    }

    private ClientScenes() {
    }

    public static List<String> modelIds() {
        return new ArrayList<>(MODELS.keySet());
    }

    public static List<String> skinIds() {
        return new ArrayList<>(SKINS.keySet());
    }

    public static void receive(Net.Index i) {
        Map<String, Net.FileRef> oldM = new HashMap<>(MODELS), oldS = new HashMap<>(SKINS);
        MODELS.clear();
        SKINS.clear();
        for (Net.FileRef f : i.models()) {
            MODELS.put(f.id(), f);
        }
        for (Net.FileRef f : i.skins()) {
            SKINS.put(f.id(), f);
        }
        // lo que cambio se vuelve a armar
        READY.keySet().removeIf(id -> !MODELS.containsKey(id) || !MODELS.get(id).equals(oldM.get(id)));
        SKIN_TEX.keySet().removeIf(id -> !SKINS.containsKey(id) || !SKINS.get(id).equals(oldS.get(id)));
        LOADING.clear();
    }

    /** El modelo armado, o null (y si falta, lo empieza a bajar). */
    public static Ready model(String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        Ready r = READY.get(id);
        if (r == null) {
            loadModel(id);
        }
        return r;
    }

    /** La textura de una skin, o null (y si falta, la empieza a bajar). */
    public static Identifier skin(String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        Identifier t = SKIN_TEX.get(id);
        if (t == null) {
            loadSkin(id);
        }
        return t;
    }

    private static void loadModel(String id) {
        Net.FileRef f = MODELS.get(id);
        if (f == null || !LOADING.add("m:" + id)) {
            return;
        }
        request(f.hash(), f.bytes(), gz -> CompletableFuture.supplyAsync(() -> BBModel.parse(new String(gunzip(gz), StandardCharsets.UTF_8)))
                .whenComplete((m, err) -> Minecraft.getInstance().execute(() -> {
                    if (err != null) {
                        StarScenes.LOGGER.error("StarScenes: no se pudo armar el modelo {}: {}", id, err.toString());
                        return;
                    }
                    for (String w : m.warnings) {
                        StarScenes.LOGGER.warn("StarScenes [{}] {}", id, w);
                    }
                    Identifier[] tex = new Identifier[m.textures.size()];
                    boolean[] skin = new boolean[tex.length];
                    boolean anySkin = false;
                    for (int t = 0; t < tex.length; t++) {
                        BBModel.Texture bt = m.textures.get(t);
                        skin[t] = bt.name.toLowerCase(Locale.ROOT).contains("skin");
                        anySkin |= skin[t];
                        if (bt.png != null) {
                            tex[t] = register("modelo/" + id + "_" + t, bt.png);
                        }
                    }
                    if (!anySkin && tex.length > 0) {
                        skin[0] = true;
                    }
                    READY.put(id, new Ready(m, tex, skin));
                })));
    }

    private static void loadSkin(String id) {
        Net.FileRef f = SKINS.get(id);
        if (f == null || !LOADING.add("s:" + id)) {
            return;
        }
        request(f.hash(), f.bytes(), gz -> {
            byte[] png = gunzip(gz);
            Minecraft.getInstance().execute(() -> {
                Identifier t = register("skin/" + id, png);
                if (t != null) {
                    SKIN_TEX.put(id, t);
                }
            });
        });
    }

    private static Identifier register(String name, byte[] png) {
        try {
            NativeImage img = NativeImage.read(png);
            Identifier loc = Identifier.fromNamespaceAndPath(StarScenes.MOD_ID, name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_/.\\-]", "_") + "_" + (seq++));
            Minecraft.getInstance().getTextureManager().register(loc, new DynamicTexture(loc::toString, img));
            return loc;
        } catch (Exception e) {
            StarScenes.LOGGER.error("StarScenes: imagen {} ilegible: {}", name, e.toString());
            return null;
        }
    }

    private static byte[] gunzip(byte[] gz) {
        try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(gz))) {
            return in.readAllBytes();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ------------------------------------------------------------------ descargas

    private static Path cacheDir() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("starscenes_cache");
    }

    private static void request(String hash, int size, Consumer<byte[]> done) {
        Path f = cacheDir().resolve(hash);
        try {
            if (Files.isRegularFile(f) && Files.size(f) == size) {
                done.accept(Files.readAllBytes(f));
                return;
            }
        } catch (Exception ignored) {
            // se pide de nuevo
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
        DOWNLOADS.clear();
        LOADING.clear();
    }
}
