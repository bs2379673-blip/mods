package com.tunombre.flashlightef;

import net.minecraft.world.entity.player.Player;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.player.PlayerPatch;

/** unica clase que toca Epic Fight */
public final class EpicSupport {
    private EpicSupport() { }

    /** true si este jugador se esta dibujando con Epic Fight (no en modo vanilla) */
    public static boolean isEpicMode(Player p) {
        PlayerPatch<?> pp = EpicFightCapabilities.getEntityPatch(p, PlayerPatch.class);
        return pp != null && pp.isEpicFightMode();
    }
}
