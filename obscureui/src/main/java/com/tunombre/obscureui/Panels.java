package com.tunombre.obscureui;

import net.minecraft.server.level.ServerPlayer;

/** Lado servidor del panel: anomalias, catalogos de los demas mods y estado en vivo. */
final class Panels {
    private Panels() { }

    private static final String API = "com.tunombre.anomalias.AnomaliaApi";

    /** el paquete admite 32000 caracteres */
    private static String trim(String json) { return json.length() > 31500 ? "{}" : json; }

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
            case "cat" -> Net.toClient(p, "cat", trim(Catalog.catalog(p.server).toString()));
            case "live" -> {
                com.google.gson.JsonObject o = Catalog.live(p.server);
                String st = Net.call(API, "statusJson");
                if (st != null) { try { o.add("anom", com.google.gson.JsonParser.parseString(st)); } catch (Exception ignored) { } }
                Net.toClient(p, "live", trim(o.toString()));
            }
            default -> { }
        }
    }
}
