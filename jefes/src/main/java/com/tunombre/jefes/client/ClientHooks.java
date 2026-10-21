package com.tunombre.jefes.client;

import com.tunombre.jefes.Boss;
import com.tunombre.jefes.Bosses;
import com.tunombre.jefes.Jefes;

/** Puntos de entrada de los paquetes del servidor (solo cliente). */
public final class ClientHooks {
    private ClientHooks() { }

    public static void defs(String spec, String model, byte[] png) {
        try {
            Boss b = new Boss(spec, model, png);
            Bosses.CLIENT.put(b.id(), b);
        } catch (Exception e) {
            Jefes.LOG.error("[jefes] el servidor mando un jefe roto", e);
        }
    }

    public static void possess(int entity) { Control.set(entity); }

    public static void cine(int entity) { Cinematic.set(entity); }

    public static void scream(float secs, boolean visual) { ScreamFx.start(secs, visual); }

    public static boolean holding() { return Cinematic.active(); }
}
