package com.starlevi.starfest.server;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.starlevi.starfest.StarFest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.storage.LevelResource;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Bailarines: NPC de StarScenes, aldeanos de StarVillagers o cualquier cosa que anime StarScenes. Bailan
 * (con la animacion que se les ponga) mientras suena musica de StarFest donde estan, y vuelven a lo suyo
 * cuando se corta. Se guardan en el mundo (starfest_bailarines.json).
 */
public final class Dancers {
    public static final boolean SCENES = FabricLoader.getInstance().isModLoaded("starscenes");
    private static final Map<UUID, String> DANCERS = new LinkedHashMap<>();
    private static final Set<UUID> DANCING = new HashSet<>();
    private static final Map<UUID, Long> REFRESH = new HashMap<>();
    private static Path file;
    private static MethodHandle play;

    private Dancers() {
    }

    private static void anim(Entity e, String anim) {
        if (!SCENES) {
            return;
        }
        try {
            if (play == null) {
                Class<?> api = Class.forName("com.starlevi.starscenes.api.StarScenesAPI");
                play = MethodHandles.publicLookup().findStatic(api, "play", MethodType.methodType(void.class, Entity.class, String.class));
            }
            play.invoke(e, anim);
        } catch (Throwable t) {
            StarFest.LOGGER.debug("StarFest: no pude animar a {}: {}", e, t.toString());
        }
    }

    public static void load(MinecraftServer s) {
        DANCERS.clear();
        DANCING.clear();
        file = s.getWorldPath(LevelResource.ROOT).resolve("starfest_bailarines.json");
        try {
            if (Files.exists(file)) {
                JsonObject o = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
                for (var e : o.entrySet()) {
                    DANCERS.put(UUID.fromString(e.getKey()), e.getValue().getAsString());
                }
            }
        } catch (Exception e) {
            StarFest.LOGGER.warn("StarFest: no pude leer los bailarines: {}", e.toString());
        }
    }

    private static void save() {
        if (file == null) {
            return;
        }
        JsonObject o = new JsonObject();
        DANCERS.forEach((k, v) -> o.addProperty(k.toString(), v));
        try {
            Files.writeString(file, o.toString(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            StarFest.LOGGER.warn("StarFest: no pude guardar los bailarines: {}", e.toString());
        }
    }

    public static Map<UUID, String> all() {
        return DANCERS;
    }

    public static void add(Entity e, String anim) {
        DANCERS.put(e.getUUID(), anim);
        save();
    }

    public static boolean remove(MinecraftServer s, Entity e) {
        boolean had = DANCERS.remove(e.getUUID()) != null;
        if (had) {
            save();
            if (DANCING.remove(e.getUUID())) {
                anim(e, "idle");
            }
        }
        return had;
    }

    public static int clear(MinecraftServer s) {
        int n = DANCERS.size();
        for (UUID id : DANCERS.keySet()) {
            Entity e = find(s, id);
            if (e != null && DANCING.contains(id)) {
                anim(e, "idle");
            }
        }
        DANCERS.clear();
        DANCING.clear();
        save();
        return n;
    }

    private static Entity find(MinecraftServer s, UUID id) {
        for (ServerLevel l : s.getAllLevels()) {
            Entity e = l.getEntity(id);
            if (e != null) {
                return e;
            }
        }
        return null;
    }

    public static void tick(MinecraftServer s) {
        if (!SCENES || DANCERS.isEmpty()) {
            return;
        }
        long now = s.overworld().getGameTime();
        for (Map.Entry<UUID, String> en : DANCERS.entrySet()) {
            Entity e = find(s, en.getKey());
            if (e == null || !e.isAlive() || e instanceof LivingEntity le && le.isSleeping()) {
                continue;
            }
            boolean music = ShowServer.playingAt(e.level().dimension(), e.blockPosition()) != null;
            UUID id = en.getKey();
            if (music) {
                // se repite cada tanto: las animaciones en ronda de StarScenes se van si nadie las renueva
                Long last = REFRESH.get(id);
                if (!DANCING.contains(id) || last == null || now - last > 40) {
                    anim(e, en.getValue());
                    DANCING.add(id);
                    REFRESH.put(id, now);
                }
            } else if (DANCING.remove(id)) {
                anim(e, "idle");
            }
        }
    }
}
