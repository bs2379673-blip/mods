package com.tunombre.modelos.client;

import com.tunombre.modelos.Modelos;
import net.minecraft.client.Minecraft;

/** solo cliente */
public final class LockClient {
    private LockClient() { }

    public static void set(boolean locked) {
        Modelos.clientLocked = locked;
        var p = Minecraft.getInstance().player;
        Modelos.clientUuid = p != null ? p.getUUID() : null;
    }
}
