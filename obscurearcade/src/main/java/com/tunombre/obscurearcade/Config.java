package com.tunombre.obscurearcade;

import com.cotii.cubosu.api.CubOsuServerApi;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.List;

/** Configuracion de maquinas y consolas desde la Llave Arcade (todo valida en el servidor, solo operadores). */
public final class Config {
    private Config() { }

    private static String str(JsonObject o, String k) { return o.has(k) ? o.get(k).getAsString() : ""; }

    public static void open(ServerPlayer p, BlockPos pos) {
        BlockEntity be = p.level().getBlockEntity(pos);
        JsonObject o = new JsonObject();
        o.addProperty("x", pos.getX());
        o.addProperty("y", pos.getY());
        o.addProperty("z", pos.getZ());
        JsonArray songs = new JsonArray();
        try {
            List<String> l = CubOsuServerApi.songs();
            for (int i = 0; i < Math.min(300, l.size()); i++) songs.add(l.get(i));
        } catch (RuntimeException ignored) { }
        o.add("songs", songs);
        JsonObject c = new JsonObject();
        if (be instanceof ArcadeEntity a) {
            o.addProperty("type", "arcade");
            c.addProperty("song", a.song);
            c.addProperty("cost_item", a.costItem);
            c.addProperty("cost_count", a.costCount);
            c.addProperty("reward_cmd", a.rewardCmd);
            c.addProperty("reward_rank", a.rewardRank);
        } else if (be instanceof VersusEntity v) {
            o.addProperty("type", "versus");
            c.addProperty("song1", v.song1);
            c.addProperty("song2", v.song2);
            c.addProperty("style", v.style);
            c.addProperty("end_cmd", v.endCmd);
            JsonArray st = new JsonArray();
            for (String s : Styles.ids()) st.add(s);
            o.add("styles", st);
        } else return;
        o.add("cfg", c);
        Net.toClient(p, "cfg", o.toString());
    }

    static void handle(ServerPlayer p, String kind, String json) {
        if (!p.hasPermissions(2)) return;
        JsonObject o;
        try { o = JsonParser.parseString(json).getAsJsonObject(); } catch (RuntimeException e) { return; }
        BlockPos pos = new BlockPos(o.get("x").getAsInt(), o.get("y").getAsInt(), o.get("z").getAsInt());
        if (p.blockPosition().distSqr(pos) > 24 * 24) return;
        BlockEntity be = p.level().getBlockEntity(pos);
        if (kind.equals("set")) {
            String k = str(o, "k"), v = str(o, "v");
            if (be instanceof ArcadeEntity a) {
                switch (k) {
                    case "song" -> a.song = v;
                    case "cost_item" -> a.costItem = v;
                    case "cost_count" -> { try { a.costCount = Math.max(0, Math.min(64, Integer.parseInt(v.trim()))); } catch (NumberFormatException ignored) { } }
                    case "reward_cmd" -> a.rewardCmd = v;
                    case "reward_rank" -> a.rewardRank = v;
                    default -> { return; }
                }
                a.setChanged();
            } else if (be instanceof VersusEntity c) {
                switch (k) {
                    case "song1" -> c.song1 = v;
                    case "song2" -> c.song2 = v;
                    case "style" -> c.style = v.isBlank() ? "pelea" : v;
                    case "end_cmd" -> c.endCmd = v;
                    default -> { return; }
                }
                c.setChanged();
            }
        } else if (kind.equals("act")) {
            String op = str(o, "op");
            if (be instanceof ArcadeEntity a && op.equals("reset_top")) {
                a.top.clear();
                a.setChanged();
                p.displayClientMessage(Component.literal("Ranking reiniciado"), true);
            } else if (be instanceof VersusEntity c) {
                switch (op) {
                    case "spot1", "spot2" -> {
                        int i = op.equals("spot1") ? 0 : 1;
                        c.hasSpot[i] = true;
                        c.sx[i] = p.getX();
                        c.sy[i] = p.getY();
                        c.sz[i] = p.getZ();
                        c.setChanged();
                        p.displayClientMessage(Component.literal("Puesto del jugador " + (i + 1) + " fijado donde estás"), true);
                    }
                    case "reset_spots" -> { c.hasSpot[0] = c.hasSpot[1] = false; c.setChanged(); p.displayClientMessage(Component.literal("Puestos restablecidos"), true); }
                    case "cancel" -> { Duels.cancelAll(); p.displayClientMessage(Component.literal("Duelos cancelados"), true); }
                    default -> { }
                }
            }
        }
    }
}
