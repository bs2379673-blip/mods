package com.tunombre.interacciones;

import de.markusbordihn.easynpc.entity.easynpc.EasyNPC;
import net.minecraft.world.entity.Entity;

/**
 * Todo lo que toca clases de Easy NPC vive aislado en esta clase. Nunca se llama (ni se carga, ni se verifica por la
 * JVM) si el mod 'easy_npc' no esta instalado: Interacciones siempre chequea Interacciones.EASY_NPC_LOADED antes de
 * usarla, asi que un servidor sin Easy NPC nunca intenta resolver estas clases.
 */
final class EasyNpcSupport {
    private EasyNpcSupport() { }

    static boolean isEasyNpc(Entity e) { return e instanceof EasyNPC<?>; }
}
