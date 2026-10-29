package com.annis.interact.client;

/** Camara del modo mimos: por encima del hombro, girando alrededor de los dos. */
public final class TouchCam {
    public static boolean active;
    public static int receiver = -1;
    /** Grados alrededor de los dos (0 = detras de quien toca). */
    public static float orbit = 70.0f;
    /** Distancia al punto medio (bloques). */
    public static float distance = 2.5f;
    /** Primera persona: camara en los ojos, el propio cuerpo se ve (sin la cabeza) para ver las manos. */
    public static boolean firstPerson = true;
    /** Primera persona: cuanto se corre la mirada (grados) respecto de su cara. */
    public static float lookYaw;
    public static float lookPitch = 4.0f;

    private TouchCam() {
    }
}
