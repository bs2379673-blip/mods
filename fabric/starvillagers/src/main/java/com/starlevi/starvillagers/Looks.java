package com.starlevi.starvillagers;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Las piezas para armar aldeanos (texturas de piel, caras, pelo y ropa de Minecraft Comes Alive,
 * GPL-3.0) y los nombres. Arma una identidad nueva al azar, o la ropa segun el oficio.
 */
public final class Looks {
    private static final List<String> TEXTURES = new ArrayList<>();
    private static final Map<String, List<String>> NAMES = new LinkedHashMap<>();
    private static final Map<String, int[]> NAME_WEIGHTS = new LinkedHashMap<>();
    private static boolean loaded;

    private Looks() {
    }

    private static String read(String path) {
        try (InputStream in = Looks.class.getResourceAsStream(path)) {
            return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    private static synchronized void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        String idx = read("/starvillagers_mca/texturas.txt");
        if (idx == null) {
            StarVillagers.LOGGER.warn("StarVillagers: no estan las texturas de MCA (se bajan al compilar con ci/bajar_mca.sh)");
            return;
        }
        for (String l : idx.split("\n")) {
            if (!l.isBlank()) {
                TEXTURES.add(l.trim());
            }
        }
        for (String region : List.of("spain")) {
            for (String g : List.of("male", "female")) {
                String json = read("/starvillagers_mca/nombres/" + region + "/" + g + ".json");
                if (json == null) {
                    continue;
                }
                JsonObject o = JsonParser.parseString(json).getAsJsonObject();
                List<String> names = NAMES.computeIfAbsent(g, k -> new ArrayList<>());
                List<Integer> w = new ArrayList<>();
                int[] old = NAME_WEIGHTS.getOrDefault(g, new int[0]);
                for (int x : old) {
                    w.add(x);
                }
                for (Map.Entry<String, JsonElement> e : o.entrySet()) {
                    names.add(e.getKey());
                    w.add(Math.max(1, (int) Math.sqrt(e.getValue().getAsInt())));
                }
                NAME_WEIGHTS.put(g, w.stream().mapToInt(Integer::intValue).toArray());
            }
        }
        StarVillagers.LOGGER.info("StarVillagers: {} texturas, {} + {} nombres", TEXTURES.size(), NAMES.getOrDefault("male", List.of()).size(),
                NAMES.getOrDefault("female", List.of()).size());
    }

    public static boolean ready() {
        load();
        return !TEXTURES.isEmpty();
    }

    /** Las texturas de una carpeta (sin las variantes de parpadeo/heterocromia). */
    private static List<String> folder(String prefix) {
        load();
        List<String> out = new ArrayList<>();
        for (String t : TEXTURES) {
            if (t.startsWith(prefix) && t.indexOf('/', prefix.length()) < 0 && !t.contains("_blink") && !t.contains("_hetero")) {
                out.add(t);
            }
        }
        return out;
    }

    private static String pick(List<String> l, Random r, String def) {
        return l.isEmpty() ? def : l.get(r.nextInt(l.size()));
    }

    private static String name(boolean female, Random r) {
        String g = female ? "female" : "male";
        List<String> names = NAMES.get(g);
        int[] w = NAME_WEIGHTS.get(g);
        if (names == null || names.isEmpty()) {
            return female ? "Ana" : "Juan";
        }
        int total = 0;
        for (int x : w) {
            total += x;
        }
        int k = r.nextInt(Math.max(1, total));
        for (int i = 0; i < names.size(); i++) {
            k -= w[i];
            if (k < 0) {
                return names.get(i);
            }
        }
        return names.get(0);
    }

    /** Todos de tez blanca o clara (config "piel": "clara"). */
    public static boolean lightSkin = true;
    private static final float LIGHT_MELANIN = 0.24f, LIGHT_HEMOGLOBIN = 0.2f;

    /** Si la piel no entra en lo elegido, la vuelve a sortear (clara); si entra, devuelve la misma. */
    public static Identity fitSkin(Identity id, Random r) {
        if (!lightSkin || id.melanin() <= LIGHT_MELANIN && id.hemoglobin() <= LIGHT_HEMOGLOBIN) {
            return id;
        }
        return new Identity(id.name(), id.female(), r.nextFloat() * LIGHT_MELANIN, r.nextFloat() * LIGHT_HEMOGLOBIN * 0.8f, id.eumelanin(),
                id.pheomelanin(), id.height(), id.skin(), id.face(), id.hair(), id.clothes(), id.voice(), id.seed());
    }

    private static float clamp(float x) {
        return Math.max(0, Math.min(1, x));
    }

    /** Un genes al azar, que tiende al medio (como la gente). */
    private static float gene(Random r) {
        return Math.max(0, Math.min(1, (r.nextFloat() + r.nextFloat() + r.nextFloat()) / 3f));
    }

    /** Un aldeano nuevo al azar (oficio: "farmer", "none", "child"...), como en un bioma templado. */
    public static Identity random(Random r, String profession) {
        return random(r, profession, 0.8f);
    }

    /**
     * Un aldeano nuevo al azar. temp = la temperatura del bioma (como en MCA: en los frios la piel es
     * mas clara y en los calurosos mas oscura; y de vez en cuando llega alguien de otro lado).
     */
    public static Identity random(Random r, String profession, float temp) {
        boolean female = r.nextBoolean();
        String g = female ? "female" : "male";
        if (r.nextFloat() < 0.15f) {
            temp = r.nextFloat() * 2 - 0.5f;
        }
        float melanin = clamp((r.nextFloat() - 0.5f) * 0.35f + temp * 0.4f + 0.1f);
        float hemoglobin = clamp(((r.nextFloat() - 0.5f) * 0.35f + temp * 0.4f + 0.1f) * 0.5f);
        float eu = r.nextFloat(), pheo = r.nextFloat();
        if (lightSkin) {
            melanin = r.nextFloat() * LIGHT_MELANIN;
            hemoglobin = r.nextFloat() * LIGHT_HEMOGLOBIN * 0.8f;
        }
        int skinIdx = (int) Math.min(4, Math.max(0, gene(r) * 5));
        String skin = "skin/" + g + "/" + skinIdx + ".png";
        String face = pick(folder("face/normal/" + g + "/"), r, "face/normal/" + g + "/0.png");
        String hair = pick(folder("hair/" + g + "/"), r, "hair/" + g + "/0.png");
        float height = female ? 0.9f + r.nextFloat() * 0.1f : 0.95f + r.nextFloat() * 0.1f;
        float voice = 0.88f + r.nextFloat() * 0.24f;
        Identity id = new Identity(name(female, r), female, melanin, hemoglobin, eu, pheo, height, skin, face, hair, "", voice, r.nextInt());
        return id.withClothes(clothes(id, profession));
    }

    /** Un pelo de su genero, siempre el mismo para esa semilla. */
    public static String hair(int seed, boolean female) {
        String g = female ? "female" : "male";
        return pick(folder("hair/" + g + "/"), new Random(seed), "hair/" + g + "/0.png");
    }

    /** La ropa para ese oficio (si no hay, la de todos los dias). */
    public static String clothes(Identity id, String profession) {
        Random r = new Random(id.seed() * 31L + profession.hashCode());
        String g = id.female() ? "female" : "male";
        String p = profession.toLowerCase(Locale.ROOT);
        List<String> l = new ArrayList<>(folder("clothing/normal/" + g + "/" + p + "/"));
        l.addAll(folder("clothing/normal/neutral/" + p + "/"));
        if (l.isEmpty()) {
            l.addAll(folder("clothing/normal/" + g + "/none/"));
        }
        return pick(l, r, "");
    }

    /** Un bebe de dos padres (el otro puede ser null, por ejemplo un jugador): mezcla sus genes. */
    public static Identity child(Random r, Identity a, Identity b) {
        Identity base = random(r, "child");
        if (a == null) {
            return base;
        }
        Identity o = b != null ? b : random(r, "none");
        float mel = mix(r, a.melanin(), o.melanin()), hem = mix(r, a.hemoglobin(), o.hemoglobin());
        float eu = mix(r, a.eumelanin(), o.eumelanin()), ph = mix(r, a.pheomelanin(), o.pheomelanin());
        String g = base.female() ? "female" : "male";
        // el tono de piel (0-4) de alguno de los padres
        String parentSkin = (r.nextBoolean() ? a : o).skin();
        String idx = parentSkin.replaceAll(".*/(\\d+)\\.png$", "$1");
        String skin = "skin/" + g + "/" + (idx.matches("\\d+") ? idx : "2") + ".png";
        Identity id = new Identity(base.name(), base.female(), mel, hem, eu, ph, base.height(), skin, base.face(), base.hair(), "", base.voice(), base.seed());
        return fitSkin(id.withClothes(clothes(id, "child")), r);
    }

    private static float mix(Random r, float x, float y) {
        float t = 0.3f + r.nextFloat() * 0.4f;
        return Math.max(0, Math.min(1, x * t + y * (1 - t) + (r.nextFloat() - 0.5f) * 0.08f));
    }

    /** El mismo aldeano con el otro genero (cara, pelo, piel y ropa de ese genero; mismos genes). */
    public static Identity regender(Identity id, boolean female, String profession) {
        if (id.female() == female) {
            return id;
        }
        Random r = new Random(id.seed() * 7L + (female ? 1 : 2));
        String g = female ? "female" : "male";
        String skin = id.skin().replaceAll("/(male|female)/", "/" + g + "/");
        String face = pick(folder("face/normal/" + g + "/"), r, "face/normal/" + g + "/0.png");
        String hair = id.hair().startsWith("biblioteca:") ? id.hair() : pick(folder("hair/" + g + "/"), r, "hair/" + g + "/0.png");
        Identity n = new Identity(id.name(), female, id.melanin(), id.hemoglobin(), id.eumelanin(), id.pheomelanin(), id.height(), skin, face, hair, "",
                id.voice(), id.seed());
        return n.withClothes(id.clothes().startsWith("biblioteca:") ? id.clothes() : clothes(n, profession));
    }

    public static Identity rename(Identity id, String name) {
        return new Identity(name, id.female(), id.melanin(), id.hemoglobin(), id.eumelanin(), id.pheomelanin(), id.height(), id.skin(), id.face(), id.hair(),
                id.clothes(), id.voice(), id.seed());
    }
}
