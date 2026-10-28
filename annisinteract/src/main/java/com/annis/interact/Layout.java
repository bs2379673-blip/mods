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
    // en brazos: pies del que es llevado respecto al que carga
    public static final float CARRY_FWD = 0.41f, CARRY_UP = 0.69f, CARRY_LEFT = 0.0f;
    public static final float BACK_FWD = -0.28f, BACK_UP = 0.20f, BACK_LEFT = -0.22f;

    private Layout() {
    }
}
