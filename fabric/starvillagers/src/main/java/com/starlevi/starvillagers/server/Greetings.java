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

    private record Reply(UUID who, UUID to, String text, long at, String anim) {
    }

    private record Look(UUID who, UUID at, long until) {
    }

    private static final Map<String, Long> LAST = new HashMap<>();
    private static final Map<UUID, Long> BUSY = new HashMap<>();
    private static final Map<UUID, Long> IDLE = new HashMap<>();
    private static final List<Reply> REPLIES = new ArrayList<>();
    private static final List<Look> LOOKS = new ArrayList<>();
    private static final Random R = new Random();

    private Greetings() {
    }

    private static String pick(String[] a) {
        return a[R.nextInt(a.length)];
    }

    private static boolean free(Villager v, long now) {
        return v.isAlive() && !com.starlevi.starvillagers.social.Together.busy(v) && !v.isSleeping() && !v.isTrading() && StarVillagers.identity(v) != null && BUSY.getOrDefault(v.getUUID(), 0L) < now;
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
                    StarScenesAPI.play(v, r.anim());
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
                if (!free(v, now) || now - LAST.getOrDefault(key, -100000L) < 2400 || !v.hasLineOfSight(p)) {
                    continue;
                }
                // las ganas de saludar dependen de la personalidad y de cuanto te quiere
                int hearts = com.starlevi.starvillagers.social.Social.mind(v).hearts(p.getUUID());
                int chance = com.starlevi.starvillagers.social.Social.personality(v).greetChance + Math.max(0, hearts) / 3;
                if (R.nextInt(100) >= chance) {
                    LAST.put(key, now - 1800); // si no saludo, vuelve a intentar en un rato
                    continue;
                }
                LAST.put(key, now);
                BUSY.put(v.getUUID(), now + 80);
                String anim = com.starlevi.starvillagers.social.Social.greetAnim(v, p);
                StarScenesAPI.say(v, com.starlevi.starvillagers.social.Social.greeting(v, p), 3.5f);
                StarScenesAPI.play(v, anim);
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
                    String open = com.starlevi.starvillagers.social.Social.personality(v).line("saludo_aldeano", R);
                    String reply = com.starlevi.starvillagers.social.Social.personality(w).line("respuesta_aldeano", R);
                    StarScenesAPI.say(v, (open.isEmpty() ? pick(OPEN) : open).replace("%otro%", b.name()), 3);
                    StarScenesAPI.play(v, com.starlevi.starvillagers.social.Social.anim(com.starlevi.starvillagers.social.Social.personality(v), "saludo"));
                    REPLIES.add(new Reply(w.getUUID(), v.getUUID(), (reply.isEmpty() ? pick(REPLY) : reply).replace("%otro%", a.name()), now + 35,
                            com.starlevi.starvillagers.social.Social.anim(com.starlevi.starvillagers.social.Social.personality(w), "respuesta")));
                    long talkUntil = now + 90;
                    // a veces se quedan charlando un rato (una conversacion de varias lineas)
                    var convos = com.starlevi.starvillagers.social.Personality.conversations();
                    if (!convos.isEmpty() && R.nextFloat() < 0.45f) {
                        List<String> c = convos.get(R.nextInt(convos.size()));
                        long t = now + 75;
                        for (int i = 0; i < c.size(); i++) {
                            boolean first = i % 2 == 0;
                            Villager who = first ? v : w;
                            String other = first ? b.name() : a.name();
                            String text = c.get(i).replace("%otro%", other);
                            String low = text.toLowerCase(java.util.Locale.ROOT);
                            String anim = low.contains("jaja") ? "laugh" : low.contains("?") && R.nextBoolean() ? "shrug" : low.contains("...") ? "think" : "talk";
                            REPLIES.add(new Reply(who.getUUID(), (first ? w : v).getUUID(), text, t, anim));
                            t += 40 + Math.min(40, text.length());
                        }
                        talkUntil = t + 20;
                        BUSY.put(v.getUUID(), Math.max(now + cool, talkUntil));
                        BUSY.put(w.getUUID(), Math.max(now + cool, talkUntil));
                    }
                    LOOKS.add(new Look(v.getUUID(), w.getUUID(), talkUntil));
                    LOOKS.add(new Look(w.getUUID(), v.getUUID(), talkUntil));
                    break;
                }
            }
        }
        // gestos sueltos de la vida diaria (estirarse a la mañana, bostezar a la noche...) y lo que piensan
        if (now % 100 == 0) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                for (Villager v : p.level().getEntitiesOfClass(Villager.class, p.getBoundingBox().inflate(32))) {
                    if (!free(v, now) || v.isBaby() || R.nextFloat() > 0.05f || IDLE.getOrDefault(v.getUUID(), 0L) > now) {
                        continue;
                    }
                    IDLE.put(v.getUUID(), now + 900 + R.nextInt(1200));
                    long t = v.level().getDayTime() % 24000;
                    String prof = StarVillagers.profession(v);
                    List<String> opts = new ArrayList<>(List.of("look_around", "scratch_head", "look_around"));
                    if (t < 2500 || t > 23000) {
                        opts.addAll(List.of("stretch", "stretch", "yawn"));
                    } else if (t > 11500 && t < 13500) {
                        opts.addAll(List.of("yawn", "yawn", "stretch"));
                    } else if (t > 2000 && t < 9000 && !prof.equals("none") && !prof.equals("nitwit")) {
                        opts.addAll(List.of("wipe_brow", "wipe_brow"));
                    }
                    String g = opts.get(R.nextInt(opts.size()));
                    StarScenesAPI.play(v, g);
                    if (R.nextInt(3) == 0) {
                        var pe = com.starlevi.starvillagers.social.Social.personality(v);
                        String key = g.equals("yawn") ? "pensamiento_sueno" : g.equals("wipe_brow") ? "pensamiento_trabajo" : "pensamiento";
                        String th = pe.line(key, R);
                        if (!th.isEmpty()) {
                            StarScenesAPI.say(v, "§7§o" + th, 3);
                        }
                    }
                }
            }
        }
        // limpieza de memoria vieja
        if (now % 6000 == 0) {
            LAST.values().removeIf(t -> now - t > 24000);
            BUSY.values().removeIf(t -> t < now);
            IDLE.values().removeIf(t -> t < now);
        }
    }
}
