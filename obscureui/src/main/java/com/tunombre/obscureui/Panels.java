package com.tunombre.obscureui;

import net.minecraft.server.level.ServerPlayer;

/** Lado servidor de los paneles propios (por ahora anomalias). */
final class Panels {
    private Panels() { }

    private static final String API = "com.tunombre.anomalias.AnomaliaApi";

    static void handle(ServerPlayer p, Net.Req r) {
        switch (r.key()) {
            case "anom.get" -> {
                String cfg = Net.call(API, "configJson");
                String st = Net.call(API, "statusJson");
                if (cfg == null) Net.toClient(p, "anom.get", "{\"has\":false}");
                else Net.toClient(p, "anom.get", "{\"has\":true,\"cfg\":" + cfg + ",\"status\":" + st + "}");
            }
            case "anom.status" -> {
                String st = Net.call(API, "statusJson");
                Net.toClient(p, "anom.status", st == null ? "{\"has\":false}" : st);
            }
            case "anom.set" -> {
                String err = Net.call(API, "applyConfig", r.payload());
                Net.toClient(p, "anom.saved", "{\"error\":" + new com.google.gson.JsonPrimitive(err == null ? "El mod anomalias no esta instalado" : err) + "}");
            }
            default -> { }
        }
    }
}
