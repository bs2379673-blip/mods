package com.annis.interact.client;

import java.util.HashMap;
import java.util.Map;

/** Lo que el cliente sabe de las interacciones. */
public final class ClientState {
    public static final Map<Integer, Float> ANCHORS = new HashMap<>();
    public static String inviteFrom = "";
    public static String inviteType = "";
    public static long inviteUntil;
    public static boolean downed;
    public static int downedSeconds;
    public static String session = "";
    /** Bailes: true = hago la parte que sigue (femenina); false = la que guia (masculina). */
    public static boolean followRole;
    /** Modo mimos: estado por cada uno que toca (clave = id de quien toca). */
    public static final Map<Integer, TouchState> TOUCHES = new HashMap<>();

    /** Quien toca a quien, donde, y la version suavizada que dibujan las animaciones. */
    public static final class TouchState {
        public int toucher;
        public int receiver;
        public int zone;
        public final float[] target = new float[3];
        public final float[] start = new float[3];
        public boolean active;
        final float[] smooth = new float[3];
        float w;
        long last;
        int shownZone;
    }

    private ClientState() {
    }

    public static void reset() {
        ANCHORS.clear();
        inviteFrom = "";
        inviteType = "";
        downed = false;
        session = "";
        TOUCHES.clear();
    }
}
