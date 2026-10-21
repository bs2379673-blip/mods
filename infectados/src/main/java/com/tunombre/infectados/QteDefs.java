package com.tunombre.infectados;

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
 * Los quick time events de este mod, definidos en codigo y escritos como "guardados del servidor" de QuickTime Events
 * (config/quicktime_server/saved/*.json) antes de que el servidor arranque. Se regeneran en cada arranque con los valores
 * del config; si alguien edita uno desde el editor del mod y lo guarda en el servidor, ese archivo ya no se toca.
 */
public final class QteDefs {
    private static final Logger LOG = LogManager.getLogger("infectados/qte");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    // ---- nombres (las fases de cada cadena) ----
    public static final List<String> ALDEANO = List.of("ob_aldeano_1", "ob_aldeano_2");
    public static final List<String> MOTOSIERRA = List.of("ob_motosierra_1", "ob_motosierra_2", "ob_motosierra_3");
    public static final String HORCA_IZQ = "ob_horca_izq", HORCA_DER = "ob_horca_der";

    private QteDefs() { }

    // ------------------------------------------------------------------ constructor de definiciones ----
    private static final class D {
        final JsonObject j = new JsonObject();

        D(String name, String type) {
            j.addProperty("name", name);
            j.addProperty("type", type);
            // base comun: tecla grande abajo al centro, texto con la tecla real (respeta los controles del jugador)
            s("texture", "key_base").n("x", 50).n("y", 70).n("scale", 3.4);
            b("show_keybind_text", true).b("cancel_keybind", true).b("lock_movement", false);
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

        D seq(String... ks) {
            JsonArray a = new JsonArray();
            for (String k : ks) a.add(k);
            j.add("sequence_keys", a);
            return this;
        }
    }

    private static int ticks(double seconds) { return (int) Math.max(10, Math.round(seconds * 20 * Infectados.cfgQteTime())); }

    private static List<JsonObject> all() {
        List<JsonObject> l = new ArrayList<>();
        final String L = "key.left", R = "key.right", F = "key.forward", B = "key.back", J = "key.jump";

        // ================= ALDEANO: lo agarra por delante =================
        // 1) sacudirselo: alternar izquierda/derecha
        l.add(new D(ALDEANO.get(0), "alternate").keys(L, R).n("presses", Infectados.cfgQteAldeanoPresses())
                .n("time_limit", ticks(Infectados.cfgQteAldeanoSeconds()))
                .b("lock_movement", true).color(0xC0392B).n("glow", 0.5).s("anim_in", "slam_in").s("idle", "heartbeat")
                .s("held", "none").n("scale", 3.0)
                .sounds("minecraft:entity.zombie.attack_wooden_door", 1.0, "minecraft:entity.player.attack.knockback", "minecraft:entity.zombie.attack_iron_door").j);
        // 2) zafarse: un golpe a tiempo para soltarse del todo
        l.add(new D(ALDEANO.get(1), "timing").keys(J, null).n("presses", 1).n("timing_zone", 0.24).n("timing_speed", 1.15)
                .b("timing_strict", false).n("time_limit", ticks(2.8)).b("lock_movement", true).color(0xE67E22).n("glow", 0.6)
                .s("anim_in", "zoom_in").s("idle", "pulse").n("scale", 3.4)
                .sounds("minecraft:entity.player.attack.crit", 1.0, "minecraft:entity.iron_golem.hurt", "minecraft:entity.zombie.attack_iron_door").j);

        // ================= MOTOSIERRA: tres fases, cada una mas dificil =================
        // 1) aguantar la sierra lejos del cuello
        l.add(new D(MOTOSIERRA.get(0), "alternate").keys(L, R).n("presses", Infectados.cfgQteSawPresses())
                .n("time_limit", ticks(Infectados.cfgQteSawSeconds())).b("lock_movement", true).color(0xFF7A1A).n("glow", 0.9)
                .s("anim_in", "slam_in").s("idle", "vibrate").s("held", "fire").n("held_delay", 100).n("scale", 3.2)
                .s("press", "shake").s("press_burst", "sparks")
                .sounds("minecraft:block.anvil.hit", 1.4, "minecraft:entity.iron_golem.damage", "minecraft:entity.generic.explode").j);
        // 2) empujar: combo de direcciones antes de que baje
        l.add(new D(MOTOSIERRA.get(1), "sequence").seq(F, L, B, R).n("presses", 2).b("sequence_strict", false)
                .n("time_limit", ticks(Infectados.cfgQteSawSeconds() * 0.9)).b("lock_movement", true).color(0xFF4D1A).n("glow", 1.0)
                .s("anim_in", "glitch_in").s("idle", "shake").n("scale", 2.6).b("show_keybind_text", true)
                .sounds("minecraft:block.chain.hit", 1.0, "minecraft:entity.iron_golem.damage", "minecraft:entity.generic.explode").j);
        // 3) el empujon final: dos golpes a tiempo, zona estrecha
        l.add(new D(MOTOSIERRA.get(2), "timing").keys(J, null).n("presses", 2).n("timing_zone", 0.15).n("timing_speed", 1.6)
                .b("timing_strict", false).n("time_limit", ticks(3.4)).b("lock_movement", true).color(0xFF2A1A).n("glow", 1.2)
                .s("anim_in", "flash_in").s("idle", "heartbeat").s("held", "inferno").n("scale", 3.4)
                .sounds("minecraft:entity.player.attack.crit", 0.9, "minecraft:entity.generic.explode", "minecraft:entity.wither.break_block").j);

        // ================= HORCA: una tecla, medio segundo =================
        l.add(horca(HORCA_IZQ, L));
        l.add(horca(HORCA_DER, R));
        return l;
    }

    private static JsonObject horca(String name, String key) {
        return new D(name, "key").keys(key, null).n("presses", 1).n("time_limit", Infectados.cfgDodgeTicks() + 3)
                .b("lock_movement", false).b("cancel_keybind", false).color(0xFFD23F).n("glow", 0.8)
                .s("anim_in", "zoom_in").n("in_duration", 70).s("anim_out", "pop_out").n("out_duration", 200).s("idle", "shake")
                .n("scale", 4.6).n("y", 62).s("press", "punch").s("progress_style", "bar")
                .sounds("minecraft:entity.player.attack.sweep", 1.2, "minecraft:entity.player.attack.sweep", "minecraft:entity.player.hurt").j;
    }

    // ------------------------------------------------------------------ instalacion ----
    private static Path root() { return FMLPaths.CONFIGDIR.get().resolve("quicktime_server"); }

    /** escribe/actualiza los guardados de este mod (llamar antes de que arranque el servidor) y deja el servidor de QTE listo */
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
                entry.addProperty("obscure", Infectados.MODID);
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
