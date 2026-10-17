package com.tunombre.modelos;

import net.minecraft.server.level.ServerPlayer;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.player.ServerPlayerPatch;

/** unica clase que toca Epic Fight (solo se carga si el mod esta instalado) */
final class EpicSupport {
    private EpicSupport() { }

    /** deja al jugador en modo vanilla (true) o en modo Epic Fight (false); sincroniza con su cliente */
    static void vanilla(ServerPlayer p, boolean vanilla) {
        ServerPlayerPatch pp = EpicFightCapabilities.getEntityPatch(p, ServerPlayerPatch.class);
        if (pp == null) return;
        if (vanilla) { if (!pp.isVanillaMode()) pp.toVanillaMode(true); }
        else if (!pp.isEpicFightMode()) pp.toEpicFightMode(true);
    }

    static boolean isEpic(ServerPlayer p) {
        ServerPlayerPatch pp = EpicFightCapabilities.getEntityPatch(p, ServerPlayerPatch.class);
        return pp != null && pp.isEpicFightMode();
    }
}
