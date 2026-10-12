package com.tunombre.spawncontrol;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Almacen de reglas (config/spawncontrol/rules.json). Se lee desde cualquier hilo (la generacion de chunks es asincrona). */
public final class Rules {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, Rule> RULES = new ConcurrentHashMap<>();

    private Rules() { }

    public static Map<String, Rule> all() { return RULES; }

    static Path file() { return FMLPaths.CONFIGDIR.get().resolve("spawncontrol").resolve("rules.json"); }

    public static synchronized void load() {
        RULES.clear();
        Path f = file();
        if (!Files.isRegularFile(f)) return;
        try (Reader r = Files.newBufferedReader(f)) {
            Map<String, Rule> m = GSON.fromJson(r, new TypeToken<Map<String, Rule>>() { }.getType());
            if (m != null) m.forEach((k, v) -> { if (v != null) { v.fix(); RULES.put(k, v); } });
        } catch (Exception e) {
            SpawnControl.LOG.error("No se pudo leer {}", f, e);
        }
    }

    public static synchronized void save() {
        Path f = file();
        try {
            Files.createDirectories(f.getParent());
            Path tmp = f.resolveSibling("rules.json.tmp");
            try (Writer w = Files.newBufferedWriter(tmp)) { GSON.toJson(new java.util.TreeMap<>(RULES), w); }
            Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            SpawnControl.LOG.error("No se pudo guardar {}", f, e);
        }
    }

    /** clave de la regla que manda para este tipo: exacta > "mod:*" > "*" */
    public static Rule find(EntityType<?> t) {
        if (RULES.isEmpty()) return null;
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(t);
        if (id == null) return null;
        Rule r = RULES.get(id.toString());
        if (r == null) r = RULES.get(id.getNamespace() + ":*");
        if (r == null) r = RULES.get("*");
        return r;
    }

    public static Rule getOrCreate(String key) { return RULES.computeIfAbsent(key, k -> new Rule()); }

    /** "todos:todos" -> "*", "mod:todos" -> "mod:*", cualquier otro -> tal cual */
    public static String key(ResourceLocation id) {
        if (id.getPath().equals("todos")) return id.getNamespace().equals("todos") ? "*" : id.getNamespace() + ":*";
        return id.toString();
    }

    /** lo inverso, para escribirlo en comandos */
    public static String cmdId(String key) {
        if (key.equals("*")) return "todos:todos";
        if (key.endsWith(":*")) return key.substring(0, key.length() - 1) + "todos";
        return key;
    }

    public static boolean validKey(String key) {
        if (key.equals("*")) return true;
        if (key.endsWith(":*")) {
            String ns = key.substring(0, key.length() - 2);
            for (ResourceLocation id : ForgeRegistries.ENTITY_TYPES.getKeys()) if (id.getNamespace().equals(ns)) return true;
            return false;
        }
        ResourceLocation id = ResourceLocation.tryParse(key);
        return id != null && ForgeRegistries.ENTITY_TYPES.containsKey(id);
    }

    /** decide si un spawn de este tipo, en este punto, esta permitido */
    public static boolean allowed(EntityType<?> type, String spawnType, String dim, Vec3 pos) {
        Rule r = find(type);
        if (r == null || !r.types.contains(spawnType)) return true;
        if (!r.enabled) return false;
        boolean inAllow = false;
        for (Zone z : r.zones) {
            if (!z.contains(dim, pos)) continue;
            if (!z.allow) return false;
            inAllow = true;
        }
        if (r.onlyZones && !inAllow) return false;
        if (r.max > 0 && r.alive.get() >= r.max) return false;
        if (r.prob < 100 && Math.random() * 100.0 >= r.prob) return false;
        if (r.max > 0) r.alive.incrementAndGet();
        return true;
    }
}
