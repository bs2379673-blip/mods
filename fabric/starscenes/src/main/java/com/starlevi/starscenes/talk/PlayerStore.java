package com.starlevi.starscenes.talk;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.starlevi.starscenes.StarScenes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Lo que StarScenes recuerda de cada jugador en este mundo: marcas, numeros, misiones (en curso con su
 * avance, y terminadas) y compras con limite. Un archivo por jugador en
 * &lt;mundo&gt;/starscenes/jugadores/&lt;uuid&gt;.json.
 */
public final class PlayerStore {
    public static final class Data {
        public Set<String> flags = new LinkedHashSet<>();
        public Map<String, Integer> vars = new LinkedHashMap<>();
        /** Mision en curso -> avance de cada objetivo. */
        public Map<String, int[]> quests = new LinkedHashMap<>();
        /** Mision terminada -> cuantas veces. */
        public Map<String, Integer> done = new LinkedHashMap<>();
        /** "tienda#oferta" -> cuantas compro. */
        public Map<String, Integer> bought = new LinkedHashMap<>();
        transient boolean dirty;
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<UUID, Data> CACHE = new HashMap<>();
    private static MinecraftServer server;
    private static int ticks;

    private PlayerStore() {
    }

    public static void start(MinecraftServer s) {
        server = s;
        CACHE.clear();
    }

    public static void stop() {
        saveAll();
        CACHE.clear();
        server = null;
    }

    private static Path file(UUID id) {
        return server.getWorldPath(LevelResource.ROOT).resolve("starscenes").resolve("jugadores").resolve(id + ".json");
    }

    public static Data of(ServerPlayer p) {
        return CACHE.computeIfAbsent(p.getUUID(), PlayerStore::load);
    }

    private static Data load(UUID id) {
        if (server != null) {
            Path f = file(id);
            if (Files.isRegularFile(f)) {
                try {
                    Data d = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), Data.class);
                    if (d != null) {
                        if (d.flags == null) d.flags = new LinkedHashSet<>();
                        if (d.vars == null) d.vars = new LinkedHashMap<>();
                        if (d.quests == null) d.quests = new LinkedHashMap<>();
                        if (d.done == null) d.done = new LinkedHashMap<>();
                        if (d.bought == null) d.bought = new LinkedHashMap<>();
                        return d;
                    }
                } catch (Exception e) {
                    StarScenes.LOGGER.warn("StarScenes: no pude leer {}: {}", f, e.toString());
                }
            }
        }
        return new Data();
    }

    public static void changed(ServerPlayer p) {
        of(p).dirty = true;
        Quests.dirty(p);
    }

    public static void setFlag(ServerPlayer p, String name, boolean on) {
        Data d = of(p);
        if (on ? d.flags.add(name) : d.flags.remove(name)) {
            changed(p);
        }
    }

    public static void setVar(ServerPlayer p, String name, int value) {
        of(p).vars.put(name, value);
        changed(p);
    }

    public static void tick(MinecraftServer s) {
        if (++ticks % 100 == 0) {
            saveAll();
        }
    }

    public static void left(ServerPlayer p) {
        Data d = CACHE.remove(p.getUUID());
        if (d != null && d.dirty) {
            save(p.getUUID(), d);
        }
    }

    private static void saveAll() {
        if (server == null) {
            return;
        }
        for (Map.Entry<UUID, Data> e : CACHE.entrySet()) {
            if (e.getValue().dirty) {
                save(e.getKey(), e.getValue());
            }
        }
    }

    private static void save(UUID id, Data d) {
        if (server == null) {
            return;
        }
        try {
            Path f = file(id);
            Files.createDirectories(f.getParent());
            Files.writeString(f, GSON.toJson(d), StandardCharsets.UTF_8);
            d.dirty = false;
        } catch (Exception e) {
            StarScenes.LOGGER.warn("StarScenes: no pude guardar los datos de {}: {}", id, e.toString());
        }
    }
}
