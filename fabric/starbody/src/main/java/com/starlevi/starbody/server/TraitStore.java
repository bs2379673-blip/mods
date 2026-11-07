package com.starlevi.starbody.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.starlevi.starbody.StarBody;
import com.starlevi.starbody.net.Net;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Los rasgos de cada skin en este mundo (&lt;mundo&gt;/starbody_rasgos.json). Se mandan a cada jugador al
 * entrar y a todos cuando alguien cambia los suyos.
 */
public final class TraitStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, String> BY_SKIN = new LinkedHashMap<>();
    private static MinecraftServer server;

    private TraitStore() {
    }

    private static Path file() {
        return server.getWorldPath(LevelResource.ROOT).resolve("starbody_rasgos.json");
    }

    public static void start(MinecraftServer s) {
        server = s;
        BY_SKIN.clear();
        try {
            if (Files.isRegularFile(file())) {
                Map<String, String> m = GSON.fromJson(Files.readString(file(), StandardCharsets.UTF_8), new TypeToken<Map<String, String>>() {
                }.getType());
                if (m != null) {
                    BY_SKIN.putAll(m);
                }
            }
        } catch (Exception e) {
            StarBody.LOGGER.warn("StarBody: no pude leer los rasgos: {}", e.toString());
        }
    }

    public static void stop() {
        server = null;
        BY_SKIN.clear();
    }

    private static void save() {
        if (server == null) {
            return;
        }
        try {
            Files.writeString(file(), GSON.toJson(BY_SKIN), StandardCharsets.UTF_8);
        } catch (Exception e) {
            StarBody.LOGGER.warn("StarBody: no pude guardar los rasgos: {}", e.toString());
        }
    }

    public static void join(ServerPlayer p) {
        for (Map.Entry<String, String> e : BY_SKIN.entrySet()) {
            ServerPlayNetworking.send(p, new Net.SkinTraits(e.getKey(), Base64.getDecoder().decode(e.getValue())));
        }
    }

    public static void set(ServerPlayer from, String skin, byte[] data) {
        if (skin.isBlank() || skin.length() > 200 || data.length > 1024) {
            return;
        }
        if (data.length == 0) {
            BY_SKIN.remove(skin);
        } else {
            BY_SKIN.put(skin, Base64.getEncoder().encodeToString(data));
        }
        save();
        Net.SkinTraits msg = new Net.SkinTraits(skin, data);
        for (ServerPlayer p : from.level().getServer().getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(p, msg);
        }
    }
}
