package com.starlevi.starscenes.emf;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.starlevi.starscenes.StarScenes;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Los modelos de EMF de los paquetes de recursos activos (por ejemplo player.jem de Fresh
 * Animations, Joyful Motions o Just Expressions). Se leen de los paquetes que tengas puestos, no se
 * copian. Si cambias los paquetes, se vuelven a leer.
 */
public final class EmfModels {
    private static final Map<String, JemModel> CACHE = new HashMap<>();
    private static String packs = "";
    private static long checked;

    private EmfModels() {
    }

    private static JsonObject json(Identifier id) {
        try {
            var res = Minecraft.getInstance().getResourceManager().getResource(id);
            if (res.isEmpty()) {
                return null;
            }
            try (var r = new JsonReader(new InputStreamReader(res.get().open(), StandardCharsets.UTF_8))) {
                r.setStrictness(Strictness.LENIENT);
                return JsonParser.parseReader(r).getAsJsonObject();
            }
        } catch (Exception e) {
            StarScenes.LOGGER.warn("StarScenes: no pude leer {}: {}", id, e.toString());
            return null;
        }
    }

    /** El modelo de EMF con ese nombre ("player", "player_slim"...), o null si ningun paquete lo trae. */
    public static JemModel get(String name) {
        long now = System.currentTimeMillis();
        if (now - checked > 1000) {
            checked = now;
            String p = String.join(",", Minecraft.getInstance().getResourcePackRepository().getSelectedIds());
            if (!p.equals(packs)) {
                packs = p;
                CACHE.clear();
            }
        }
        if (CACHE.containsKey(name)) {
            return CACHE.get(name);
        }
        JemModel m = null;
        for (String dir : new String[]{"emf/cem/", "optifine/cem/"}) {
            JsonObject root = json(Identifier.withDefaultNamespace(dir + name + ".jem"));
            if (root == null) {
                continue;
            }
            String folder = dir;
            m = JemModel.parse(root, ref -> {
                String r = ref.endsWith(".jpm") ? ref : ref + ".jpm";
                Identifier id = r.contains(":") ? Identifier.tryParse(r) : Identifier.withDefaultNamespace(r.contains("/") ? r : folder + r);
                return id == null ? null : json(id);
            });
            StarScenes.LOGGER.info("StarScenes: modelo de EMF {} ({}): {} piezas, {} formulas{}", name, dir, m.parts.size(), m.lines.size(),
                    m.warnings.isEmpty() ? "" : ", avisos: " + m.warnings);
            break;
        }
        CACHE.put(name, m);
        return m;
    }
}
