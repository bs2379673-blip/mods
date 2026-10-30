package com.annis.vfx.client;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/**
 * Aparicion de un personaje: no se ve hasta el momento justo y despues "nace" creciendo desde la luz
 * con un pequeno rebote.
 */
public final class Reveal {
    private record Entry(long startTick, float at) {
    }

    private static final Map<Integer, Entry> ENTRIES = new HashMap<>();
    private static final float GROW = 1.6f;

    private Reveal() {
    }

    public static void add(int entityId, long startTick, float at) {
        ENTRIES.put(entityId, new Entry(startTick, at));
    }

    public static void clear() {
        ENTRIES.clear();
    }

    private static float time(Entry e, float tickDelta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return 1.0e9f;
        }
        return (mc.level.getGameTime() - e.startTick() + tickDelta) / 20.0f - e.at();
    }

    /** true mientras todavia no tiene que verse. */
    public static boolean hidden(Entity entity) {
        Entry e = ENTRIES.get(entity.getId());
        if (e == null) {
            return false;
        }
        float t = time(e, Minecraft.getInstance().getPartialTick());
        if (t > GROW + 1) {
            ENTRIES.remove(entity.getId());
            return false;
        }
        return t < 0;
    }

    /** Escala al aparecer (1 = normal). */
    public static float scale(Entity entity, float tickDelta) {
        Entry e = ENTRIES.get(entity.getId());
        if (e == null) {
            return 1;
        }
        float x = Math.max(0, Math.min(1, time(e, tickDelta) / GROW));
        float c1 = 1.20158f, c3 = c1 + 1;
        float s = 1 + c3 * (x - 1) * (x - 1) * (x - 1) + c1 * (x - 1) * (x - 1);
        return Math.max(0.02f, s);
    }
}
