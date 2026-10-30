package com.annis.npc.client;

import com.annis.npc.compat.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Cinematic Interactions + EMF (Fresh Animations): Cinematic pone la pose del jugador en la mesa de
 * crafteo, el horno, el yunque, la fogata... pero EMF la pisa con sus animaciones. Mientras el
 * jugador esta en una de esas escenas, se pausa EMF en ese jugador y se ve la animacion de Cinematic.
 */
public final class CinematicEmf {
    private static final boolean ACTIVE = FabricLoader.getInstance().isModLoaded("cinematic")
            && FabricLoader.getInstance().isModLoaded("entity_model_features");
    private static Method atStation;
    private static Method sitting;
    private static boolean looked;
    private static final Set<UUID> PAUSED = new HashSet<>();

    private CinematicEmf() {
    }

    private static boolean busy(Player p) {
        try {
            if (!looked) {
                looked = true;
                try {
                    atStation = Class.forName("dev.dyslexic.cinematic.client.ClientPayloads").getMethod("atStation", int.class);
                } catch (Throwable ignored) {
                    atStation = null;
                }
                try {
                    sitting = Class.forName("dev.dyslexic.cinematic.client.KneeLayer").getMethod("sitting", int.class);
                } catch (Throwable ignored) {
                    sitting = null;
                }
            }
            if (atStation != null && atStation.invoke(null, p.getId()) != null) {
                return true;
            }
            return sitting != null && Boolean.TRUE.equals(sitting.invoke(null, p.getId()));
        } catch (Throwable t) {
            return false;
        }
    }

    public static void tick(Minecraft mc) {
        if (!ACTIVE || !EmfCompat.loaded()) {
            return;
        }
        if (mc.level == null) {
            for (UUID id : PAUSED) {
                EmfCompat.setOtherPaused(id, false);
            }
            PAUSED.clear();
            return;
        }
        Set<UUID> now = new HashSet<>();
        for (Player p : mc.level.players()) {
            if (busy(p)) {
                now.add(p.getUUID());
            }
        }
        for (UUID id : now) {
            if (PAUSED.add(id)) {
                EmfCompat.setOtherPaused(id, true);
            }
        }
        PAUSED.removeIf(id -> {
            if (now.contains(id)) {
                return false;
            }
            EmfCompat.setOtherPaused(id, false);
            return true;
        });
    }
}
