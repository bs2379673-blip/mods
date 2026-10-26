package com.annis.npc.client;

import com.annis.npc.AnnisNpc;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Carga skins (PNG de 64x64) desde config/annisnpc/skins/<nombre>.png sin necesidad de resource pack.
 */
public final class SkinManager {
    private static final Map<String, Identifier> CACHE = new HashMap<>();
    private static final Set<String> MISSING = new HashSet<>();

    private SkinManager() {
    }

    public static Path dir() {
        return FabricLoader.getInstance().getConfigDir().resolve("annisnpc").resolve("skins");
    }

    /** Devuelve la textura de la skin, o null si no existe (se usa Steve/Alex). */
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
        Path file = dir().resolve(name + ".png");
        if (!Files.isRegularFile(file)) {
            MISSING.add(name);
            return null;
        }
        try (InputStream in = Files.newInputStream(file)) {
            NativeImage image = NativeImage.read(in);
            Identifier id = Identifier.of(AnnisNpc.MOD_ID, "skin/" + sanitize(name));
            MinecraftClient.getInstance().getTextureManager().registerTexture(id, new NativeImageBackedTexture(image));
            CACHE.put(name, id);
            return id;
        } catch (Exception e) {
            AnnisNpc.LOGGER.warn("No se pudo cargar la skin {}", file, e);
            MISSING.add(name);
            return null;
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
                stream.map(p -> p.getFileName().toString())
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
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
    }
}
