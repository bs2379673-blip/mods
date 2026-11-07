package com.starlevi.starvillagers.family;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.starlevi.starvillagers.StarVillagers;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Las familias de todo el mundo (aldeanos y jugadores): padres, pareja, esposo/a e hijos. Se guarda
 * en &lt;mundo&gt;/starvillagers/familias.json, asi el arbol sigue aunque un aldeano este lejos o haya muerto.
 */
public final class FamilyStore {
    public static final class Person {
        public String name = "?";
        public boolean female, player, dead;
        public final List<UUID> parents = new ArrayList<>();
        public final List<UUID> children = new ArrayList<>();
        public UUID partner, spouse;
        public long lastChildDay = -100;
    }

    private static final Map<UUID, Person> PEOPLE = new HashMap<>();
    private static Path file;
    private static boolean dirty;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private FamilyStore() {
    }

    public static Person get(UUID id) {
        return PEOPLE.get(id);
    }

    public static Person getOrCreate(UUID id, String name, boolean female, boolean player) {
        Person p = PEOPLE.computeIfAbsent(id, k -> new Person());
        if (name != null && !name.isEmpty() && !name.equals(p.name)) {
            p.name = name;
            dirty = true;
        }
        if (p.female != female || p.player != player) {
            p.female = female;
            p.player = player;
            dirty = true;
        }
        return p;
    }

    public static void changed() {
        dirty = true;
    }

    public static void start(MinecraftServer server) {
        PEOPLE.clear();
        file = server.getWorldPath(LevelResource.ROOT).resolve("starvillagers").resolve("familias.json");
        if (!Files.exists(file)) {
            return;
        }
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            for (var e : root.entrySet()) {
                JsonObject o = e.getValue().getAsJsonObject();
                Person p = new Person();
                p.name = o.has("nombre") ? o.get("nombre").getAsString() : "?";
                p.female = o.has("mujer") && o.get("mujer").getAsBoolean();
                p.player = o.has("jugador") && o.get("jugador").getAsBoolean();
                p.dead = o.has("murio") && o.get("murio").getAsBoolean();
                if (o.has("padres")) {
                    o.getAsJsonArray("padres").forEach(x -> p.parents.add(UUID.fromString(x.getAsString())));
                }
                if (o.has("hijos")) {
                    o.getAsJsonArray("hijos").forEach(x -> p.children.add(UUID.fromString(x.getAsString())));
                }
                if (o.has("pareja")) {
                    p.partner = UUID.fromString(o.get("pareja").getAsString());
                }
                if (o.has("esposo")) {
                    p.spouse = UUID.fromString(o.get("esposo").getAsString());
                }
                if (o.has("ultimo_hijo")) {
                    p.lastChildDay = o.get("ultimo_hijo").getAsLong();
                }
                PEOPLE.put(UUID.fromString(e.getKey()), p);
            }
        } catch (Exception ex) {
            StarVillagers.LOGGER.warn("StarVillagers: no pude leer familias.json: {}", ex.toString());
        }
    }

    public static void save() {
        if (!dirty || file == null) {
            return;
        }
        dirty = false;
        JsonObject root = new JsonObject();
        for (var e : PEOPLE.entrySet()) {
            Person p = e.getValue();
            JsonObject o = new JsonObject();
            o.addProperty("nombre", p.name);
            o.addProperty("mujer", p.female);
            if (p.player) {
                o.addProperty("jugador", true);
            }
            if (p.dead) {
                o.addProperty("murio", true);
            }
            JsonArray pa = new JsonArray();
            p.parents.forEach(x -> pa.add(x.toString()));
            o.add("padres", pa);
            JsonArray ch = new JsonArray();
            p.children.forEach(x -> ch.add(x.toString()));
            o.add("hijos", ch);
            if (p.partner != null) {
                o.addProperty("pareja", p.partner.toString());
            }
            if (p.spouse != null) {
                o.addProperty("esposo", p.spouse.toString());
            }
            o.addProperty("ultimo_hijo", p.lastChildDay);
            root.add(e.getKey().toString(), o);
        }
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            StarVillagers.LOGGER.warn("StarVillagers: no pude guardar familias.json: {}", ex.toString());
        }
    }

    public static void stop() {
        save();
        PEOPLE.clear();
        file = null;
    }
}
