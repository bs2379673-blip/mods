package com.annis.compat.client;

import com.annis.compat.AnnisCompat;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.Reader;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Las animaciones de Annis Compat. Viven donde Punchy Moves las carga
 * (assets/punchy_moves/animations/...), asi se abren y se editan con Punchy Studio.
 * Si un paquete de recursos trae una animacion con el mismo nombre, gana la del paquete.
 */
public final class ClipLibrary {
    public static final String PREFIX = "annis_";
    private static final Map<String, Clip> CLIPS = new HashMap<>();

    private ClipLibrary() {
    }

    public static synchronized Clip get(String name) {
        return CLIPS.get(name);
    }

    public static synchronized void load(ResourceManager manager) {
        CLIPS.clear();
        Map<String, Boolean> fromPack = new HashMap<>();
        Map<ResourceLocation, Resource> found = manager.listResources("animations", id -> id.getPath().endsWith(".json"));
        for (Map.Entry<ResourceLocation, Resource> en : found.entrySet()) {
            String ns = en.getKey().getNamespace();
            if (!ns.equals("punchy_moves") && !ns.equals("annis_compat")) {
                continue;
            }
            String pack = String.valueOf(en.getValue().sourcePackId()).toLowerCase(Locale.ROOT);
            // lo de los paquetes de recursos del jugador ("file/...") gana a lo que traen los mods
            boolean ours = !pack.startsWith("file/");
            try (Reader r = en.getValue().openAsReader()) {
                JsonElement root = JsonParser.parseReader(r);
                if (!root.isJsonObject() || !root.getAsJsonObject().has("animations")) {
                    continue;
                }
                for (Map.Entry<String, JsonElement> a : root.getAsJsonObject().getAsJsonObject("animations").entrySet()) {
                    String name = a.getKey().toLowerCase(Locale.ROOT);
                    if (!name.startsWith(PREFIX) || !a.getValue().isJsonObject()) {
                        continue;
                    }
                    Boolean prev = fromPack.get(name);
                    if (prev != null && (prev || ours)) {
                        continue; // ya hay una version de un paquete (o esta es la nuestra repetida)
                    }
                    CLIPS.put(name, Clip.read(name, (JsonObject) a.getValue()));
                    fromPack.put(name, !ours);
                }
            } catch (Exception ex) {
                AnnisCompat.LOGGER.warn("No se pudo leer la animacion {}: {}", en.getKey(), ex.toString());
            }
        }
        AnnisCompat.LOGGER.info("Annis Compat: {} animaciones cargadas", CLIPS.size());
    }
}
