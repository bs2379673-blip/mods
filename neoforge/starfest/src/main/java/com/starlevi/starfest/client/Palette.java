package com.starlevi.starfest.client;

/** Paletas de colores del show: cambian cada 8 compases y se intensifican en la parte fuerte. */
public final class Palette {
    private static final int[][] SETS = {
            {0xFF2A6D, 0x05D9E8, 0xD1F7FF, 0x7700FF},           // neon
            {0xFF0055, 0xFF8800, 0xFFD000, 0xFF2200},           // fuego
            {0x00E5FF, 0x2979FF, 0x7C4DFF, 0xE040FB},           // hielo / violeta
            {0x00FF85, 0x00FFF0, 0xB2FF59, 0x18FFFF},           // verde laser
            {0xFF4FD8, 0x8F00FF, 0x00D4FF, 0xFFFFFF},           // rosa y celeste
            {0xFF1744, 0xFFFFFF, 0x2962FF, 0xFFFFFF},           // rojo blanco azul
    };
    private static final int[] IDLE = {0x3A1C71, 0x1A2A6C};

    private Palette() {
    }

    public static int color(Beat b, int slot) {
        if (!b.playing()) {
            return IDLE[Math.floorMod(slot, IDLE.length)];
        }
        int[] set = SETS[Math.floorMod(b.section() * 7 + 1, SETS.length)];
        return set[Math.floorMod(slot + b.beat() / 2, set.length)];
    }

    /** Color fijo de la paleta actual (para que todo el boliche combine). */
    public static int main(Beat b, int i) {
        if (!b.playing()) {
            return IDLE[Math.floorMod(i, IDLE.length)];
        }
        int[] set = SETS[Math.floorMod(b.section() * 7 + 1, SETS.length)];
        return set[Math.floorMod(i, set.length)];
    }

    public static float r(int c) {
        return (c >> 16 & 0xFF) / 255.0f;
    }

    public static float g(int c) {
        return (c >> 8 & 0xFF) / 255.0f;
    }

    public static float b(int c) {
        return (c & 0xFF) / 255.0f;
    }

    /** Numero estable por bloque (para que cada foco tenga su "lugar" en la coreografia). */
    public static int slot(net.minecraft.core.BlockPos p) {
        int h = p.getX() * 73856093 ^ p.getY() * 19349663 ^ p.getZ() * 83492791;
        return h & 0x7FFFFFFF;
    }
}
