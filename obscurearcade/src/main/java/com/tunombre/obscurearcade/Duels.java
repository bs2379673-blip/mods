package com.tunombre.obscurearcade;

import com.cotii.cubosu.api.CubOsuResult;
import com.cotii.cubosu.api.CubOsuServerApi;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** Gestor de duelos activos (uno por consola). */
public final class Duels {
    private static final Map<String, Duel> BY_POS = new HashMap<>();
    private static final Map<UUID, Duel> BY_PLAYER = new HashMap<>();

    private Duels() { }

    private static String key(ServerLevel l, BlockPos p) { return l.dimension().location() + "@" + p.asLong(); }

    public static boolean inDuel(UUID id) { return BY_PLAYER.containsKey(id); }

    private static void say(ServerPlayer p, String s) { p.displayClientMessage(Component.literal(s), true); }

    /** un jugador usa la consola: el primero espera, el segundo inicia la cuenta atras */
    public static void use(ServerPlayer p, ServerLevel level, VersusEntity be) {
        if (be.song1.isBlank()) { say(p, "Esta consola no tiene canción (configúrala con la Llave Arcade)"); return; }
        if (!CubOsuServerApi.hasMod(p)) { say(p, "Necesitas tener CubOsu! instalado para jugar"); return; }
        Duel mine = BY_PLAYER.get(p.getUUID());
        if (mine != null) {
            if (mine.phase == Duel.Phase.WAIT) { mine.forget(p.getUUID()); say(p, "Saliste de la espera"); }
            else say(p, "Ya estás en un duelo");
            return;
        }
        if (Arcade.isPlaying(p.getUUID())) { say(p, "Ya estás jugando en una máquina"); return; }
        String k = key(level, be.getBlockPos());
        Duel d = BY_POS.get(k);
        if (d == null) {
            d = new Duel(level, be, p);
            BY_POS.put(k, d);
            BY_PLAYER.put(p.getUUID(), d);
            say(p, "Esperando rival… otra persona debe usar la consola");
            for (ServerPlayer o : level.players()) if (o != p && o.blockPosition().distSqr(be.getBlockPos()) < 30 * 30)
                o.sendSystemMessage(Component.literal(p.getGameProfile().getName() + " busca rival en la consola de duelo"));
        } else if (d.phase == Duel.Phase.WAIT) {
            if (d.joinSecond(p)) BY_PLAYER.put(p.getUUID(), d);
        } else say(p, "Hay un duelo en curso");
    }

    /** inicia un duelo entre dos jugadores en la consola mas cercana al primero (para comandos y eventos) */
    public static String start(ServerPlayer a, ServerPlayer b) {
        ServerLevel level = a.serverLevel();
        VersusEntity best = null;
        double bd = 64 * 64;
        BlockPos c = a.blockPosition();
        for (int cx = (c.getX() >> 4) - 4; cx <= (c.getX() >> 4) + 4; cx++) {
            for (int cz = (c.getZ() >> 4) - 4; cz <= (c.getZ() >> 4) + 4; cz++) {
                if (!level.hasChunk(cx, cz)) continue;
                for (var e : level.getChunk(cx, cz).getBlockEntities().values()) {
                    if (e instanceof VersusEntity v) {
                        double d = v.getBlockPos().distSqr(c);
                        if (d < bd) { bd = d; best = v; }
                    }
                }
            }
        }
        if (best == null) return "No hay ninguna consola de duelo cerca de " + a.getGameProfile().getName();
        if (inDuel(a.getUUID()) || inDuel(b.getUUID())) return "Alguno ya está en un duelo";
        if (!CubOsuServerApi.hasMod(a) || !CubOsuServerApi.hasMod(b)) return "Ambos necesitan CubOsu! instalado";
        if (best.song1.isBlank()) return "La consola no tiene canción";
        Duel d = new Duel(level, best, a);
        BY_POS.put(key(level, best.getBlockPos()), d);
        BY_PLAYER.put(a.getUUID(), d);
        if (d.joinSecond(b)) BY_PLAYER.put(b.getUUID(), d);
        return "Duelo iniciado: " + a.getGameProfile().getName() + " vs " + b.getGameProfile().getName();
    }

    public static void tick(MinecraftServer server) {
        if (BY_POS.isEmpty()) return;
        for (Iterator<Map.Entry<String, Duel>> it = BY_POS.entrySet().iterator(); it.hasNext(); ) {
            Duel d = it.next().getValue();
            boolean keep;
            try { keep = d.tick(); } catch (RuntimeException e) { d.forceCancel(); keep = false; }
            if (!keep) {
                it.remove();
                BY_PLAYER.values().removeIf(x -> x == d);
            }
        }
    }

    public static void abort(UUID id) {
        Duel d = BY_PLAYER.get(id);
        if (d != null) d.forget(id);
    }

    public static void cancelAll() {
        for (Duel d : new ArrayList<>(BY_POS.values())) d.forceCancel();
    }

    public static void clear() {
        BY_POS.clear();
        BY_PLAYER.clear();
    }

    public static void onFinish(ServerPlayer p, CubOsuResult r) {
        Duel d = BY_PLAYER.get(p.getUUID());
        if (d != null) d.onFinish(p, r);
    }

    public static void onPacket(ServerPlayer p, String kind, String json) {
        switch (kind) {
            case "note" -> {
                Duel d = BY_PLAYER.get(p.getUUID());
                if (d == null) return;
                try {
                    int i = json.indexOf(',');
                    d.onNote(p, Integer.parseInt(json.substring(0, i).trim()), Integer.parseInt(json.substring(i + 1).trim()));
                } catch (RuntimeException ignored) { }
            }
            case "set", "act" -> Config.handle(p, kind, json);
            default -> { }
        }
    }
}
