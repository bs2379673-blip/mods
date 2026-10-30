package com.annis.npc.client;

import com.annis.npc.compat.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Punchy Moves + Fresh Animations Player (EMF): los dos animan los brazos y EMF dibuja al final, asi
 * que el golpe y el minado de Punchy quedaban tapados (solo se movia la espada). Mientras un jugador
 * golpea o mina (y un ratito despues, para que termine la animacion), se pausa EMF en ese jugador y
 * se ve la animacion de Punchy completa.
 */
public final class PunchySwingEmf {
    private static final boolean ACTIVE = FabricLoader.getInstance().isModLoaded("punchy_moves")
            && FabricLoader.getInstance().isModLoaded("entity_model_features");
    /** Ticks que se mantiene la pausa despues del ultimo golpe (las animaciones de Punchy duran ~0,8 s). */
    private static final int HOLD = 16;
    private static final Map<UUID, Integer> LEFT = new HashMap<>();

    private PunchySwingEmf() {
    }

    public static void tick(Minecraft mc) {
        if (!ACTIVE || !EmfCompat.loaded()) {
            return;
        }
        if (mc.level == null) {
            LEFT.clear();
            return;
        }
        for (Player p : mc.level.players()) {
            boolean busy = p.swinging
                    || (p == mc.player && mc.gameMode != null && mc.gameMode.isDestroying());
            UUID id = p.getUUID();
            if (busy) {
                if (!LEFT.containsKey(id)) {
                    EmfCompat.setSwingPaused(id, true);
                }
                LEFT.put(id, HOLD);
            }
        }
        LEFT.entrySet().removeIf(e -> {
            int n = e.getValue() - 1;
            if (n > 0) {
                e.setValue(n);
                return false;
            }
            EmfCompat.setSwingPaused(e.getKey(), false);
            return true;
        });
    }
}
