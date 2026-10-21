package com.tunombre.jefes;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraftforge.fml.loading.FMLPaths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Los quick time events de los jefes, definidos en codigo y escrito como "guardado del servidor" de QuickTime Events
 * (config/quicktime_server/saved/*.json) antes de que el servidor arranque. Se regenera en cada arranque con los valores
 * del config; si alguien edita uno desde el editor del mod y lo guarda en el servidor, ese archivo ya no se toca.
 */
public final class QteDefs {
    private static final Logger LOG = LogManager.getLogger("jefes/qte");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /** nombre de la fase n (1..) del quick time de un jefe */
    public static String name(String boss, int n) { return "jefes_" + boss + "_" + n; }

    public static List<String> phases(Boss b) {
        List<String> l = new ArrayList<>();
        for (int i = 0; i < b.spec.qte.size(); i++) l.add(name(b.id(), i + 1));
        return l;
    }

    private static final class D {
        final JsonObject j = new JsonObject();

        D(String name, String type) {
            j.addProperty("name", name);
            j.addProperty("type", type);
            s("texture", "key_base").n("x", 50).n("y", 70).n("scale", 3.4);
            b("show_keybind_text", true).b("cancel_keybind", true).b("lock_movement", true);
            s("progress_style", "ring").b("show_timer", true).b("urgency", true).b("pressed_flash", true);
            s("anim_in", "pop_in").n("in_duration", 260).s("anim_out", "burst_out").n("out_duration", 380).s("fail_out", "fall_out");
            s("idle", "none").s("press", "punch").s("held", "none");
            s("press_burst", "sparks").s("success_burst", "pixels").s("fail_burst", "smoke");
        }

        D s(String k, String v) { j.addProperty(k, v); return this; }
        D n(String k, Number v) { j.addProperty(k, v); return this; }
        D b(String k, boolean v) { j.addProperty(k, v); return this; }

        D keys(String k1, String k2) {
            s("key", k1);
            if (k2 != null) s("key2", k2);
            return this;
        }

        D color(int rgb) {
            String h = String.format("#%06X", rgb & 0xFFFFFF);
            return s("accent_color", h).s("glow_color", h).s("border_color", h).s("held_color", h);
        }

        D sound(String field, String id, double vol, double pitch) {
            JsonObject o = new JsonObject();
            o.addProperty("sound", id);
            o.addProperty("volume", vol);
            o.addProperty("pitch", pitch);
            j.add(field, o);
            return this;
        }

        D sounds(String press, double ppitch, String ok, String fail) {
            sound("press_sound", press, 0.7, ppitch).b("pitch_ramp", true);
            sound("success_sound", ok, 0.8, 1.2);
            sound("fail_sound", fail, 0.9, 0.7);
            return this;
        }
    }

    private static int ticks(double seconds) { return (int) Math.max(10, Math.round(seconds * 20 * Jefes.cfgQteTime())); }

    private static int color(JsonObject q, int def) {
        try { return q.has("color") ? Integer.parseInt(q.get("color").getAsString().replace("#", ""), 16) : def; } catch (NumberFormatException e) { return def; }
    }

    private static String key(JsonObject q, String k, String def) { return q.has(k) ? q.get(k).getAsString() : def; }

    private static List<JsonObject> all() {
        List<JsonObject> l = new ArrayList<>();
        for (Boss b : Bosses.SERVER.values()) {
            int n = 0;
            for (JsonObject q : b.spec.qte) {
                n++;
                String name = name(b.id(), n), type = key(q, "type", "any");
                int presses = q.has("presses") ? q.get("presses").getAsInt() : 20;
                double secs = q.has("seconds") ? q.get("seconds").getAsDouble() : 5;
                D d = new D(name, type).color(color(q, 0xC8322A)).n("glow", 0.9).n("scale", 3.2);
                switch (type) {
                    case "alternate" -> d.keys(key(q, "key1", "key.left"), key(q, "key2", "key.right")).n("presses", presses)
                            .s("anim_in", "wave_in").s("idle", "sway")
                            .sounds("minecraft:entity.generic.swim", 1.0, "minecraft:entity.player.attack.knockback", "minecraft:entity.generic.explode");
                    case "timing" -> d.keys(key(q, "key", "key.jump"), null).n("presses", presses).n("timing_zone", 0.17).n("timing_speed", 1.5).b("timing_strict", false)
                            .s("anim_in", "slam_in").s("idle", "heartbeat")
                            .sounds("minecraft:entity.player.attack.crit", 0.9, "minecraft:entity.generic.explode", "minecraft:entity.wither.break_block");
                    default -> d.keys(key(q, "key", "key.jump"), null).n("presses", presses)
                            .s("anim_in", "rise_in").s("idle", "breathe").s("held", "aura")
                            .sounds("minecraft:entity.generic.swim", 1.1, "minecraft:entity.player.attack.knockback", "minecraft:entity.generic.explode");
                }
                d.n("time_limit", ticks(secs));
                l.add(d.j);
            }
        }
        return l;
    }

    private static Path root() { return FMLPaths.CONFIGDIR.get().resolve("quicktime_server"); }

    /** escribe/actualiza los guardados de este mod (llamar antes de que arranque el servidor) */
    public static void install() {
        try {
            Path dir = root().resolve("saved");
            Files.createDirectories(dir);
            for (JsonObject def : all()) {
                String name = def.get("name").getAsString();
                Path file = dir.resolve(name.toLowerCase() + ".json");
                if (Files.isRegularFile(file)) {
                    try {
                        JsonObject old = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
                        if (!old.has("obscure")) continue;          // lo guardo alguien desde el editor: es suyo
                    } catch (RuntimeException ex) {
                        // archivo roto: se rehace
                    }
                }
                JsonObject entry = new JsonObject();
                entry.addProperty("obscure", Jefes.MODID);
                entry.add("definition", def);
                entry.add("animations", new JsonArray());
                Files.writeString(file, GSON.toJson(entry), StandardCharsets.UTF_8);
            }
            allowSingleplayer();
        } catch (IOException | RuntimeException ex) {
            LOG.warn("No se pudieron escribir los quick time events: {}", ex.toString());
        }
    }

    /** QuickTime Events solo hace de servidor en un servidor dedicado, salvo que su config diga lo contrario */
    private static void allowSingleplayer() {
        Path cfg = root().resolve("config.json");
        try {
            if (!Files.isRegularFile(cfg)) return;
            String text = Files.readString(cfg, StandardCharsets.UTF_8);
            String fixed = text.replaceAll("\"singleplayer\"\\s*:\\s*false", "\"singleplayer\": true");
            if (!fixed.equals(text)) {
                Files.writeString(cfg, fixed, StandardCharsets.UTF_8);
                LOG.info("QuickTime Events: activado 'singleplayer' para que funcione tambien en mundos de un jugador / LAN");
            }
        } catch (IOException ignored) { }
    }
}
