package com.starlevi.starpackport.convert;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * StarPackPort (propiedad de Starlevi): convierte un paquete de recursos hecho para versiones nuevas
 * (1.20.2 a 1.21.11) al formato que entiende Minecraft 1.20.1 (pack_format 15).
 * <p>
 * No usa clases de Minecraft, asi se puede probar afuera del juego.
 */
public final class PackConverter {
    public static final int TARGET = 15;
    /** Cambiar cuando cambia la conversion, para que se rehagan los packs ya convertidos. */
    public static final String VERSION = "1";

    /** Busca un archivo del juego base (1.20.1), p. ej. "assets/minecraft/textures/gui/icons.png". */
    public interface Vanilla {
        byte[] get(String path);
    }

    static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    final Map<String, byte[]> files = new TreeMap<>();
    final Set<String> fromOverlay = new HashSet<>();
    final Vanilla vanilla;
    final Consumer<String> log;
    final List<String> report = new ArrayList<>();
    JsonObject meta = new JsonObject();
    int targetFormat = TARGET;

    private PackConverter(Vanilla vanilla, Consumer<String> log) {
        this.vanilla = vanilla;
        this.log = log;
    }

    // ------------------------------------------------------------------ entrada

    /** Lista los nombres de archivo del pack (zip o carpeta), con "/" como separador. */
    public static List<String> listNames(Path pack) throws IOException {
        List<String> out = new ArrayList<>();
        if (Files.isDirectory(pack)) {
            try (Stream<Path> s = Files.walk(pack)) {
                s.filter(Files::isRegularFile).forEach(p -> out.add(pack.relativize(p).toString().replace('\\', '/')));
            }
        } else {
            try (ZipFile z = new ZipFile(pack.toFile())) {
                Iterator<? extends ZipEntry> it = z.entries().asIterator();
                while (it.hasNext()) {
                    ZipEntry e = it.next();
                    if (!e.isDirectory()) {
                        out.add(e.getName());
                    }
                }
            }
        }
        return out;
    }

    static Map<String, byte[]> readAll(Path pack) throws IOException {
        Map<String, byte[]> out = new LinkedHashMap<>();
        if (Files.isDirectory(pack)) {
            try (Stream<Path> s = Files.walk(pack)) {
                for (Path p : (Iterable<Path>) s.filter(Files::isRegularFile)::iterator) {
                    out.put(pack.relativize(p).toString().replace('\\', '/'), Files.readAllBytes(p));
                }
            }
        } else {
            try (ZipFile z = new ZipFile(pack.toFile())) {
                Iterator<? extends ZipEntry> it = z.entries().asIterator();
                while (it.hasNext()) {
                    ZipEntry e = it.next();
                    if (!e.isDirectory()) {
                        try (InputStream in = z.getInputStream(e)) {
                            out.put(e.getName(), in.readAllBytes());
                        }
                    }
                }
            }
        }
        return out;
    }

    static byte[] readOne(Path pack, String name) throws IOException {
        if (Files.isDirectory(pack)) {
            Path p = pack.resolve(name);
            return Files.isRegularFile(p) ? Files.readAllBytes(p) : null;
        }
        try (ZipFile z = new ZipFile(pack.toFile())) {
            ZipEntry e = z.getEntry(name);
            if (e == null) {
                return null;
            }
            try (InputStream in = z.getInputStream(e)) {
                return in.readAllBytes();
            }
        }
    }

    // ------------------------------------------------------------------ hace falta?

    private static final Pattern NEW_STUFF = Pattern.compile(
            "^assets/[^/]+/(items|equipment|shaders|post_effect)/.*"
                    + "|^assets/[^/]+/textures/gui/sprites/.*"
                    + "|^assets/[^/]+/textures/entity/equipment/.*"
                    + "|^assets/[^/]+/textures/trims/entity/.*"
                    + "|^assets/minecraft/atlases/items\\.json$"
                    + "|.*/(short_grass|iron_chain|turtle_scute|elytra_broken|temperate_(cow|pig|chicken)|sheep_wool)\\.(png|json)$");

    /** Dice si el pack trae cosas que 1.20.1 no entiende. */
    public static boolean needsConversion(Path pack) {
        try {
            byte[] mm = readOne(pack, "pack.mcmeta");
            if (mm == null) {
                return false;
            }
            JsonObject meta = parseObject(mm);
            JsonObject p = meta.has("pack") && meta.get("pack").isJsonObject() ? meta.getAsJsonObject("pack") : new JsonObject();
            int min = packMin(p);
            if (min > TARGET) {
                return true;
            }
            int t = Math.max(TARGET, min);
            Set<String> applied = new HashSet<>();
            for (String d : overlays(meta, t)) {
                applied.add(d + "/");
            }
            for (String n : listNames(pack)) {
                String rel = n;
                for (String d : applied) {
                    if (n.startsWith(d)) {
                        rel = n.substring(d.length());
                        break;
                    }
                }
                if (rel.startsWith("assets/") && NEW_STUFF.matcher(rel).matches()) {
                    return true;
                }
            }
            return !applied.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    // ------------------------------------------------------------------ formatos del pack.mcmeta

    /** Rango de formatos: numero, [min, max] o {min_inclusive, max_inclusive}. */
    static int[] range(JsonElement e) {
        if (e == null || e.isJsonNull()) {
            return null;
        }
        if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) {
            int v = (int) Math.floor(e.getAsDouble());
            return new int[]{v, v};
        }
        if (e.isJsonArray()) {
            JsonArray a = e.getAsJsonArray();
            if (a.size() == 1) {
                int v = a.get(0).getAsInt();
                return new int[]{v, v};
            }
            if (a.size() >= 2) {
                return new int[]{a.get(0).getAsInt(), a.get(1).getAsInt()};
            }
        }
        if (e.isJsonObject()) {
            JsonObject o = e.getAsJsonObject();
            int lo = o.has("min_inclusive") ? o.get("min_inclusive").getAsInt() : Integer.MIN_VALUE;
            int hi = o.has("max_inclusive") ? o.get("max_inclusive").getAsInt() : Integer.MAX_VALUE;
            return new int[]{lo, hi};
        }
        return null;
    }

    /** min_format / max_format: numero, [mayor, menor] o decimal. */
    static Integer major(JsonElement e) {
        if (e == null || e.isJsonNull()) {
            return null;
        }
        if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) {
            return (int) Math.floor(e.getAsDouble());
        }
        if (e.isJsonArray() && e.getAsJsonArray().size() > 0) {
            return e.getAsJsonArray().get(0).getAsInt();
        }
        return null;
    }

    static int packMin(JsonObject p) {
        int min = Integer.MAX_VALUE;
        try {
            Integer mf = major(p.get("min_format"));
            if (mf != null) {
                min = Math.min(min, mf);
            }
            int[] sf = range(p.get("supported_formats"));
            if (sf != null) {
                min = Math.min(min, sf[0]);
            }
            if (p.has("pack_format")) {
                min = Math.min(min, p.get("pack_format").getAsInt());
            }
        } catch (RuntimeException ignored) {
        }
        return min == Integer.MAX_VALUE ? TARGET : min;
    }

    /** Carpetas de "overlays" que valen para el formato t, en el orden en que se aplican. */
    static List<String> overlays(JsonObject meta, int t) {
        List<String> out = new ArrayList<>();
        try {
            if (!meta.has("overlays")) {
                return out;
            }
            JsonArray entries = meta.getAsJsonObject("overlays").getAsJsonArray("entries");
            for (JsonElement el : entries) {
                JsonObject o = el.getAsJsonObject();
                int lo;
                int hi;
                int[] r = range(o.get("formats"));
                if (r != null) {
                    lo = r[0];
                    hi = r[1];
                } else {
                    Integer a = major(o.get("min_format"));
                    Integer b = major(o.get("max_format"));
                    lo = a == null ? Integer.MIN_VALUE : a;
                    hi = b == null ? Integer.MAX_VALUE : b;
                }
                if (lo <= t && t <= hi && o.has("directory")) {
                    out.add(o.get("directory").getAsString());
                }
            }
        } catch (RuntimeException ignored) {
        }
        return out;
    }

    // ------------------------------------------------------------------ conversion

    /**
     * Convierte el pack y lo escribe (como carpeta) en {@code out}. Devuelve un resumen de lo hecho.
     */
    public static List<String> convert(Path pack, Path out, Vanilla vanilla, Consumer<String> log) throws IOException {
        PackConverter c = new PackConverter(vanilla, log);
        c.load(pack);
        c.run();
        c.write(out);
        return c.report;
    }

    void load(Path pack) throws IOException {
        Map<String, byte[]> raw = readAll(pack);
        byte[] mm = raw.get("pack.mcmeta");
        if (mm != null) {
            try {
                meta = parseObject(mm);
            } catch (RuntimeException e) {
                meta = new JsonObject();
            }
        }
        JsonObject p = meta.has("pack") && meta.get("pack").isJsonObject() ? meta.getAsJsonObject("pack") : new JsonObject();
        targetFormat = Math.max(TARGET, packMin(p));
        List<String> ov = overlays(meta, targetFormat);
        for (Map.Entry<String, byte[]> e : raw.entrySet()) {
            String n = e.getKey();
            if (junk(n)) {
                continue;
            }
            if (n.startsWith("assets/") || n.equals("pack.png")) {
                files.put(n, e.getValue());
            }
        }
        for (String d : ov) {
            String pre = d + "/assets/";
            for (Map.Entry<String, byte[]> e : raw.entrySet()) {
                String n = e.getKey();
                if (n.startsWith(pre) && !junk(n)) {
                    String rel = n.substring(d.length() + 1);
                    files.put(rel, e.getValue());
                    fromOverlay.add(rel);
                }
            }
        }
        if (!ov.isEmpty()) {
            report.add("capas aplicadas (formato " + targetFormat + "): " + String.join(", ", ov));
        }
    }

    private static boolean junk(String n) {
        String base = n.substring(n.lastIndexOf('/') + 1);
        return n.startsWith("__MACOSX/") || n.contains("/__MACOSX/") || base.equals(".DS_Store") || base.startsWith("._")
                || n.startsWith(".git/") || n.startsWith(".idea/");
    }

    void run() {
        dropUnsupported();
        renames();
        equipment();
        atlases();
        try {
            int n = new SpriteAtlases(this).apply();
            if (n > 0) {
                report.add("interfaz: " + n + " imagenes pasadas a las hojas de 1.20.1");
            }
        } catch (Exception e) {
            log.accept("StarPackPort: fallo la interfaz: " + e);
        }
        try {
            int n = new ItemDefinitions(this).apply();
            if (n > 0) {
                report.add("items: " + n + " modelos convertidos");
            }
        } catch (Exception e) {
            log.accept("StarPackPort: fallo la conversion de items: " + e);
        }
        fixModels();
        // lo que 1.20.1 no lee
        files.keySet().removeIf(k -> k.matches("^assets/[^/]+/(items|equipment)/.*") || k.startsWith("assets/minecraft/textures/gui/sprites/"));
        writeMeta();
    }

    void dropUnsupported() {
        int before = files.size();
        files.keySet().removeIf(k -> k.matches("^assets/[^/]+/(shaders|post_effect)/.*"));
        if (files.size() != before) {
            report.add("shaders descartados: " + (before - files.size()) + " archivos (1.20.1 usa otro sistema)");
        }
    }

    /** Mueve un archivo (y su .mcmeta) si el destino no existe. */
    boolean move(String from, String to) {
        if (!files.containsKey(from)) {
            return false;
        }
        if (!files.containsKey(to)) {
            files.put(to, files.get(from));
            String fm = from + ".mcmeta";
            if (files.containsKey(fm) && !files.containsKey(to + ".mcmeta")) {
                files.put(to + ".mcmeta", files.get(fm));
            }
        }
        files.remove(from);
        files.remove(from + ".mcmeta");
        return true;
    }

    private static final Map<String, String> STEMS = Map.of(
            "short_grass", "grass",
            "iron_chain", "chain",
            "turtle_scute", "scute",
            "elytra_broken", "broken_elytra");
    private static final Pattern STEM_PATH = Pattern.compile(
            "^assets/minecraft/(blockstates|models/block|models/item|items|textures/block|textures/item)/(short_grass|iron_chain|turtle_scute|elytra_broken)(\\.png|\\.json|\\.png\\.mcmeta)$");
    private static final Pattern STEM_REF = Pattern.compile(
            "\"((?:minecraft:)?(?:block|item)/)(short_grass|iron_chain|turtle_scute|elytra_broken)\"");

    void renames() {
        int n = 0;
        for (String k : new ArrayList<>(files.keySet())) {
            Matcher m = STEM_PATH.matcher(k);
            if (m.matches() && !k.endsWith(".mcmeta")) {
                if (move(k, "assets/minecraft/" + m.group(1) + "/" + STEMS.get(m.group(2)) + m.group(3))) {
                    n++;
                }
            }
        }
        for (String k : new ArrayList<>(files.keySet())) {
            if (k.endsWith(".json") && k.matches("^assets/[^/]+/(models|blockstates|items)/.*")) {
                String s = new String(files.get(k), StandardCharsets.UTF_8);
                Matcher m = STEM_REF.matcher(s);
                if (m.find()) {
                    files.put(k, m.replaceAll(r -> Matcher.quoteReplacement("\"" + r.group(1) + STEMS.get(r.group(2)) + "\""))
                            .getBytes(StandardCharsets.UTF_8));
                }
            }
        }
        String e = "assets/minecraft/textures/entity/";
        n += move(e + "chicken/temperate_chicken.png", e + "chicken.png") ? 1 : 0;
        n += move(e + "cow/temperate_cow.png", e + "cow/cow.png") ? 1 : 0;
        n += move(e + "pig/temperate_pig.png", e + "pig/pig.png") ? 1 : 0;
        n += move(e + "sheep/sheep_wool.png", e + "sheep/sheep_fur.png") ? 1 : 0;
        String slot = "assets/minecraft/textures/gui/sprites/container/slot/";
        for (String a : new String[]{"helmet", "chestplate", "leggings", "boots", "shield"}) {
            n += move(slot + a + ".png", "assets/minecraft/textures/item/empty_armor_slot_" + a + ".png") ? 1 : 0;
        }
        for (String a : new String[]{"amethyst_shard", "axe", "diamond", "emerald", "hoe", "ingot", "lapis_lazuli", "pickaxe",
                "quartz", "redstone_dust", "shovel", "sword", "smithing_template_armor_trim", "smithing_template_netherite_upgrade"}) {
            n += move(slot + a + ".png", "assets/minecraft/textures/item/empty_slot_" + a + ".png") ? 1 : 0;
        }
        if (n > 0) {
            report.add("archivos renombrados a los nombres de 1.20.1: " + n);
        }
    }

    private static final Pattern EQUIP = Pattern.compile("^assets/([^/]+)/textures/entity/equipment/([^/]+)/([^/]+)\\.png$");
    private static final Pattern TRIM = Pattern.compile("^assets/([^/]+)/textures/trims/entity/(humanoid|humanoid_leggings)/([^/]+)\\.png$");

    void equipment() {
        int n = 0;
        for (String k : new ArrayList<>(files.keySet())) {
            Matcher m = EQUIP.matcher(k);
            if (m.matches()) {
                String ns = m.group(1);
                String layer = m.group(2);
                String name = m.group(3);
                String base = "assets/" + ns + "/textures/";
                String to = null;
                switch (layer) {
                    case "humanoid", "humanoid_leggings" -> {
                        String suf = layer.equals("humanoid") ? "_layer_1" : "_layer_2";
                        if (name.equals("leather_overlay")) {
                            to = base + "models/armor/leather" + suf + "_overlay.png";
                        } else {
                            to = base + "models/armor/" + (name.equals("turtle_scute") ? "turtle" : name) + suf + ".png";
                        }
                    }
                    case "wings" -> to = name.equals("elytra") ? base + "entity/elytra.png" : null;
                    case "horse_body" -> to = name.endsWith("_overlay") ? null : base + "entity/horse/armor/horse_armor_" + name + ".png";
                    case "llama_body" -> to = base + "entity/llama/decor/" + name + ".png";
                    case "pig_saddle" -> to = base + "entity/pig/pig_saddle.png";
                    case "strider_saddle" -> to = base + "entity/strider/strider_saddle.png";
                    default -> {
                    }
                }
                if (to != null && move(k, to)) {
                    n++;
                }
            }
            Matcher t = TRIM.matcher(k);
            if (t.matches()) {
                String to = "assets/" + t.group(1) + "/textures/trims/models/armor/" + t.group(3)
                        + (t.group(2).equals("humanoid_leggings") ? "_leggings" : "") + ".png";
                if (move(k, to)) {
                    n++;
                }
            }
        }
        if (n > 0) {
            report.add("armaduras/equipo pasados a las rutas de 1.20.1: " + n);
        }
    }

    /** 1.21.11 separa los items en su propio atlas; en 1.20.1 van en el de bloques. */
    void atlases() {
        String items = "assets/minecraft/atlases/items.json";
        if (!files.containsKey(items)) {
            return;
        }
        try {
            JsonObject it = parseObject(files.remove(items));
            String bk = "assets/minecraft/atlases/blocks.json";
            JsonObject blocks = files.containsKey(bk) ? parseObject(files.get(bk)) : new JsonObject();
            JsonArray dst = blocks.has("sources") ? blocks.getAsJsonArray("sources") : new JsonArray();
            if (it.has("sources")) {
                for (JsonElement e : it.getAsJsonArray("sources")) {
                    dst.add(e);
                }
            }
            blocks.add("sources", dst);
            putJson(bk, blocks);
        } catch (RuntimeException e) {
            log.accept("StarPackPort: no pude leer atlases/items.json: " + e);
        }
    }

    /**
     * 1.21.11 deja girar los cubos en cualquier angulo y en varios ejes a la vez ("x", "y", "z").
     * 1.20.1 lee solo "axis" + "angle"; el mod (ModelRotations) acepta cualquier angulo y,
     * para varios ejes, lee "starpackport_xyz".
     */
    void fixModels() {
        int n = 0;
        for (String k : new ArrayList<>(files.keySet())) {
            if (!k.endsWith(".json") || !k.matches("^assets/[^/]+/models/.*")) {
                continue;
            }
            byte[] raw = files.get(k);
            String txt = raw == null ? "" : new String(raw, StandardCharsets.UTF_8);
            if (!txt.contains("\"rotation\"") && !txt.contains("\"light_emission\"")) {
                continue;
            }
            JsonObject o;
            try {
                o = parseObject(raw);
            } catch (RuntimeException e) {
                continue;
            }
            boolean changed = false;
            if (o.has("elements") && o.get("elements").isJsonArray()) {
                for (JsonElement el : o.getAsJsonArray("elements")) {
                    if (!el.isJsonObject()) {
                        continue;
                    }
                    JsonObject r = el.getAsJsonObject().has("rotation") && el.getAsJsonObject().get("rotation").isJsonObject()
                            ? el.getAsJsonObject().getAsJsonObject("rotation") : null;
                    JsonObject eo = el.getAsJsonObject();
                    if (eo.has("light_emission") && eo.get("light_emission").isJsonPrimitive() && !eo.has("forge_data")) {
                        // brillo propio del cubo: Forge 1.20.1 lo lee de "forge_data"
                        int le = eo.get("light_emission").getAsInt();
                        JsonObject fd = new JsonObject();
                        fd.addProperty("block_light", le);
                        fd.addProperty("sky_light", le);
                        eo.add("forge_data", fd);
                        eo.remove("light_emission");
                        changed = true;
                    }
                    if (r == null || r.has("axis")) {
                        continue;
                    }
                    float[] xyz = new float[3];
                    String[] names = {"x", "y", "z"};
                    int nonzero = 0;
                    int first = -1;
                    for (int i = 0; i < 3; i++) {
                        if (r.has(names[i]) && r.get(names[i]).isJsonPrimitive()) {
                            xyz[i] = r.get(names[i]).getAsFloat();
                        }
                        if (xyz[i] != 0) {
                            nonzero++;
                            if (first < 0) {
                                first = i;
                            }
                        }
                        r.remove(names[i]);
                    }
                    if (nonzero == 0) {
                        r.addProperty("axis", "y");
                        r.addProperty("angle", 0);
                    } else if (nonzero == 1) {
                        r.addProperty("axis", names[first]);
                        r.addProperty("angle", xyz[first]);
                    } else {
                        r.addProperty("axis", names[first]);
                        r.addProperty("angle", 0);
                        JsonArray a = new JsonArray();
                        for (float f : xyz) {
                            a.add(f);
                        }
                        r.add("starpackport_xyz", a);
                    }
                    if (!r.has("origin")) {
                        JsonArray origin = new JsonArray();
                        origin.add(8);
                        origin.add(8);
                        origin.add(8);
                        r.add("origin", origin);
                    }
                    changed = true;
                }
            }
            if (changed) {
                putJson(k, o);
                n++;
            }
        }
        if (n > 0) {
            report.add("modelos con giros de 1.21.11 traducidos: " + n);
        }
    }

    void writeMeta() {
        JsonObject out = new JsonObject();
        JsonObject p = new JsonObject();
        p.addProperty("pack_format", TARGET);
        JsonObject old = meta.has("pack") && meta.get("pack").isJsonObject() ? meta.getAsJsonObject("pack") : new JsonObject();
        p.add("description", old.has("description") ? old.get("description") : new com.google.gson.JsonPrimitive(""));
        out.add("pack", p);
        for (String keep : new String[]{"filter", "language"}) {
            if (meta.has(keep)) {
                out.add(keep, meta.get(keep));
            }
        }
        putJson("pack.mcmeta", out);
    }

    void write(Path out) throws IOException {
        Path tmp = out.resolveSibling(out.getFileName() + ".tmp");
        deleteTree(tmp);
        Files.createDirectories(tmp);
        for (Map.Entry<String, byte[]> e : files.entrySet()) {
            Path p = tmp.resolve(e.getKey());
            if (!p.normalize().startsWith(tmp)) {
                continue;
            }
            Files.createDirectories(p.getParent());
            Files.write(p, e.getValue());
        }
        Files.writeString(tmp.resolve("starpackport.txt"),
                "Convertido por StarPackPort (de Starlevi) para Minecraft 1.20.1.\n\n" + String.join("\n", report) + "\n");
        deleteTree(out);
        Files.move(tmp, out);
    }

    static void deleteTree(Path p) throws IOException {
        if (!Files.exists(p)) {
            return;
        }
        try (Stream<Path> s = Files.walk(p)) {
            for (Path q : (Iterable<Path>) s.sorted(Comparator.reverseOrder())::iterator) {
                Files.delete(q);
            }
        }
    }

    // ------------------------------------------------------------------ util

    static JsonObject parseObject(byte[] b) {
        String s = new String(b, StandardCharsets.UTF_8);
        if (s.startsWith("﻿")) {
            s = s.substring(1);
        }
        JsonReader r = new JsonReader(new StringReader(s));
        r.setLenient(true);
        return JsonParser.parseReader(r).getAsJsonObject();
    }

    JsonObject json(String path) {
        byte[] b = files.get(path);
        if (b == null) {
            return null;
        }
        try {
            return parseObject(b);
        } catch (RuntimeException e) {
            return null;
        }
    }

    void putJson(String path, JsonElement o) {
        files.put(path, GSON.toJson(o).getBytes(StandardCharsets.UTF_8));
    }

    Collection<String> keys() {
        return new ArrayList<>(files.keySet());
    }
}
