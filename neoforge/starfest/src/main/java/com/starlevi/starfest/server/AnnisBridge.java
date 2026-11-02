package com.starlevi.starfest.server;

import com.annis.npc.link.EntityLinks;
import net.minecraft.world.entity.LivingEntity;

/** Puente con Annis NPC (solo se carga si Annis NPC esta instalado). */
final class AnnisBridge {
    private AnnisBridge() {
    }

    static String current(LivingEntity e) {
        String a = EntityLinks.currentAnim(e);
        return a == null ? "" : a;
    }

    static void play(LivingEntity e, String anim) {
        EntityLinks.play(e, anim, 0);
    }

    static void stop(LivingEntity e) {
        EntityLinks.stop(e);
    }
}
