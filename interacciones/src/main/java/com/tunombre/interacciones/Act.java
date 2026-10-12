package com.tunombre.interacciones;

/** tipos de interaccion */
public final class Act {
    public static final int HIGH_FIVE = 0, FIST_BUMP = 1, CARRY = 2, TRADE = 3, EXECUTE = 4, DROP = 5,
            HUG = 6, HANDSHAKE = 7, BOW = 8, SALUTE = 9, CHEST_BUMP = 10, PAT = 11, DANCE = 12, SHOVE = 13, SLAP = 14, FRISK = 15,
            PIGGYBACK = 16, ARREST = 17, CHOKE = 18, SCARE = 19, BANDAGE = 20, GIVE = 21, TOAST = 22, RPS = 23, SECRET = 24,
            SLOW_DANCE = 25, BOOST = 26, BRIDAL = 27, COUNT = 28;
    public static final String[] VERB = {"chocar los cinco", "chocar el puno", "cargarte al hombro", "tradear contigo", "", "",
            "darte un abrazo", "darte la mano", "hacerte una reverencia", "hacerte un saludo militar", "chocar el pecho",
            "darte una palmada en el hombro", "bailar contigo", "darte un empujon", "darte una cachetada", "cachearte",
            "llevarte a caballito", "", "", "", "vendarte las heridas", "darte lo que tiene en la mano", "brindar contigo",
            "jugar a piedra, papel o tijera", "hacer el saludo secreto", "bailar lento contigo", "impulsarte hacia arriba",
            "llevarte en brazos"};
    public static final String[] NAME = {"Chocar los cinco", "Choque de puno", "Cargar", "Tradear", "Ejecucion sigilosa", "Soltar",
            "Abrazo", "Dar la mano", "Reverencia", "Saludo militar", "Choque de pecho", "Palmada", "Bailar", "Empujon", "Cachetada",
            "Cachear", "A caballito", "Esposar", "Llave de sueno", "Asustar (¡BU!)", "Vendar", "Dar objeto", "Brindis",
            "Piedra, papel, tijera", "Saludo secreto", "Baile lento", "Impulsar", "En brazos"};
    /** orden del menu (G): las que se piden y el otro acepta */
    public static final int[] MENU = {HIGH_FIVE, FIST_BUMP, HUG, HANDSHAKE, SECRET, BOW, SALUTE, CHEST_BUMP, PAT, TOAST, RPS, DANCE, SLOW_DANCE,
            SHOVE, SLAP, FRISK, BANDAGE, GIVE, BOOST, CARRY, PIGGYBACK, BRIDAL, TRADE};

    /** por la espalda y sin pedir permiso (como la ejecucion) */
    public static boolean instant(int a) { return a == EXECUTE || a == ARREST || a == CHOKE || a == SCARE; }

    /** las que no tienen sentido con un NPC (necesitan que el otro sea un jugador de verdad) */
    public static boolean playerOnly(int a) { return a == CARRY || a == TRADE || a == PIGGYBACK || a == BRIDAL || a == ARREST || a == GIVE; }

    private Act() { }
}
