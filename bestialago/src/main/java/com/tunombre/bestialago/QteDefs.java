package com.tunombre.bestialago;

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
 * El quick time de la Bestia del Lago, definido en codigo y escrito como "guardado del servidor" de QuickTime Events
 * (config/quicktime_server/saved/*.json) antes de que el servidor arranque. Se regenera en cada arranque con los valores
 * del config; si alguien edita uno desde el editor del mod y lo guarda en el servidor, ese archivo ya no se toca.
 */
public final class QteDefs {
    private static final Logger LOG = LogManager.getLogger("bestialago/qte");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /** abrir las mandibulas -> sacudirse -> patada al hocico */
    public static final List<String> BESTIA = List.of("ob_bestia_1", "ob_bestia_2", "ob_bestia_3");

    private QteDefs() { }

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

    private static int ticks(double seconds) { return (int) Math.max(10, Math.round(seconds * 20 * BestiaLago.cfgQteTime())); }

    private static List<JsonObject> all() {
        List<JsonObject> l = new ArrayList<>();
        final String L = "key.left", R = "key.right", J = "key.jump";
        // 1) forzarle las mandibulas: cualquier tecla, cuantas mas mejor
        l.add(new D(BESTIA.get(0), "any").keys(J, null).n("presses", BestiaLago.cfgQtePresses())
                .n("time_limit", ticks(BestiaLago.cfgQteSeconds())).color(0x2AA6C8).n("glow", 0.7)
                .s("anim_in", "rise_in").s("idle", "breathe").s("held", "aura").n("scale", 3.2)
                .sounds("minecraft:entity.generic.swim", 1.1, "minecraft:entity.player.attack.knockback", "minecraft:entity.generic.explode").j);
        // 2) sacudirse hasta soltar un brazo: izquierda / derecha
        l.add(new D(BESTIA.get(1), "alternate").keys(L, R).n("presses", Math.max(6, BestiaLago.cfgQtePresses() / 2))
                .n("time_limit", ticks(BestiaLago.cfgQteSeconds() * 0.6)).color(0x35C3A0).n("glow", 0.8)
                .s("anim_in", "wave_in").s("idle", "sway").n("scale", 3.0)
                .sounds("minecraft:entity.generic.splash", 1.0, "minecraft:entity.player.attack.knockback", "minecraft:entity.generic.explode").j);
        // 3) patada al hocico: zona estrecha, dos aciertos
        l.add(new D(BESTIA.get(2), "timing").keys(J, null).n("presses", 2).n("timing_zone", 0.17).n("timing_speed", 1.5)
                .b("timing_strict", false).n("time_limit", ticks(3.6)).color(0xE04F5F).n("glow", 1.0)
                .s("anim_in", "slam_in").s("idle", "heartbeat").n("scale", 3.4)
                .sounds("minecraft:entity.player.attack.crit", 0.9, "minecraft:entity.generic.explode", "minecraft:entity.wither.break_block").j);
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
                entry.addProperty("obscure", BestiaLago.MODID);
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
