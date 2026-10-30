package com.annis.npc.link;

import com.annis.npc.net.EmoteSyncPayload;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Emotes de Punchy Moves para todos: el cliente avisa que emote lanzo y el servidor lo pone como
 * animacion de Annis en ese jugador (asi lo ven los demas, aunque Punchy no tenga red). Los que se
 * repiten terminan al moverse.
 */
public final class EmoteSync {
    private record Running(String anim, Vec3 at, long until) {
    }

    private static final Map<UUID, Running> RUNNING = new HashMap<>();

    private EmoteSync() {
    }

    public static void init() {
        ServerPlayNetworking.registerGlobalReceiver(EmoteSyncPayload.ID, (p, ctx) -> start(ctx.player(), p.anim(), p.ticks()));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            Iterator<Map.Entry<UUID, Running>> it = RUNNING.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, Running> en = it.next();
                ServerPlayer pl = server.getPlayerList().getPlayer(en.getKey());
                Running r = en.getValue();
                if (pl == null) {
                    it.remove();
                    continue;
                }
                boolean mine = r.anim().equals(EntityLinks.currentAnim(pl));
                boolean moved = pl.position().distanceToSqr(r.at()) > 0.15 * 0.15;
                boolean over = r.until() > 0 && pl.level().getGameTime() >= r.until();
                if (!mine) {
                    it.remove();
                } else if (moved || over) {
                    EntityLinks.stop(pl);
                    it.remove();
                }
            }
        });
    }

    private static void start(ServerPlayer player, String anim, int ticks) {
        if (anim == null || anim.length() > 160 || !anim.startsWith("punchy:")) {
            return;
        }
        // durante una interaccion (abrazo, baile...) no se pisa
        String current = EntityLinks.currentAnim(player);
        if (!current.isEmpty() && !current.startsWith("punchy:")) {
            return;
        }
        int hold = Math.max(0, Math.min(20 * 60 * 5, ticks));
        EntityLinks.play(player, anim, hold);
        RUNNING.put(player.getUUID(), new Running(anim, player.position(), hold > 0 ? player.level().getGameTime() + hold : 0));
    }
}
