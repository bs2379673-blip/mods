package com.annis.interact.client;

/** Camara del modo mimos: por encima del hombro, girando alrededor de los dos. */
public final class TouchCam {
    public static boolean active;
    public static int receiver = -1;
    /** Grados alrededor de los dos (0 = detras de quien toca). */
    public static float orbit = 35.0f;
    /** Distancia al punto medio (bloques). */
    public static float distance = 2.2f;

    private TouchCam() {
    }
}
