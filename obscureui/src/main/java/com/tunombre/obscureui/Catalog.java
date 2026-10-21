package com.tunombre.obscureui;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Lo que el panel necesita saber del servidor: que mods de Obscure hay, sus catalogos (plantillas de color, modos, cosmeticos,
 * quick times guardados, zonas de plagas) y el estado en vivo (jugadores, rendimiento, valores del filtro de color).
 * Todo por reflexion: ningun mod es dependencia dura de este.
 */
final class Catalog {
    private static final String[] MODS = {"anomalias", "visionfx", "cosmeticos", "infectados", "bestialago", "quicktime_events"};

    private Catalog() { }

    static boolean has(String id) { return ModList.get().isLoaded(id); }

    private static Object get(Class<?> c, String field, Object on) throws ReflectiveOperationException {
        Field f = c.getField(field);
        return f.get(on);
    }

    private static Object call(Object on, String method) throws ReflectiveOperationException {
        Method m = on.getClass().getMethod(method);
        return m.invoke(on);
    }

    // ------------------------------------------------------------------ catalogo (cambia poco) ----
    static JsonObject catalog(MinecraftServer server) {
        JsonObject o = new JsonObject();
        JsonObject mods = new JsonObject();
        for (String m : MODS) mods.addProperty(m, has(m));
        o.add("mods", mods);

        if (has("visionfx")) {
            try {
                JsonObject v = new JsonObject();
                v.add("plantillas", entries(Class.forName("com.tunombre.visionfx.Templates"), "ALL", "DESC"));
                v.add("modos", entries(Class.forName("com.tunombre.visionfx.Modes"), "IDS", "DESC"));
                o.add("vfx", v);
            } catch (Throwable ignored) { }
        }
        if (has("cosmeticos")) {
            try {
                JsonArray arr = new JsonArray();
                Class<?> c = Class.forName("com.tunombre.cosmeticos.Cosmetics");
                for (Object def : (List<?>) get(c, "ALL", null)) {
                    JsonObject d = new JsonObject();
                    d.addProperty("id", String.valueOf(call(def, "id")));
                    d.addProperty("nombre", String.valueOf(call(def, "nombre")));
                    arr.add(d);
                }
                o.add("cos", arr);
            } catch (Throwable ignored) { }
        }
        if (has("quicktime_events")) {
            try {
                Class<?> api = Class.forName("com.cotii.api.QuickTimeEventsServerApi");
                JsonArray arr = new JsonArray();
                for (Object n : (Collection<?>) api.getMethod("savedNames").invoke(null)) arr.add(String.valueOf(n));
                o.add("qte", arr);
                o.addProperty("qteOn", (Boolean) api.getMethod("isEnabled").invoke(null));
            } catch (Throwable ignored) { }
        }
        if (has("infectados")) {
            try {
                Class<?> zc = Class.forName("com.tunombre.infectados.Zones");
                Object zones = zc.getMethod("get", MinecraftServer.class).invoke(null, server);
                JsonArray arr = new JsonArray();
                for (Object z : ((Map<?, ?>) get(zc, "zones", zones)).values()) {
                    Class<?> k = z.getClass();
                    JsonObject d = new JsonObject();
                    d.addProperty("n", String.valueOf(get(k, "name", z)));
                    d.addProperty("d", ((ResourceKey<?>) get(k, "dim", z)).location().getPath());
                    BlockPos c = (BlockPos) get(k, "center", z);
                    d.addProperty("x", c.getX());
                    d.addProperty("z", c.getZ());
                    d.addProperty("r", (Integer) get(k, "radius", z));
                    d.addProperty("max", (Integer) get(k, "max", z));
                    d.addProperty("on", (Boolean) get(k, "active", z));
                    d.addProperty("boss", (Boolean) get(k, "boss", z));
                    arr.add(d);
                }
                o.add("zonas", arr);
            } catch (Throwable ignored) { }
        }
        return o;
    }

    private static JsonArray entries(Class<?> c, String keys, String desc) throws ReflectiveOperationException {
        Map<?, ?> k = (Map<?, ?>) get(c, keys, null);
        Map<?, ?> d = (Map<?, ?>) get(c, desc, null);
        JsonArray arr = new JsonArray();
        for (Object key : k.keySet()) {
            JsonObject e = new JsonObject();
            e.addProperty("id", String.valueOf(key));
            Object txt = d.get(key);
            e.addProperty("d", txt == null ? "" : String.valueOf(txt));
            arr.add(e);
        }
        return arr;
    }

    // ------------------------------------------------------------------ en vivo ----
    static JsonObject live(MinecraftServer server) {
        JsonObject o = new JsonObject();
        o.addProperty("ms", Math.round(server.getAverageTickTime() * 10F) / 10.0);
        JsonArray players = new JsonArray();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            JsonObject d = new JsonObject();
            d.addProperty("n", p.getGameProfile().getName());
            d.addProperty("d", p.level().dimension().location().getPath());
            d.addProperty("x", (int) p.getX());
            d.addProperty("y", (int) p.getY());
            d.addProperty("z", (int) p.getZ());
            d.addProperty("hp", Math.round(p.getHealth()));
            d.addProperty("ping", p.latency);
            d.addProperty("gm", p.gameMode.getGameModeForPlayer().getName());
            players.add(d);
        }
        o.add("players", players);
        if (has("visionfx")) {
            try {
                Class<?> vd = Class.forName("com.tunombre.visionfx.VisionData");
                Object data = vd.getMethod("get", MinecraftServer.class).invoke(null, server);
                Object g = get(vd, "global", data);
                JsonObject v = new JsonObject();
                for (String f : new String[]{"strength", "contrast", "desaturate", "brightness", "chromatic", "vignette", "saturation", "gamma", "toneAmt"})
                    v.addProperty(f, (Float) get(g.getClass(), f, g));
                v.addProperty("enabled", (Boolean) get(g.getClass(), "enabled", g));
                v.addProperty("ps1", (Boolean) get(g.getClass(), "ps1", g));
                o.add("vfx", v);
            } catch (Throwable ignored) { }
        }
        return o;
    }
}
