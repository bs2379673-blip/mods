package com.annis.interact;

import com.annis.interact.net.CustomInteractionsPayload;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Interacciones creadas sin programar, con animaciones de Punchy Studio (o de Annis):
 * config/annisinteract_extra.json. El servidor las lee y se las manda a los clientes (para la rueda).
 *
 * Cada interaccion:
 *   id, nombre, yo (animacion de quien la pide), otro (animacion del otro),
 *   lugar: "frente" | "derecha" | "encima" (el otro va encima de quien la pide) | "cama",
 *   distancia (frente/derecha, bloques), adelante / arriba / izquierda (encima, bloques),
 *   segundos (0 = hasta que alguien pulse Shift), romantica, corazones (MCA), activa.
 */
public final class CustomInteractions {
    private static String json = "{}";

    private CustomInteractions() {
    }

    public static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("annisinteract_extra.json");
    }

    private static final String EXAMPLE = """
            {
              "interacciones": [
                {
                  "id": "ejemplo_saludo",
                  "nombre": "Saludo (ejemplo)",
                  "yo": "punchy:annis_chocar_cinco",
                  "otro": "punchy:annis_chocar_cinco",
                  "lugar": "frente",
                  "distancia": 0.8,
                  "segundos": 2.25,
                  "romantica": false,
                  "corazones": 0,
                  "activa": false
                },
                {
                  "id": "ejemplo_hombros",
                  "nombre": "Llevar en los hombros (ejemplo)",
                  "yo": "punchy:mi_carga_hombros",
                  "otro": "punchy:mi_va_en_hombros",
                  "lugar": "encima",
                  "adelante": 0.0,
                  "arriba": 1.05,
                  "izquierda": 0.0,
                  "segundos": 0,
                  "romantica": false,
                  "corazones": 20,
                  "activa": false
                }
              ]
            }
            """;

    /** Servidor: lee el archivo (o crea uno de ejemplo) y lo aplica. */
    public static void load() {
        String text = EXAMPLE;
        try {
            if (Files.isRegularFile(file())) {
                text = Files.readString(file());
            } else {
                Files.writeString(file(), EXAMPLE);
            }
        } catch (Exception e) {
            AnnisInteract.LOGGER.warn("No se pudo leer {}: {}", file(), e.toString());
        }
        int n = apply(text);
        json = text;
        AnnisInteract.LOGGER.info("Interacciones por archivo: {}", n);
    }

    /** Lee el texto y registra las interacciones activas (servidor y cliente). Devuelve cuantas. */
    public static int apply(String text) {
        List<Interaction> list = new ArrayList<>();
        Map<String, Float> dist = new HashMap<>();
        CarryPose.CUSTOM.clear();
        try {
            JsonElement root = JsonParser.parseString(text);
            JsonArray arr = root.isJsonObject() && root.getAsJsonObject().has("interacciones")
                    ? root.getAsJsonObject().getAsJsonArray("interacciones") : new JsonArray();
            for (JsonElement e : arr) {
                if (!e.isJsonObject()) {
                    continue;
                }
                JsonObject o = e.getAsJsonObject();
                if (o.has("activa") && !o.get("activa").getAsBoolean()) {
                    continue;
                }
                String id = str(o, "id", "").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
                String yo = str(o, "yo", ""), otro = str(o, "otro", "");
                if (id.isEmpty() || yo.isEmpty() || otro.isEmpty()) {
                    continue;
                }
                String name = str(o, "nombre", id);
                String lugar = str(o, "lugar", "frente").toLowerCase(Locale.ROOT);
                float secs = num(o, "segundos", 0);
                int ticks = Math.round(secs * 20);
                boolean romantic = o.has("romantica") && o.get("romantica").getAsBoolean();
                int hearts = (int) num(o, "corazones", 0);
                Interaction.Place place;
                Interaction.Kind kind = ticks > 0 ? Interaction.Kind.SCENE : Interaction.Kind.LOOP;
                switch (lugar) {
                    case "derecha" -> place = Interaction.Place.RIGHT;
                    case "encima" -> {
                        place = Interaction.Place.CARRY_FRONT;
                        kind = Interaction.Kind.CARRY;
                        CarryPose.CUSTOM.put(otro, new float[]{num(o, "adelante", 0), num(o, "arriba", 1.0f), num(o, "izquierda", 0)});
                    }
                    case "cama" -> place = Interaction.Place.BED;
                    default -> place = Interaction.Place.FACE;
                }
                list.add(new Interaction(id, name, yo, otro, place, ticks, kind, romantic, hearts, 1, 3, false));
                dist.put(id, num(o, "distancia", 0.8f));
            }
        } catch (Exception ex) {
            AnnisInteract.LOGGER.warn("Error en las interacciones por archivo: {}", ex.toString());
        }
        Interaction.setCustom(list, dist);
        return list.size();
    }

    private static String str(JsonObject o, String k, String def) {
        return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsString() : def;
    }

    private static float num(JsonObject o, String k, float def) {
        try {
            return o.has(k) ? o.get(k).getAsFloat() : def;
        } catch (Exception e) {
            return def;
        }
    }

    public static void sendTo(ServerPlayer p) {
        ServerPlayNetworking.send(p, new CustomInteractionsPayload(json));
    }

    public static void sendAll(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            sendTo(p);
        }
    }
}
