package com.annis.npc.client;

import com.annis.npc.AnnisNpc;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.core.data.gson.AnimationSerializing;
import com.annis.npc.compat.FabricLoader;

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
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * Carga emotes en JSON (formato de Emotecraft / Blockbench) desde:
 *  - la carpeta emotes/ del juego (la misma que usa Emotecraft)
 *  - config/annisnpc/emotes/
 */
public final class EmoteLibrary {
    private static final Map<String, KeyframeAnimation> CACHE = new HashMap<>();
    private static final Set<String> MISSING = new HashSet<>();

    private EmoteLibrary() {
    }

    public static List<Path> dirs() {
        FabricLoader loader = FabricLoader.getInstance();
        List<Path> dirs = new ArrayList<>();
        dirs.add(loader.getGameDir().resolve("emotes"));
        dirs.add(loader.getConfigDir().resolve("annisnpc").resolve("emotes"));
        return dirs;
    }

    public static KeyframeAnimation get(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        KeyframeAnimation cached = CACHE.get(name);
        if (cached != null) {
            return cached;
        }
        if (MISSING.contains(name)) {
            return null;
        }
        for (Path dir : dirs()) {
            Path file = dir.resolve(name + ".json");
            if (!Files.isRegularFile(file)) {
                continue;
            }
            try (InputStream in = Files.newInputStream(file)) {
                List<KeyframeAnimation> list = AnimationSerializing.deserializeAnimation(in);
                if (!list.isEmpty()) {
                    CACHE.put(name, list.get(0));
                    return list.get(0);
                }
            } catch (Exception e) {
                AnnisNpc.LOGGER.warn("No se pudo leer el emote {}", file, e);
            }
        }
        MISSING.add(name);
        return null;
    }

    public static void clear() {
        CACHE.clear();
        MISSING.clear();
    }

    public static List<String> list() {
        Set<String> names = new TreeSet<>();
        for (Path dir : dirs()) {
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try (Stream<Path> stream = Files.list(dir)) {
                stream.map(p -> p.getFileName().toString())
                        .filter(n -> n.toLowerCase(Locale.ROOT).endsWith(".json"))
                        .map(n -> n.substring(0, n.length() - 5))
                        .forEach(names::add);
            } catch (Exception e) {
                AnnisNpc.LOGGER.warn("No se pudo listar los emotes de {}", dir, e);
            }
        }
        return new ArrayList<>(names);
    }
}
