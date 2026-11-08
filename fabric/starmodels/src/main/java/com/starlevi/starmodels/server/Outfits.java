package com.starlevi.starmodels.server;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.starlevi.starmodels.StarModels;
import com.starlevi.starmodels.net.Net;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * El atuendo de cada jugador (se guarda en el jugador y sobrevive a la muerte): un json con el personaje
 * base, los accesorios que se saco ("sin"), los que se puso de otros personajes ("con", con su ajuste) y
 * si va con armadura de Minecraft.
 */
public final class Outfits {
    public static final AttachmentType<String> OUTFIT = AttachmentRegistry.create(Net.id("atuendo"),
            b -> b.persistent(Codec.STRING).copyOnDeath().initializer(() -> ""));

    private Outfits() {
    }

    public static void init() {
        // carga la clase (registra el dato del jugador)
    }

    public static String of(ServerPlayer p) {
        String s = p.getAttached(OUTFIT);
        return s == null ? "" : s;
    }

    /** El personaje base de un atuendo ("" si no tiene). */
    public static String base(String json) {
        if (json == null || json.isBlank()) {
            return "";
        }
        try {
            JsonObject o = JsonParser.parseString(json).getAsJsonObject();
            return o.has("base") ? o.get("base").getAsString() : "";
        } catch (Exception e) {
            return "";
        }
    }

    public static void set(ServerPlayer p, String json) {
        p.setAttached(OUTFIT, json == null ? "" : json);
        Net.Outfit msg = new Net.Outfit(p.getUUID(), of(p));
        for (ServerPlayer o : PlayerLookup.all(p.level().getServer())) {
            ServerPlayNetworking.send(o, msg);
        }
    }

    /** Desde el workshop del jugador. */
    public static void choose(ServerPlayer p, String json) {
        if (json.length() > 30000) {
            return;
        }
        String base = base(json);
        if (!json.isBlank() && (base.isEmpty() || !AvatarLibrary.exists(base))) {
            p.displayClientMessage(Component.literal("No existe el personaje " + base), true);
            return;
        }
        set(p, json.isBlank() ? "" : json);
    }

    /** Solo el personaje, con todos sus accesorios. */
    public static String plain(String avatar) {
        if (avatar.isEmpty()) {
            return "";
        }
        JsonObject o = new JsonObject();
        o.addProperty("base", avatar);
        return o.toString();
    }

    /** Al entrar: la lista de personajes y el atuendo de cada uno de los que estan conectados. */
    public static void join(ServerPlayer p) {
        ServerPlayNetworking.send(p, new Net.Index(AvatarLibrary.all()));
        for (ServerPlayer o : PlayerLookup.all(p.level().getServer())) {
            String a = of(o);
            if (!a.isEmpty()) {
                ServerPlayNetworking.send(p, new Net.Outfit(o.getUUID(), a));
            }
        }
        String mine = of(p);
        if (!mine.isEmpty()) {
            Net.Outfit msg = new Net.Outfit(p.getUUID(), mine);
            for (ServerPlayer o : PlayerLookup.all(p.level().getServer())) {
                ServerPlayNetworking.send(o, msg);
            }
        }
        StarModels.LOGGER.debug("StarModels: {} entro con {}", p.getName().getString(), base(mine));
    }
}
