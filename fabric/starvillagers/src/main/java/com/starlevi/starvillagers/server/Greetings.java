package com.starlevi.starvillagers.server;

import com.starlevi.starscenes.api.StarScenesAPI;
import com.starlevi.starvillagers.Identity;
import com.starlevi.starvillagers.StarVillagers;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Los saludos de pasada: cuando pasas al lado de un aldeano te mira, te dice algo en un globito y
 * te saluda con la mano; y cuando dos aldeanos se cruzan, se saludan y se contestan.
 */
public final class Greetings {
    private static final String[] TO_PLAYER = {"¡Oh, hola %jugador%!", "¡Hola, %jugador%!", "¡Buenas, %jugador%!", "¡Eh, %jugador%! ¿Todo bien?",
            "¡Qué bueno verte, %jugador%!", "¡Hola! ¿Cómo andás?"};
    private static final String[] MORNING = {"¡Buen día, %jugador%!", "¡Buen día! Lindo día, ¿no?"};
    private static final String[] AFTERNOON = {"¡Buenas tardes, %jugador%!"};
    private static final String[] NIGHT = {"Buenas noches, %jugador%...", "¡Cuidado afuera de noche!", "Ya es tarde, ¿no?"};
    private static final String[] OPEN = {"¡Hola, %otro%!", "¡Eh, %otro%! ¿Cómo andás?", "¡Buenas, %otro%!", "¿Viste qué día, %otro%?",
            "¡%otro%! Justo te buscaba."};
    private static final String[] REPLY = {"¡Hola, %otro%!", "¡Bien! ¿Y vos, %otro%?", "¡Todo tranqui!", "¡Hola, hola!", "¡Sí, qué día!",
            "¿Ah, sí? ¿Para qué?"};

    private record Reply(UUID who, UUID to, String text, long at) {
    }

    private record Look(UUID who, UUID at, long until) {
    }

    private static final Map<String, Long> LAST = new HashMap<>();
    private static final Map<UUID, Long> BUSY = new HashMap<>();
    private static final List<Reply> REPLIES = new ArrayList<>();
    private static final List<Look> LOOKS = new ArrayList<>();
    private static final Random R = new Random();

    private Greetings() {
    }

    private static String pick(String[] a) {
        return a[R.nextInt(a.length)];
    }

    private static boolean free(Villager v, long now) {
        return v.isAlive() && !v.isSleeping() && !v.isTrading() && StarVillagers.identity(v) != null && BUSY.getOrDefault(v.getUUID(), 0L) < now;
    }

    public static void tick(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        // respuestas y miradas pendientes
        for (Iterator<Reply> it = REPLIES.iterator(); it.hasNext(); ) {
            Reply r = it.next();
            if (r.at() > now) {
                continue;
            }
            it.remove();
            for (ServerLevel level : server.getAllLevels()) {
                Entity e = level.getEntity(r.who());
                if (e instanceof Villager v && v.isAlive()) {
                    StarScenesAPI.say(v, r.text(), 3);
                    StarScenesAPI.play(v, "wave");
                }
            }
        }
        if (!LOOKS.isEmpty()) {
            for (Iterator<Look> it = LOOKS.iterator(); it.hasNext(); ) {
                Look l = it.next();
                if (l.until() < now) {
                    it.remove();
                    continue;
                }
                for (ServerLevel level : server.getAllLevels()) {
                    Entity who = level.getEntity(l.who()), at = level.getEntity(l.at());
                    if (who instanceof Villager v && at != null) {
                        v.getLookControl().setLookAt(at, 30, 30);
                    }
                }
            }
        }
        if (now % 10 != 0) {
            return;
        }
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.isSpectator()) {
                continue;
            }
            ServerLevel level = p.level();
            // pasaste al lado
            for (Villager v : level.getEntitiesOfClass(Villager.class, p.getBoundingBox().inflate(4.5))) {
                String key = v.getUUID() + ":" + p.getUUID();
                if (!free(v, now) || now - LAST.getOrDefault(key, -100000L) < 2400 || R.nextFloat() > 0.35f || !v.hasLineOfSight(p)) {
                    continue;
                }
                LAST.put(key, now);
                BUSY.put(v.getUUID(), now + 80);
                long day = level.getDayTime() % 24000;
                String[] set = R.nextBoolean() ? TO_PLAYER : day < 6000 ? MORNING : day < 12000 ? AFTERNOON : day < 23000 ? NIGHT : MORNING;
                StarScenesAPI.say(v, pick(set).replace("%jugador%", p.getName().getString()), 3.5f);
                StarScenesAPI.play(v, "wave");
                LOOKS.add(new Look(v.getUUID(), p.getUUID(), now + 50));
                break;
            }
            // dos aldeanos que se cruzan (cerca de algun jugador, para no gastar en lugares vacios)
            if (now % 40 != 0) {
                continue;
            }
            for (Villager v : level.getEntitiesOfClass(Villager.class, p.getBoundingBox().inflate(24))) {
                if (!free(v, now) || R.nextFloat() > 0.2f) {
                    continue;
                }
                for (Villager w : level.getEntitiesOfClass(Villager.class, v.getBoundingBox().inflate(3))) {
                    if (w == v || !free(w, now) || !v.hasLineOfSight(w)) {
                        continue;
                    }
                    Identity a = StarVillagers.identity(v), b = StarVillagers.identity(w);
                    long cool = 1800 + R.nextInt(1800);
                    BUSY.put(v.getUUID(), now + cool);
                    BUSY.put(w.getUUID(), now + cool);
                    StarScenesAPI.say(v, pick(OPEN).replace("%otro%", b.name()), 3);
                    StarScenesAPI.play(v, "wave");
                    REPLIES.add(new Reply(w.getUUID(), v.getUUID(), pick(REPLY).replace("%otro%", a.name()), now + 35));
                    LOOKS.add(new Look(v.getUUID(), w.getUUID(), now + 90));
                    LOOKS.add(new Look(w.getUUID(), v.getUUID(), now + 90));
                    break;
                }
            }
        }
        // limpieza de memoria vieja
        if (now % 6000 == 0) {
            LAST.values().removeIf(t -> now - t > 24000);
            BUSY.values().removeIf(t -> t < now);
        }
    }
}
