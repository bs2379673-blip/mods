package com.tunombre.obscurearcade.client;

import com.tunombre.obscurearcade.Net;

/** Estado del cliente durante un duelo: cada nota juzgada por CubOsu se informa al servidor, que decide todo. */
public final class ClientState {
    private ClientState() { }

    public static volatile boolean dueling;

    public static void onJudge(int lane, int judgement) {
        if (!dueling) return;
        Net.sendToServer("note", lane + "," + judgement);
    }
}
