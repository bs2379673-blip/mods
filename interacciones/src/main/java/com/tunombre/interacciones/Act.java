package com.tunombre.interacciones;

/** tipos de interaccion */
public final class Act {
    public static final int HIGH_FIVE = 0, FIST_BUMP = 1, CARRY = 2, TRADE = 3, EXECUTE = 4, DROP = 5,
            HUG = 6, HANDSHAKE = 7, BOW = 8, SALUTE = 9, CHEST_BUMP = 10, PAT = 11, DANCE = 12, SHOVE = 13, SLAP = 14, FRISK = 15,
            PIGGYBACK = 16, COUNT = 17;
    public static final String[] VERB = {"chocar los cinco", "chocar el puno", "cargarte al hombro", "tradear contigo", "", "",
            "darte un abrazo", "darte la mano", "hacerte una reverencia", "hacerte un saludo militar", "chocar el pecho",
            "darte una palmada en el hombro", "bailar contigo", "darte un empujon", "darte una cachetada", "cachearte",
            "llevarte a caballito"};
    public static final String[] NAME = {"Chocar los cinco", "Choque de puno", "Cargar", "Tradear", "Ejecucion sigilosa", "Soltar",
            "Abrazo", "Dar la mano", "Reverencia", "Saludo militar", "Choque de pecho", "Palmada", "Bailar", "Empujon", "Cachetada",
            "Cachear", "A caballito"};
    /** orden del menu (G) */
    public static final int[] MENU = {HIGH_FIVE, FIST_BUMP, HUG, HANDSHAKE, BOW, SALUTE, CHEST_BUMP, PAT, DANCE, SHOVE, SLAP, FRISK, CARRY, PIGGYBACK, TRADE};

    /** las que no tienen sentido con un NPC (necesitan que el otro sea un jugador de verdad) */
    public static boolean playerOnly(int a) { return a == CARRY || a == TRADE || a == PIGGYBACK; }

    private Act() { }
}
