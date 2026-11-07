package com.starlevi.starvillagers.social;

import com.google.gson.JsonObject;
import com.starlevi.starscenes.talk.ItemSpec;
import com.starlevi.starvillagers.StarVillagers;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.item.ItemStack;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Las personalidades y los textos de los aldeanos, de config/starvillagers/personalidades.json
 * (se crea sola la primera vez; se puede editar y agregar personalidades).
 */
public final class Personality {
    public final String id, name;
    public final int baseMood, greetChance;
    public final Map<String, List<String>> lines = new HashMap<>();
    public final List<ItemSpec> likes = new ArrayList<>(), dislikes = new ArrayList<>();
    final List<List<String>> convos = new ArrayList<>();

    /** Las conversaciones entre aldeanos (de "_comun", o las del mod si tu archivo no tiene). */
    public static List<List<String>> conversations() {
        if (common != null && !common.convos.isEmpty()) {
            return common.convos;
        }
        return builtin != null ? builtin.convos : List.of();
    }

    private Personality(String id, JsonObject o) {
        this.id = id;
        this.name = o.has("nombre") ? o.get("nombre").getAsString() : id;
        this.baseMood = o.has("humor_base") ? o.get("humor_base").getAsInt() : 0;
        this.greetChance = o.has("ganas_de_saludar") ? o.get("ganas_de_saludar").getAsInt() : 35;
        for (var e : o.entrySet()) {
            if (e.getKey().equals("conversaciones") && e.getValue().isJsonArray()) {
                for (var c : e.getValue().getAsJsonArray()) {
                    if (c.isJsonArray()) {
                        List<String> l = new ArrayList<>();
                        c.getAsJsonArray().forEach(x -> l.add(x.getAsString()));
                        if (l.size() >= 2) {
                            this.convos.add(l);
                        }
                    }
                }
                continue;
            }
            if (e.getValue().isJsonArray()) {
                List<String> l = new ArrayList<>();
                e.getValue().getAsJsonArray().forEach(x -> l.add(x.getAsString()));
                switch (e.getKey()) {
                    case "le_gusta" -> l.forEach(x -> add(likes, x));
                    case "no_le_gusta" -> l.forEach(x -> add(dislikes, x));
                    default -> lines.put(e.getKey(), l);
                }
            }
        }
    }

    private static void add(List<ItemSpec> to, String s) {
        ItemSpec i = ItemSpec.parse(s);
        if (i != null) {
            to.add(i);
        }
    }

    public static final Map<String, Personality> ALL = new LinkedHashMap<>();
    /** Lo comun a todos (textos generales y gustos por oficio). */
    public static Personality common;
    /** Lo comun que trae el mod (por si tu archivo es de una version vieja y le faltan textos). */
    private static Personality builtin;
    public static final Map<String, List<ItemSpec>> PROFESSION_LIKES = new HashMap<>();

    public static Personality get(String id) {
        Personality p = ALL.get(id);
        return p != null ? p : ALL.isEmpty() ? common : ALL.values().iterator().next();
    }

    public static String random(Random r) {
        if (ALL.isEmpty()) {
            return "amistoso";
        }
        List<String> ids = new ArrayList<>(ALL.keySet());
        return ids.get(r.nextInt(ids.size()));
    }

    /** Un texto al azar de esta personalidad, o del comun si no tiene. */
    public String line(String key, Random r) {
        List<String> l = lines.get(key);
        if ((l == null || l.isEmpty()) && common != null && common != this) {
            l = common.lines.get(key);
        }
        if ((l == null || l.isEmpty()) && builtin != null) {
            l = builtin.lines.get(key);
        }
        if (l == null || l.isEmpty()) {
            return "";
        }
        return l.get(r.nextInt(l.size()));
    }

    /** 2 = le encanta, 1 = le gusta, 0 = normal, -1 = no le gusta. */
    public int taste(ItemStack st, String profession) {
        for (ItemSpec i : dislikes) {
            if (i.matches(st)) {
                return -1;
            }
        }
        if (common != null) {
            for (ItemSpec i : common.dislikes) {
                if (i.matches(st)) {
                    return -1;
                }
            }
        }
        for (ItemSpec i : likes) {
            if (i.matches(st)) {
                return 2;
            }
        }
        for (ItemSpec i : PROFESSION_LIKES.getOrDefault(profession, List.of())) {
            if (i.matches(st)) {
                return 2;
            }
        }
        if (common != null) {
            for (ItemSpec i : common.likes) {
                if (i.matches(st)) {
                    return 1;
                }
            }
        }
        return 0;
    }

    public static void load() {
        Path f = FabricLoader.getInstance().getConfigDir().resolve("starvillagers").resolve("personalidades.json");
        try {
            if (Files.exists(f) && !Files.readString(f, StandardCharsets.UTF_8).contains("charla_general")) {
                // la version sin gestos se guarda aparte
                Files.move(f, f.resolveSibling("personalidades_viejo.json"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            if (!Files.exists(f)) {
                Files.createDirectories(f.getParent());
                try (var in = Personality.class.getResourceAsStream("/starvillagers_dialogos/personalidades.json")) {
                    Files.write(f, in.readAllBytes());
                }
            }
            var r = new com.google.gson.stream.JsonReader(new java.io.StringReader(Files.readString(f, StandardCharsets.UTF_8)));
            r.setStrictness(com.google.gson.Strictness.LENIENT);
            JsonObject root = com.google.gson.JsonParser.parseReader(r).getAsJsonObject();
            try (var in = Personality.class.getResourceAsStream("/starvillagers_dialogos/personalidades.json")) {
                var br = new com.google.gson.stream.JsonReader(new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
                br.setStrictness(com.google.gson.Strictness.LENIENT);
                builtin = new Personality("_comun", com.google.gson.JsonParser.parseReader(br).getAsJsonObject().getAsJsonObject("_comun"));
            }
            ALL.clear();
            PROFESSION_LIKES.clear();
            common = null;
            for (var e : root.entrySet()) {
                if (!e.getValue().isJsonObject()) {
                    continue;
                }
                if (e.getKey().equals("_comun")) {
                    common = new Personality("_comun", e.getValue().getAsJsonObject());
                } else if (e.getKey().equals("_gustos_por_oficio")) {
                    for (var o : e.getValue().getAsJsonObject().entrySet()) {
                        List<ItemSpec> l = new ArrayList<>();
                        o.getValue().getAsJsonArray().forEach(x -> add(l, x.getAsString()));
                        PROFESSION_LIKES.put(o.getKey(), l);
                    }
                } else {
                    ALL.put(e.getKey(), new Personality(e.getKey(), e.getValue().getAsJsonObject()));
                }
            }
            if (common == null) {
                common = new Personality("_comun", new JsonObject());
            }
            StarVillagers.LOGGER.info("StarVillagers: {} personalidades", ALL.size());
        } catch (Exception e) {
            StarVillagers.LOGGER.warn("StarVillagers: no pude leer personalidades.json: {}", e.toString());
            if (common == null) {
                common = new Personality("_comun", new JsonObject());
            }
        }
    }
}
