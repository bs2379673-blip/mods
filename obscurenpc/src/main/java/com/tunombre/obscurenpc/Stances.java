package com.tunombre.obscurenpc;

/** Posturas fijas del NPC (se mantienen mientras esta quieto). */
public final class Stances {
    public static final String[] NAMES = {"normal", "sentado", "durmiendo", "arrodillado", "brazos_cruzados", "manos_atras", "manos_cadera", "pensando", "guardia", "agachado"};

    private Stances() { }

    public static int byName(String s) {
        for (int i = 0; i < NAMES.length; i++) if (NAMES[i].equalsIgnoreCase(s)) return i;
        try { return Math.max(0, Math.min(NAMES.length - 1, Integer.parseInt(s))); } catch (NumberFormatException e) { return 0; }
    }

    public static String name(int i) { return i >= 0 && i < NAMES.length ? NAMES[i] : "normal"; }
}
