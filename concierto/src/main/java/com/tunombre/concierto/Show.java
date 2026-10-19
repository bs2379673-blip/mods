package com.tunombre.concierto;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** estado del concierto en el servidor (en memoria) y comandos /concierto */
public final class Show {
    /** orden fijo: el indice es el que usa el shader */
    public static final List<String> SCENES = List.of("spectrum", "oscilloscope", "starfield", "kaleidoscope", "milkdrop", "magnetosphere", "cthugha", "fireworks", "sunset", "meltglow", "static", "konton", "magia", "rooms", "synth", "pop", "hypno", "baka", "obscure", "cosmos", "aurora", "nebulosa", "lluvia", "olas", "tormenta", "galaxia", "ciudad", "prisma", "sakura", "eclipse");

    /** medidas del escenario, en unidades de la escala (salvo el giro y la cantidad). El orden es el del paquete. */
    public static final List<String> PARAMS = List.of("piso_inicio", "piso_fin", "piso_ancho", "panel_dist", "panel_atras", "panel_ancho", "panel_alto", "panel_giro", "rayos_ancho", "rayos_atras", "rayos_largo", "rayos_cant");
    private static final float[] DEFAULTS = {-1F, 13F, 11F, 12F, -2F, 10F, 5F, 25F, 9F, -4F, 38F, 10F};

    private static String song = "";
    private static long startMs;
    private static int scene = -1;
    private static float intensity = 1F, strobe = 0.5F;
    private static boolean playing;

    private static boolean stageSet;
    private static net.minecraft.server.level.ServerLevel stageLevel;
    private static double sx, sy, sz;
    private static float stageYaw, stageScale = 4F;
    private static final float[] stageParams = DEFAULTS.clone();

    /** ordenes del guion que corren en el servidor (efectos para los jugadores) */
    private static final List<JsonObject> srvCues = new ArrayList<>();
    private static int srvNext;

    private Show() { }

    private static void loadServerCues(String name) {
        srvCues.clear();
        srvNext = 0;
        String key = name.replace("_", "").toLowerCase();
        try {
            Path p = FMLPaths.CONFIGDIR.get().resolve("concierto").resolve("shows").resolve(key + ".json");
            Reader r = null;
            if (Files.isRegularFile(p)) r = Files.newBufferedReader(p, StandardCharsets.UTF_8);
            else {
                InputStream in = Show.class.getResourceAsStream("/assets/concierto/shows/" + key + ".json");
                if (in != null) r = new InputStreamReader(in, StandardCharsets.UTF_8);
            }
            if (r == null) return;
            final Reader rr = r;
            try (rr) {
                JsonElement root = JsonParser.parseReader(rr);
                for (JsonElement e : root.getAsJsonObject().getAsJsonArray("cues")) {
                    JsonObject o = e.getAsJsonObject();
                    if (o.has("fx") && (o.get("fx").getAsString().equals("jugadores") || o.get("fx").getAsString().equals("aparecer") || o.get("fx").getAsString().equals("aviso") || o.get("fx").getAsString().equals("final"))) srvCues.add(o);
                }
            }
            srvCues.sort((a, b) -> Float.compare(a.get("t").getAsFloat(), b.get("t").getAsFloat()));
        } catch (Exception ignored) { }
    }

    /** ordenes diferidas (por ejemplo: que aparezca la bailarina justo cuando la estrella toca el escenario) */
    private record Pending(long due, String cmd) { }
    private static final List<Pending> pending = new ArrayList<>();

    private static void runCmd(MinecraftServer srv, String cmd) {
        // corre en la dimension del escenario (si no, @e solo vería el mundo principal y no hallaría las marcas)
        var src = srv.createCommandSourceStack().withSuppressedOutput();
        if (stageLevel != null) src = src.withLevel(stageLevel);
        srv.getCommands().performPrefixedCommand(src, cmd);
    }

    /** hace aparecer a la bailarina (nativo, sin datapack) */
    private static void appearNow(MinecraftServer srv, String who) {
        Dancing.appear(srv, who, stageFallback(srv));
    }

    private static Dancing.Mark stageFallback(MinecraftServer srv) {
        if (!stageSet || stageLevel == null) return null;
        return new Dancing.Mark(stageLevel.dimension(), sx, sy, sz, stageYaw);
    }

    private static String fnOf(String who) {
        return switch (who.toLowerCase()) {
            case "miku" -> "mikushow:bailarina/auto_miku";
            case "teto" -> "mikushow:bailarina/auto_teto";
            case "neru" -> "mikushow:bailarina/auto_neru";
            default -> null;
        };
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Dancing.tick();
        if (!pending.isEmpty()) {
            long now = System.currentTimeMillis();
            for (var it = pending.iterator(); it.hasNext(); ) {
                Pending p = it.next();
                if (p.due() <= now) { it.remove(); if (p.cmd().startsWith("@appear ")) appearNow(e.getServer(), p.cmd().substring(8)); else runCmd(e.getServer(), p.cmd()); }
            }
        }
        if (!playing || srvNext >= srvCues.size()) return;
        float t = (System.currentTimeMillis() - startMs) / 1000F;
        while (srvNext < srvCues.size() && srvCues.get(srvNext).get("t").getAsFloat() <= t) {
            JsonObject o = srvCues.get(srvNext++);
            if (t - o.get("t").getAsFloat() > 2F) continue;
            if (o.get("fx").getAsString().equals("aviso")) {
                Avisos.show(e.getServer(), o);
                continue;
            }
            if (o.get("fx").getAsString().equals("final")) {
                Dancing.vanishAll(e.getServer());
                continue;
            }
            if (o.get("fx").getAsString().equals("aparecer")) {
                if (o.has("who")) appearNow(e.getServer(), o.get("who").getAsString());
                continue;
            }
            Vec3 c = stageSet ? new Vec3(sx, sy, sz) : Vec3.ZERO;
            Players.apply(e.getServer(), o.has("modo") ? o.get("modo").getAsString() : "brillo", o.has("dur") ? o.get("dur").getAsFloat() : 8F, c, o.has("radio") ? o.get("radio").getAsFloat() : 80F);
        }
    }

    private static int sceneIndex(String n) { return n.equalsIgnoreCase("auto") ? -1 : SCENES.indexOf(n.toLowerCase()); }

    private static Net.Msg stageMsg() {
        return new Net.Msg(Net.STAGE, "", 0, stageYaw, stageSet ? stageScale : 0F, 0F, sx, sy, sz, stageParams.clone());
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        Net.send(p, new Net.Msg(Net.PARAMS, "", 0, intensity, strobe, 0F));
        Net.send(p, stageMsg());
        if (playing) Net.send(p, new Net.Msg(Net.PLAY, song, scene, 0F, 0F, (System.currentTimeMillis() - startMs) / 1000F));
    }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent e) { register(e.getDispatcher()); }

    private static void say(CommandSourceStack s, String m) { s.sendSuccess(() -> Component.literal(m), false); }

    private static void audio(CommandSourceStack s, String cmd) {
        MinecraftServer srv = s.getServer();
        srv.getCommands().performPrefixedCommand(srv.createCommandSourceStack().withSuppressedOutput(), cmd);
    }

    private static int start(CommandSourceStack s, String name, String sc, boolean withAudio) {
        int idx = sceneIndex(sc);
        if (idx == -1 && !sc.equalsIgnoreCase("auto")) { say(s, "Escena desconocida: " + sc + ". Usa /concierto lista"); return 0; }
        song = name;
        scene = idx;
        startMs = System.currentTimeMillis();
        playing = true;
        loadServerCues(name);
        Dancing.danceFor(s.getServer(), name);
        Net.sendAll(new Net.Msg(Net.PLAY, song, scene, 0F, 0F, 0F));
        if (withAudio) audio(s, "execute as @a at @s run playsound customsounds:" + song + " master @s ~ ~ ~ 4 1");
        say(s, "Concierto: " + song + " (escena " + (idx < 0 ? "segun el guion" : SCENES.get(idx)) + ")" + (withAudio ? "" : " [sin audio]"));
        return 1;
    }

    private static int colorOf(String color) {
        String c = color.startsWith("#") ? color.substring(1) : color;
        return switch (c.toLowerCase()) {
            case "miku" -> 0x39C5BB;
            case "teto" -> 0xE0324B;
            case "neru" -> 0xF2D21B;
            default -> Integer.parseInt(c, 16);
        };
    }

    /** estrella del color de cada una: cruza el cielo sobre el publico y cae en el escenario (o en pos) */
    private static int star(CommandSourceStack s, String color, Vec3 at, float secs) {
        int rgb;
        try { rgb = colorOf(color); } catch (NumberFormatException ex) { say(s, "Color invalido: " + color); return 0; }
        float side = color.equalsIgnoreCase("teto") ? -1F : 1F;
        Net.sendAll(new Net.Msg(Net.STAR, "", rgb, secs, side, 0F, at.x, at.y, at.z, new float[0]));
        return 1;
    }


    /** marca (sin invocar nada) el lugar donde aparecen las bailarinas: donde estas parado y mirando hacia el publico */
    private static int mark(CommandSourceStack s, int scale) {
        if (!(s.getEntity() instanceof net.minecraft.server.level.ServerPlayer p)) { say(s, "Usa /concierto marcar parado donde bailan"); return 0; }
        say(s, Dancing.setMark(p, scale));
        return 1;
    }

    private static int unmark(CommandSourceStack s) {
        int n = Dancing.clearMarks(s.getServer());
        say(s, n + " marca(s) borradas");
        return 1;
    }

    private static int appear(CommandSourceStack s, String who) {
        if (!Dancing.known(who)) { say(s, "Usa miku, teto o neru"); return 0; }
        appearNow(s.getServer(), who);
        return 1;
    }

    /** estrella que cae y, justo en la explosion del impacto, aparece la bailarina (todo con el mismo reloj) */
    private static int entry(CommandSourceStack s, String who, float secs) {
        if (!Dancing.known(who)) { say(s, "Usa miku, teto o neru"); return 0; }
        star(s, who, stageOr(s), secs);
        pending.add(new Pending(System.currentTimeMillis() + (long) ((secs + 0.1F) * 1000F), "@appear " + who));
        return 1;
    }

    private static Vec3 stageOr(CommandSourceStack s) { return stageSet ? new Vec3(sx, sy, sz) : s.getPosition(); }

    private static int light(CommandSourceStack s, String color, Vec3 at, float size, float secs) {
        int rgb;
        try { rgb = colorOf(color); }
        catch (NumberFormatException ex) { say(s, "Color invalido: " + color + " (usa miku, teto, neru o un hex como #ff6a9a)"); return 0; }
        Net.sendAll(new Net.Msg(Net.LIGHT, "", rgb, size, secs, 0F, at.x, at.y, at.z, new float[0]));
        say(s, "Columna de luz #" + String.format("%06x", rgb));
        return 1;
    }

    private static List<String> names(List<String> l, String... extra) {
        List<String> out = new ArrayList<>(l);
        out.addAll(Arrays.asList(extra));
        return out;
    }

    private static void register(CommandDispatcher<CommandSourceStack> d) {
        var scenes = (com.mojang.brigadier.suggestion.SuggestionProvider<CommandSourceStack>) (c, b) -> SharedSuggestionProvider.suggest(names(SCENES, "auto"), b);
        d.register(Commands.literal("concierto").requires(s -> s.hasPermission(2))
                .then(Commands.literal("iniciar").then(Commands.argument("cancion", StringArgumentType.word())
                        .executes(c -> start(c.getSource(), StringArgumentType.getString(c, "cancion"), "auto", true))
                        .then(Commands.argument("escena", StringArgumentType.word()).suggests(scenes)
                                .executes(c -> start(c.getSource(), StringArgumentType.getString(c, "cancion"), StringArgumentType.getString(c, "escena"), true))
                                .then(Commands.argument("audio", BoolArgumentType.bool())
                                        .executes(c -> start(c.getSource(), StringArgumentType.getString(c, "cancion"), StringArgumentType.getString(c, "escena"), BoolArgumentType.getBool(c, "audio")))))))
                .then(Commands.literal("escena").then(Commands.argument("nombre", StringArgumentType.word()).suggests(scenes)
                        .executes(c -> {
                            String n = StringArgumentType.getString(c, "nombre");
                            int i = sceneIndex(n);
                            if (i == -1 && !n.equalsIgnoreCase("auto")) { say(c.getSource(), "Escena desconocida: " + n); return 0; }
                            scene = i;
                            Net.sendAll(new Net.Msg(Net.SCENE, "", i, 0F, 0F, 0F));
                            say(c.getSource(), "Escena: " + (i < 0 ? "segun el guion" : SCENES.get(i)));
                            return 1;
                        })))
                .then(Commands.literal("intensidad").then(Commands.argument("valor", FloatArgumentType.floatArg(0F, 2F)).executes(c -> {
                    intensity = FloatArgumentType.getFloat(c, "valor");
                    Net.sendAll(new Net.Msg(Net.PARAMS, "", 0, intensity, strobe, 0F));
                    say(c.getSource(), "Intensidad: " + intensity);
                    return 1;
                })))
                .then(Commands.literal("estrobo").then(Commands.argument("valor", FloatArgumentType.floatArg(0F, 1F)).executes(c -> {
                    strobe = FloatArgumentType.getFloat(c, "valor");
                    Net.sendAll(new Net.Msg(Net.PARAMS, "", 0, intensity, strobe, 0F));
                    say(c.getSource(), "Estrobo y destellos: " + strobe);
                    return 1;
                })))
                .then(Commands.literal("detener").executes(c -> {
                    if (playing && !song.isEmpty()) audio(c.getSource(), "stopsound @a * customsounds:" + song);
                    playing = false;
                    srvCues.clear();
                    Players.cancel(c.getSource().getServer());
                    Net.sendAll(new Net.Msg(Net.STOP, "", 0, 0F, 0F, 0F));
                    say(c.getSource(), "Concierto detenido");
                    return 1;
                }))
                .then(Commands.literal("escenario")
                        // /concierto escenario aqui [escala]: donde estas parado es el centro del grupo de bailarinas y mirando hacia el publico
                        .then(Commands.literal("aqui")
                                .executes(c -> setStage(c.getSource(), stageScale))
                                .then(Commands.argument("escala", FloatArgumentType.floatArg(0.25F, 40F)).executes(c -> setStage(c.getSource(), FloatArgumentType.getFloat(c, "escala")))))
                        .then(Commands.literal("marca").executes(c -> stageFromMark(c.getSource())))
                        .then(Commands.literal("quitar").executes(c -> {
                            stageSet = false;
                            Net.sendAll(stageMsg());
                            say(c.getSource(), "Escenario quitado");
                            return 1;
                        }))
                        .then(Commands.literal("ajustar").then(Commands.argument("medida", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(PARAMS, b))
                                .then(Commands.argument("valor", FloatArgumentType.floatArg(-200F, 400F)).executes(c -> {
                                    int i = PARAMS.indexOf(StringArgumentType.getString(c, "medida"));
                                    if (i < 0) { say(c.getSource(), "Medidas: " + String.join(", ", PARAMS)); return 0; }
                                    stageParams[i] = FloatArgumentType.getFloat(c, "valor");
                                    Net.sendAll(stageMsg());
                                    say(c.getSource(), PARAMS.get(i) + " = " + stageParams[i]);
                                    return 1;
                                }))))
                        .then(Commands.literal("reiniciar").executes(c -> {
                            System.arraycopy(DEFAULTS, 0, stageParams, 0, DEFAULTS.length);
                            Net.sendAll(stageMsg());
                            say(c.getSource(), "Medidas del escenario restauradas");
                            return 1;
                        })))
                // /concierto luz <miku|teto|neru|#hex> [x y z] [tamano] [segundos]: columna de luz que cae del cielo
                .then(Commands.literal("luz").then(Commands.argument("color", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("miku", "teto", "neru"), b))
                        .executes(c -> light(c.getSource(), StringArgumentType.getString(c, "color"), c.getSource().getPosition(), 1F, 4F))
                        .then(Commands.argument("pos", Vec3Argument.vec3())
                                .executes(c -> light(c.getSource(), StringArgumentType.getString(c, "color"), Vec3Argument.getVec3(c, "pos"), 1F, 4F))
                                .then(Commands.argument("tamano", FloatArgumentType.floatArg(0.2F, 40F))
                                        .executes(c -> light(c.getSource(), StringArgumentType.getString(c, "color"), Vec3Argument.getVec3(c, "pos"), FloatArgumentType.getFloat(c, "tamano"), 4F))
                                        .then(Commands.argument("segundos", FloatArgumentType.floatArg(1F, 60F))
                                                .executes(c -> light(c.getSource(), StringArgumentType.getString(c, "color"), Vec3Argument.getVec3(c, "pos"), FloatArgumentType.getFloat(c, "tamano"), FloatArgumentType.getFloat(c, "segundos"))))))))
                // /concierto marcar [escala 1|4|10] | quitarmarcas | aparecer <quien> | entrada <quien> [segundos]
                .then(Commands.literal("marcar")
                        .executes(c -> mark(c.getSource(), 4))
                        .then(Commands.argument("escala", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 10))
                                .executes(c -> mark(c.getSource(), com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "escala")))))
                .then(Commands.literal("hologramas").then(Commands.argument("estado", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("on", "off"), b))
                        .executes(c -> {
                            Dancing.holos = !StringArgumentType.getString(c, "estado").equalsIgnoreCase("off");
                            say(c.getSource(), "Hologramas de Miku: " + (Dancing.holos ? "ENCENDIDOS (salen cuando aparece Miku)" : "APAGADOS"));
                            return 1;
                        })))
                .then(Commands.literal("final").executes(c -> {   // prueba del final: las bailarinas estallan y arranca el juego de luces
                    Dancing.vanishAll(c.getSource().getServer());
                    Net.sendAll(new Net.Msg(Net.FX, "final", 0, 0F, 0F, 0F));
                    say(c.getSource(), "Final de prueba lanzado");
                    return 1;
                }))
                .then(Commands.literal("vermarcas").executes(c -> { say(c.getSource(), Dancing.listMarks(c.getSource().getServer())); return 1; }))
                .then(Commands.literal("quitarmarcas").executes(c -> unmark(c.getSource())))
                .then(Commands.literal("aparecer").then(Commands.argument("quien", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("miku", "teto", "neru"), b))
                        .executes(c -> appear(c.getSource(), StringArgumentType.getString(c, "quien")))))
                .then(Commands.literal("entrada").then(Commands.argument("quien", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("miku", "teto", "neru"), b))
                        .executes(c -> entry(c.getSource(), StringArgumentType.getString(c, "quien"), 3.2F))
                        .then(Commands.argument("segundos", FloatArgumentType.floatArg(1F, 20F))
                                .executes(c -> entry(c.getSource(), StringArgumentType.getString(c, "quien"), FloatArgumentType.getFloat(c, "segundos"))))))
                // /concierto estrella <miku|teto|neru|#hex> [pos] [segundos]
                .then(Commands.literal("estrella").then(Commands.argument("color", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("miku", "teto", "neru"), b))
                        .executes(c -> star(c.getSource(), StringArgumentType.getString(c, "color"), stageOr(c.getSource()), 3.2F))
                        .then(Commands.argument("pos", Vec3Argument.vec3())
                                .executes(c -> star(c.getSource(), StringArgumentType.getString(c, "color"), Vec3Argument.getVec3(c, "pos"), 3.2F))
                                .then(Commands.argument("segundos", FloatArgumentType.floatArg(1F, 20F))
                                        .executes(c -> star(c.getSource(), StringArgumentType.getString(c, "color"), Vec3Argument.getVec3(c, "pos"), FloatArgumentType.getFloat(c, "segundos")))))))
                // /concierto jugadores <volar|brillo|confeti|nada> [segundos] [radio]
                .then(Commands.literal("jugadores").then(Commands.argument("modo", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("volar", "brillo", "confeti", "nada"), b))
                        .executes(c -> Players.apply(c.getSource().getServer(), StringArgumentType.getString(c, "modo"), 10F, stageOr(c.getSource()), 80F))
                        .then(Commands.argument("segundos", FloatArgumentType.floatArg(1F, 120F))
                                .executes(c -> Players.apply(c.getSource().getServer(), StringArgumentType.getString(c, "modo"), FloatArgumentType.getFloat(c, "segundos"), stageOr(c.getSource()), 80F))
                                .then(Commands.argument("radio", FloatArgumentType.floatArg(5F, 400F))
                                        .executes(c -> Players.apply(c.getSource().getServer(), StringArgumentType.getString(c, "modo"), FloatArgumentType.getFloat(c, "segundos"), stageOr(c.getSource()), FloatArgumentType.getFloat(c, "radio")))))))
                .then(Commands.literal("efecto").then(Commands.argument("nombre", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("destello", "nova", "onda", "apagon", "pirotecnia", "fuegos"), b))
                        .executes(c -> {
                            Net.sendAll(new Net.Msg(Net.FX, StringArgumentType.getString(c, "nombre"), 0, 0F, 0F, 0F));
                            return 1;
                        })))
                .then(Commands.literal("lista").executes(c -> {
                    say(c.getSource(), "Escenas: " + String.join(", ", SCENES) + ", auto");
                    return 1;
                })));
    }

    private static int stageFromMark(CommandSourceStack s) {
        Dancing.Mark m = Dancing.stageMark(s.getServer());
        if (m == null) { say(s, "No hay marca: parate donde bailan y usa /concierto marcar"); return 0; }
        var lv = s.getServer().getLevel(m.dim());
        if (lv == null) { say(s, "La dimension de la marca no existe"); return 0; }
        return setStageAt(s, lv, new Vec3(m.x(), m.y(), m.z()), m.yaw(), stageScale);
    }

    private static int setStage(CommandSourceStack s, float scale) {
        return setStageAt(s, s.getLevel(), s.getPosition(), s.getRotation().y, scale);
    }

    private static int setStageAt(CommandSourceStack s, net.minecraft.server.level.ServerLevel lv, Vec3 p, float yaw, float scale) {
        // el escenario se alinea a la cuadricula de bloques: centro del bloque y giro de 90 en 90
        sx = Mth.floor(p.x) + 0.5;
        sy = Mth.floor(p.y + 0.001);
        sz = Mth.floor(p.z) + 0.5;
        stageYaw = Math.round(yaw / 90F) * 90F;
        stageScale = scale;
        stageSet = true;
        stageLevel = lv;
        Net.sendAll(stageMsg());
        say(s, String.format("Escenario en %.0f %.0f %.0f, mirando hacia %s, escala %.2f", sx, sy, sz, switch (((int) Math.floorMod(Math.round(stageYaw), 360)) / 90) { case 0 -> "el sur (+Z)"; case 1 -> "el oeste (-X)"; case 2 -> "el norte (-Z)"; default -> "el este (+X)"; }, scale));
        return 1;
    }
}
