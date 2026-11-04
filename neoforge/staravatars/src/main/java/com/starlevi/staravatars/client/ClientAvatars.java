package com.starlevi.staravatars.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.starlevi.staravatars.StarAvatars;
import com.starlevi.staravatars.net.Net;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.zip.GZIPInputStream;

/**
 * Lo que el cliente sabe de los personajes: la lista del servidor, que usa cada jugador, y los modelos
 * ya armados. Los archivos se bajan una sola vez (quedan en staravatars_cache) y solo los de los
 * personajes que alguien usa.
 */
public final class ClientAvatars {
    /** Un personaje listo para dibujar. */
    public static final class Ready {
        public final AvatarBuilder.Result model;
        public final ResourceLocation[] textures;

        Ready(AvatarBuilder.Result model, ResourceLocation[] textures) {
            this.model = model;
            this.textures = textures;
        }
    }

    private static final Map<String, Net.Avatar> INDEX = new LinkedHashMap<>();
    private static final Map<UUID, String> OUTFITS = new HashMap<>();
    private static final Map<String, Ready> READY = new HashMap<>();
    /** Personajes que se estan bajando o armando (para no empezar dos veces). */
    private static final Map<String, Boolean> LOADING = new HashMap<>();
    private static final Map<String, Pending> DOWNLOADS = new HashMap<>();
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

    public static void receive(Net.Index i) {
        INDEX.clear();
        for (Net.Avatar a : i.avatars()) {
            INDEX.put(a.id(), a);
        }
        // si cambio un personaje (otros archivos), se vuelve a armar
        READY.keySet().removeIf(id -> !INDEX.containsKey(id));
        LOADING.clear();
    }

    public static void outfit(Net.Outfit o) {
        if (o.avatar().isEmpty()) {
            OUTFITS.remove(o.player());
        } else {
            OUTFITS.put(o.player(), o.avatar());
        }
    }

    /** El personaje armado que usa ese jugador, o null (y si falta, lo empieza a bajar). */
    public static Ready of(UUID player) {
        String id = OUTFITS.get(player);
        return id == null ? null : get(id);
    }

    public static String outfitId(UUID player) {
        return OUTFITS.get(player);
    }

    public static Ready get(String id) {
        Ready r = READY.get(id);
        if (r == null) {
            load(id);
        }
        return r;
    }

    private static void load(String id) {
        Net.Avatar a = INDEX.get(id);
        if (a == null || LOADING.containsKey(id)) {
            return;
        }
        LOADING.put(id, true);
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

    /** Se arma en otro hilo (leer el .obj lleva un segundo); las texturas se suben despues en el hilo del juego. */
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
            return AvatarBuilder.build(files, a.json());
        }).whenComplete((res, err) -> Minecraft.getInstance().execute(() -> {
            if (err != null) {
                StarAvatars.LOGGER.error("StarAvatars: no se pudo armar {}: {}", a.id(), err.toString());
                return;
            }
            for (String l : res.log) {
                StarAvatars.LOGGER.info("StarAvatars [{}] {}", a.id(), l);
            }
            ResourceLocation[] tex = new ResourceLocation[res.materials.size()];
            for (int i = 0; i < tex.length; i++) {
                AvatarBuilder.Material m = res.materials.get(i);
                NativeImage img = new NativeImage(m.width, m.height, false);
                for (int y = 0; y < m.height; y++) {
                    for (int x = 0; x < m.width; x++) {
                        int p = m.argb[y * m.width + x];
                        // NativeImage guarda ABGR
                        img.setPixelRGBA(x, y, (p & 0xFF00FF00) | (p & 0xFF) << 16 | (p >> 16) & 0xFF);
                    }
                }
                m.argb = null;
                ResourceLocation loc = ResourceLocation.fromNamespaceAndPath(StarAvatars.MOD_ID, "avatar/" + a.id() + "_" + (textureSeq++));
                Minecraft.getInstance().getTextureManager().register(loc, new DynamicTexture(img));
                tex[i] = loc;
            }
            READY.put(a.id(), new Ready(res, tex));
        }));
    }

    // ------------------------------------------------------------------ descargas

    private static Path cacheDir() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("staravatars_cache");
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
            PacketDistributor.sendToServer(new Net.Need(hash));
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

    /** Al salir del servidor se olvida quien usa que (los modelos armados quedan para la proxima). */
    public static void disconnect() {
        OUTFITS.clear();
        DOWNLOADS.clear();
        LOADING.clear();
    }
}
