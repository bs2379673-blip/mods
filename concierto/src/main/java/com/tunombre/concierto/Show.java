package com.tunombre.concierto;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Arrays;
import java.util.List;

/** estado del concierto en el servidor (en memoria) y comandos /concierto */
public final class Show {
    /** orden fijo: el indice es el que usa el shader */
    public static final List<String> SCENES = List.of("spectrum", "oscilloscope", "starfield", "kaleidoscope", "milkdrop", "magnetosphere", "cthugha", "fireworks");

    private static String song = "";
    private static long startMs;
    private static int scene = -1;
    private static float intensity = 1F, strobe = 0.5F;
    private static boolean playing;

    private Show() { }

    private static int sceneIndex(String n) { return n.equalsIgnoreCase("auto") ? -1 : SCENES.indexOf(n.toLowerCase()); }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        Net.send(p, new Net.Msg(Net.PARAMS, "", 0, intensity, strobe, 0F));
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
        say(s, "Concierto: " + song + " (escena " + (idx < 0 ? "auto" : SCENES.get(idx)) + ")" + (withAudio ? "" : " [sin audio]"));
        return 1;
    }

    private static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("concierto").requires(s -> s.hasPermission(2))
                .then(Commands.literal("iniciar").then(Commands.argument("cancion", StringArgumentType.word())
                        .executes(c -> start(c.getSource(), StringArgumentType.getString(c, "cancion"), "auto", true))
                        .then(Commands.argument("escena", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(append(SCENES, "auto"), b))
                                .executes(c -> start(c.getSource(), StringArgumentType.getString(c, "cancion"), StringArgumentType.getString(c, "escena"), true))
                                .then(Commands.argument("audio", BoolArgumentType.bool())
                                        .executes(c -> start(c.getSource(), StringArgumentType.getString(c, "cancion"), StringArgumentType.getString(c, "escena"), BoolArgumentType.getBool(c, "audio")))))))
                .then(Commands.literal("escena").then(Commands.argument("nombre", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(append(SCENES, "auto"), b))
                        .executes(c -> {
                            String n = StringArgumentType.getString(c, "nombre");
                            int i = sceneIndex(n);
                            if (i == -1 && !n.equalsIgnoreCase("auto")) { say(c.getSource(), "Escena desconocida: " + n); return 0; }
                            scene = i;
                            Net.sendAll(new Net.Msg(Net.SCENE, "", i, 0F, 0F, 0F));
                            say(c.getSource(), "Escena: " + (i < 0 ? "auto" : SCENES.get(i)));
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
                .then(Commands.literal("lista").executes(c -> {
                    say(c.getSource(), "Escenas: " + String.join(", ", SCENES) + ", auto");
                    return 1;
                })));
    }

    private static List<String> append(List<String> l, String x) {
        String[] a = Arrays.copyOf(l.toArray(new String[0]), l.size() + 1);
        a[l.size()] = x;
        return Arrays.asList(a);
    }
}
