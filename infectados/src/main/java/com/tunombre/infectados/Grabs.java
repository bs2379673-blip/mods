package com.tunombre.infectados;

import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** para que no te agarren otra vez nada mas soltarte */
public final class Grabs {
    private static final Map<UUID, Long> LAST = new ConcurrentHashMap<>();

    private Grabs() { }

    public static void mark(Player p) { LAST.put(p.getUUID(), p.level().getGameTime()); }

    public static boolean recentlyGrabbed(Player p) {
        Long t = LAST.get(p.getUUID());
        return t != null && p.level().getGameTime() - t < 200;
    }
}
