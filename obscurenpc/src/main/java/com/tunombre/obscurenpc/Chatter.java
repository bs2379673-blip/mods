package com.tunombre.obscurenpc;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/** Charla entre NPCs que estan juntos: uno dice una frase suelta y el otro le contesta con su propio conocimiento. */
public final class Chatter {
    private static final Random RND = new Random();

    private static final class Run {
        UUID a, b;
        ServerLevel level;
        int step;
        int wait;
        String last = "";
    }

    private static final List<Run> RUNS = new ArrayList<>();

    private Chatter() { }

    /** lo llama un NPC cuando su espera termino: busca con quien charlar */
    static void tick(Actor a) {
        a.chatterCooldown = 500 + RND.nextInt(1200);
        if (a.isTalking() || a.getTarget() != null || a.data().charla_lineas.isEmpty()) return;
        for (Run r : RUNS) if (r.a.equals(a.getUUID()) || r.b.equals(a.getUUID())) return;
        Actor best = null;
        double bd = 36;
        for (Actor o : a.level().getEntitiesOfClass(Actor.class, a.getBoundingBox().inflate(6))) {
            if (o == a || !o.data().charla_npc || o.isTalking() || o.getTarget() != null) continue;
            double dd = a.distanceToSqr(o);
            if (dd < bd) { bd = dd; best = o; }
        }
        if (best == null) return;
        Run r = new Run();
        r.a = a.getUUID();
        r.b = best.getUUID();
        r.level = (ServerLevel) a.level();
        r.wait = 5;
        RUNS.add(r);
    }

    public static void serverTick(MinecraftServer server) {
        if (RUNS.isEmpty()) return;
        for (Iterator<Run> it = RUNS.iterator(); it.hasNext(); ) {
            Run r = it.next();
            Entity ea = r.level.getEntity(r.a), eb = r.level.getEntity(r.b);
            if (!(ea instanceof Actor a) || !(eb instanceof Actor b) || !a.isAlive() || !b.isAlive() || a.distanceTo(b) > 9 || a.talkingTo != null || b.talkingTo != null || a.getTarget() != null) { it.remove(); continue; }
            a.getLookControl().setLookAt(b, 30F, 30F);
            b.getLookControl().setLookAt(a, 30F, 30F);
            if (r.wait-- > 0) continue;
            switch (r.step) {
                case 0 -> {
                    List<String> l = a.data().charla_lineas;
                    r.last = l.get(RND.nextInt(l.size()));
                    r.wait = a.speak(r.last, null, true) + 12;
                }
                case 1 -> {
                    Brain.Reply rep = Brain.replyToNpc(b, a, r.last);
                    String t = rep.text();
                    // si no sabe responder usa una frase suya
                    if (!b.data().charla_lineas.isEmpty() && (RND.nextInt(3) == 0 || t == null)) t = b.data().charla_lineas.get(RND.nextInt(b.data().charla_lineas.size()));
                    r.last = t;
                    r.wait = b.speak(t, null, true) + 12;
                }
                case 2 -> {
                    if (RND.nextBoolean()) { Brain.Reply rep = Brain.replyToNpc(a, b, r.last); r.wait = a.speak(rep.text(), null, true) + 10; }
                    else r.wait = 1;
                }
                default -> { it.remove(); continue; }
            }
            r.step++;
        }
    }

    public static void clear() { RUNS.clear(); }
}
