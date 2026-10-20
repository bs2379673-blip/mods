package com.tunombre.flashlightef;

import net.minecraft.world.entity.player.Player;
import yesman.epicfight.client.world.capabilites.entitypatch.player.AbstractClientPlayerPatch;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.player.PlayerPatch;

/** unica clase que toca Epic Fight */
public final class EpicSupport {
    private EpicSupport() { }

    /**
     * true si Epic Fight dibuja a este jugador con su propio esqueleto. Pasa en modo Epic Fight, pero TAMBIEN en modo vanilla
     * cuando la opcion "modelo original" de Epic Fight esta apagada (el modo vanilla es el que se activa solo al pasar a primera
     * persona): el brazo sigue siendo el de Epic Fight y la linterna tiene la misma orientacion que en modo Epic Fight.
     */
    public static boolean isEpicMode(Player p) {
        PlayerPatch<?> pp = EpicFightCapabilities.getEntityPatch(p, PlayerPatch.class);
        if (pp == null) return false;
        if (pp.isEpicFightMode()) return true;
        try {
            return pp instanceof AbstractClientPlayerPatch<?> c && c.overrideRender();
        } catch (Throwable t) { return false; }
    }
}
