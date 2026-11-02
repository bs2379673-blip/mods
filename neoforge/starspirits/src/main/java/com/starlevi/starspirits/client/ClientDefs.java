package com.starlevi.starspirits.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.starlevi.starspirits.SpiritDef;
import com.starlevi.starspirits.StarSpirits;
import com.starlevi.starspirits.net.Net;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Lo que el cliente sabe de los espiritus del servidor: definiciones, texturas y modelos. Los
 * archivos se piden una sola vez y quedan guardados en starspirits_cache.
 */
public final class ClientDefs {
    private static final Map<String, SpiritDef> DEFS = new HashMap<>();
    private static final Map<String, String> FILE_HASH = new HashMap<>();
    private static final Map<String, ResourceLocation> TEXTURES = new HashMap<>();
    private static final Map<String, SpiritRig> MODELS = new HashMap<>();
    private static final Map<String, Boolean> BUILTIN_TEX = new HashMap<>();
    private static final Map<String, byte[]> CACHE = new HashMap<>();
    private static final Map<String, Pending> DOWNLOADS = new HashMap<>();

    private static final class Pending {
        final byte[] buf;
        final List<Consumer<byte[]>> waiters = new ArrayList<>();

        Pending(int size) {
            this.buf = new byte[size];
        }
    }

    /** Forma de los espiritus de ejemplo (por si llegan antes que la lista del servidor). */
    private static final Map<String, String> BUILTIN_FORM = Map.of("champi", "hongo", "luminon", "hongo", "margarita", "flor",
            "loto", "loto", "brotecito", "arbusto", "bonsai", "bonsai", "campanita", "campana", "rosita", "rosa");
    private static final Map<String, String> FORM_TEX = Map.of("hongo", "champi", "flor", "margarita", "loto", "loto",
            "arbusto", "brotecito", "bonsai", "bonsai", "campana", "campanita", "rosa", "rosita");

    private ClientDefs() {
    }

    public static SpiritDef def(String id) {
        return DEFS.get(id);
    }

    public static void receive(Net.Defs p) {
        DEFS.clear();
        FILE_HASH.clear();
        for (Net.Def d : p.defs()) {
            try {
                DEFS.put(d.id(), new SpiritDef(d.id(), d.json()));
            } catch (Exception e) {
                StarSpirits.LOGGER.warn("StarSpirits: espiritu {} invalido: {}", d.id(), e.toString());
                continue;
            }
            for (Net.FileRef f : d.files()) {
                FILE_HASH.put(f.file(), f.hash());
                if (!TEXTURES.containsKey(f.hash()) && !MODELS.containsKey(f.hash())) {
                    request(f.hash(), f.bytes(), data -> loaded(f.file(), f.hash(), data));
                }
            }
        }
    }

    private static void loaded(String file, String hash, byte[] data) {
        Minecraft.getInstance().execute(() -> {
            try {
                if (file.endsWith(".png")) {
                    NativeImage img = NativeImage.read(data);
                    ResourceLocation loc = ResourceLocation.fromNamespaceAndPath(StarSpirits.MOD_ID, "servidor/" + hash);
                    Minecraft.getInstance().getTextureManager().register(loc, new DynamicTexture(img));
                    TEXTURES.put(hash, loc);
                } else if (file.endsWith(".json")) {
                    MODELS.put(hash, GeoModel.parse(data));
                } else if (file.endsWith(".obj")) {
                    MODELS.put(hash, ObjModel.parse(data));
                }
            } catch (Exception e) {
                StarSpirits.LOGGER.warn("StarSpirits: no se pudo cargar {}: {}", file, e.toString());
            }
        });
    }

    private static String formOf(String id) {
        SpiritDef d = DEFS.get(id);
        if (d != null) {
            return d.form;
        }
        return BUILTIN_FORM.getOrDefault(id, "hongo");
    }

    public static SpiritRig rig(String id) {
        SpiritDef d = DEFS.get(id);
        if (d != null && d.modelFile != null) {
            SpiritRig m = MODELS.get(FILE_HASH.get(d.modelFile));
            if (m != null) {
                return m;
            }
        }
        return Forms.get(formOf(id));
    }

    public static ResourceLocation texture(String id) {
        SpiritDef d = DEFS.get(id);
        if (d != null && d.textureFile != null) {
            ResourceLocation t = TEXTURES.get(FILE_HASH.get(d.textureFile));
            if (t != null) {
                return t;
            }
        }
        ResourceLocation own = builtin(id);
        if (own != null) {
            return own;
        }
        return builtin(FORM_TEX.getOrDefault(formOf(id), "champi"));
    }

    /** Textura que brilla (luz propia), o null. */
    public static ResourceLocation glow(String id) {
        SpiritDef d = DEFS.get(id);
        if (d != null && d.glowFile != null) {
            return TEXTURES.get(FILE_HASH.get(d.glowFile));
        }
        if (d != null && d.textureFile != null) {
            return null;
        }
        return builtinGlow(id);
    }

    private static ResourceLocation builtin(String name) {
        ResourceLocation loc = ResourceLocation.fromNamespaceAndPath(StarSpirits.MOD_ID, "textures/entity/" + name + ".png");
        Boolean has = BUILTIN_TEX.computeIfAbsent(loc.toString(),
                k -> Minecraft.getInstance().getResourceManager().getResource(loc).isPresent());
        return has ? loc : null;
    }

    private static ResourceLocation builtinGlow(String name) {
        return builtin(name + "_brillo");
    }

    public static int tint(String id) {
        SpiritDef d = DEFS.get(id);
        return d == null ? 0xFFFFFF : d.tint;
    }

    public static float turn(String id) {
        SpiritDef d = DEFS.get(id);
        return d == null ? 0f : d.turn;
    }

    public static float scale(String id) {
        SpiritDef d = DEFS.get(id);
        return d == null ? 1f : d.scale;
    }

    // ------------------------------------------------------------------ descargas

    private static Path cacheDir() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("starspirits_cache");
    }

    private static byte[] cached(String hash) {
        byte[] b = CACHE.get(hash);
        if (b != null) {
            return b;
        }
        Path f = cacheDir().resolve(hash);
        try {
            if (Files.isRegularFile(f)) {
                b = Files.readAllBytes(f);
                CACHE.put(hash, b);
                return b;
            }
        } catch (Exception ignored) {
            // se pide de nuevo
        }
        return null;
    }

    private static void request(String hash, int size, Consumer<byte[]> done) {
        byte[] have = cached(hash);
        if (have != null) {
            done.accept(have);
            return;
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
        CACHE.put(c.hash(), p.buf);
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
}
