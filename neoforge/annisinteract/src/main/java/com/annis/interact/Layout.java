package com.annis.interact;

/** Medidas de colocacion de cada interaccion (bloques), compartidas por servidor y animaciones. */
public final class Layout {
    public static final float HUG = 0.36f;
    public static final float KISS_FWD = 0.375f, KISS_LEFT = 0.44f;
    public static final float HIGH_FIVE = 0.8f;
    public static final float SHAKE = 0.85f;
    public static final float PAT = 0.6f;
    public static final float HAND_SIDE = 1.1f;
    public static final float WALTZ = 0.6f;
    public static final float STARS_SIDE = 1.05f;
    public static final float SIT_SIDE = 1.0f;
    public static final float HELP = 1.0f;
    public static final float PROPOSE = 0.85f;
    public static final float SLOW = 0.62f;
    /** Mimos: frente a frente, cerca. */
    public static final float TOUCH = 0.7f;
    /** Apoyarse en el hombro: uno al lado del otro. */
    public static final float LEAN_SIDE = 0.9f;
    /** Bailes nuevos (frente a frente). */
    public static final float SWING = 0.85f;
    public static final float BACHATA = 0.55f;
    public static final float TANGO = 0.55f;
    /** Cama: cada uno a esta distancia del centro de la cama, hacia su lado (bloques). */
    public static final float BED_SIDE = 0.22f;
    /** Besar la mano. */
    public static final float HAND_KISS = 0.95f;
    // en brazos: pies del que es llevado respecto al que carga
    public static final float CARRY_FWD = 0.37f, CARRY_UP = 0.52f, CARRY_LEFT = 0.0f;
    public static final float BACK_FWD = -0.19f, BACK_UP = 0.25f, BACK_LEFT = -0.10f;

    private Layout() {
    }
}
