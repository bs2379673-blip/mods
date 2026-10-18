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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** estado del concierto en el servidor (en memoria) y comandos /concierto */
public final class Show {
    /** orden fijo: el indice es el que usa el shader */
    public static final List<String> SCENES = List.of("spectrum", "oscilloscope", "starfield", "kaleidoscope", "milkdrop", "magnetosphere", "cthugha", "fireworks", "sunset", "meltglow");

    /** medidas del escenario, en unidades de la escala (salvo el giro y la cantidad). El orden es el del paquete. */
    public static final List<String> PARAMS = List.of("piso_inicio", "piso_fin", "piso_ancho", "panel_dist", "panel_atras", "panel_ancho", "panel_alto", "panel_giro", "rayos_ancho", "rayos_atras", "rayos_largo", "rayos_cant");
    private static final float[] DEFAULTS = {-1F, 13F, 11F, 12F, -2F, 10F, 5F, 25F, 9F, -4F, 38F, 10F};

    private static String song = "";
    private static long startMs;
    private static int scene = -1;
    private static float intensity = 1F, strobe = 0.5F;
    private static boolean playing;

    private static boolean stageSet;
    private static double sx, sy, sz;
    private static float stageYaw, stageScale = 4F;
    private static final float[] stageParams = DEFAULTS.clone();

    private Show() { }

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

    private static void say(CommandSourceStack s, String m) { s.sendSuccess(() -> Component.literal(m), true); }

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
        Net.sendAll(new Net.Msg(Net.PLAY, song, scene, 0F, 0F, 0F));
        if (withAudio) audio(s, "execute as @a at @s run playsound customsounds:" + song + " master @s ~ ~ ~ 4 1");
        say(s, "Concierto: " + song + " (escena " + (idx < 0 ? "segun el guion" : SCENES.get(idx)) + ")" + (withAudio ? "" : " [sin audio]"));
        return 1;
    }

    private static int light(CommandSourceStack s, String color, Vec3 at, float size, float secs) {
        int rgb;
        try {
            String c = color.startsWith("#") ? color.substring(1) : color;
            rgb = switch (c.toLowerCase()) {
                case "miku" -> 0x39C5BB;
                case "teto" -> 0xE0324B;
                case "neru" -> 0xF2D21B;
                default -> Integer.parseInt(c, 16);
            };
        } catch (NumberFormatException ex) { say(s, "Color invalido: " + color + " (usa miku, teto, neru o un hex como #ff6a9a)"); return 0; }
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
                    Net.sendAll(new Net.Msg(Net.STOP, "", 0, 0F, 0F, 0F));
                    say(c.getSource(), "Concierto detenido");
                    return 1;
                }))
                .then(Commands.literal("escenario")
                        // /concierto escenario aqui [escala]: donde estas parado es el centro del grupo de bailarinas y mirando hacia el publico
                        .then(Commands.literal("aqui")
                                .executes(c -> setStage(c.getSource(), stageScale))
                                .then(Commands.argument("escala", FloatArgumentType.floatArg(0.25F, 40F)).executes(c -> setStage(c.getSource(), FloatArgumentType.getFloat(c, "escala")))))
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
                .then(Commands.literal("efecto").then(Commands.argument("nombre", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("destello", "nova", "onda", "apagon"), b))
                        .executes(c -> {
                            Net.sendAll(new Net.Msg(Net.FX, StringArgumentType.getString(c, "nombre"), 0, 0F, 0F, 0F));
                            return 1;
                        })))
                .then(Commands.literal("lista").executes(c -> {
                    say(c.getSource(), "Escenas: " + String.join(", ", SCENES) + ", auto");
                    return 1;
                })));
    }

    private static int setStage(CommandSourceStack s, float scale) {
        Vec3 p = s.getPosition();
        // el escenario se alinea a la cuadricula de bloques: centro del bloque y giro de 90 en 90
        sx = Mth.floor(p.x) + 0.5;
        sy = Mth.floor(p.y + 0.001);
        sz = Mth.floor(p.z) + 0.5;
        float yaw = s.getRotation().y;
        stageYaw = Math.round(yaw / 90F) * 90F;
        stageScale = scale;
        stageSet = true;
        Net.sendAll(stageMsg());
        say(s, String.format("Escenario en %.0f %.0f %.0f, mirando hacia %s, escala %.2f", sx, sy, sz, switch (((int) Math.floorMod(Math.round(stageYaw), 360)) / 90) { case 0 -> "el sur (+Z)"; case 1 -> "el oeste (-X)"; case 2 -> "el norte (-Z)"; default -> "el este (+X)"; }, scale));
        return 1;
    }
}
