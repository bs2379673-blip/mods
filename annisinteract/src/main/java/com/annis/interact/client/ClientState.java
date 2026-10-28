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

    private ClientState() {
    }

    public static void reset() {
        ANCHORS.clear();
        inviteFrom = "";
        inviteType = "";
        downed = false;
        session = "";
    }
}
