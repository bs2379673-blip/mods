package com.tunombre.anomalias;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * /anomalia <rojo|ambar|violeta|azul|verde> [segundos|fijo]  - lanza la anomalia (por defecto 60 s; "fijo" = queda hasta /anomalia fin)
 * /anomalia fin                                              - la apaga con fundido y el cielo vuelve al de siempre
 * /anomalia destellos <0-1>                                  - intensidad de los relampagos (0 = sin destellos)
 * /anomalia estado
 */
public final class Server {
    private Server() { }

    private static Tipo active;
    private static long startMs;
    private static float duration;   // 0 = fija
    private static float strobe = 1F;

    private record Aviso(long due, String cmd, boolean raw) { }
    private static final List<Aviso> avisos = new ArrayList<>();

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("anomalia").requires(s -> s.hasPermission(2))
                .then(Commands.literal("fin").executes(c -> stop(c.getSource())))
                .then(Commands.literal("estado").executes(c -> {
                    if (active == null) say(c.getSource(), "No hay ninguna anomalia activa.");
                    else say(c.getSource(), "Anomalia " + active.id + (duration > 0 ? String.format(Locale.ROOT, " (quedan %.0f s)", Math.max(0, duration - (System.currentTimeMillis() - startMs) / 1000F)) : " (fija: usa /anomalia fin)"));
                    return 1;
                }))
                .then(Commands.literal("destellos").then(Commands.argument("nivel", FloatArgumentType.floatArg(0F, 1F)).executes(c -> {
                    strobe = FloatArgumentType.getFloat(c, "nivel");
                    Net.sendAll(new Net.Msg(Net.SETTINGS, 0, 0F, 0F, strobe));
                    say(c.getSource(), "Destellos de la anomalia: " + strobe);
                    return 1;
                })))
                .then(Commands.argument("tipo", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("rojo", "ambar", "violeta", "azul", "verde"), b))
                        .executes(c -> start(c.getSource(), StringArgumentType.getString(c, "tipo"), "60"))
                        .then(Commands.argument("duracion", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("fijo", "60", "120", "30"), b))
                                .executes(c -> start(c.getSource(), StringArgumentType.getString(c, "tipo"), StringArgumentType.getString(c, "duracion"))))));
    }

    private static void say(CommandSourceStack s, String m) { s.sendSuccess(() -> Component.literal(m), false); }

    private static int start(CommandSourceStack s, String tipo, String dur) {
        Tipo t = Tipo.of(tipo);
        if (t == null) { s.sendFailure(Component.literal("Tipo desconocido. Usa: rojo, ambar, violeta, azul o verde")); return 0; }
        float d;
        if (dur.equalsIgnoreCase("fijo") || dur.equalsIgnoreCase("fija") || dur.equals("0")) d = 0F;
        else {
            try { d = Math.max(20F, Float.parseFloat(dur)); } catch (NumberFormatException ex) { s.sendFailure(Component.literal("Duracion: un numero de segundos (minimo 20) o 'fijo'")); return 0; }
        }
        active = t; duration = d; startMs = System.currentTimeMillis();
        avisos.clear();
        Net.sendAll(new Net.Msg(Net.START, t.ordinal(), d, 0F, strobe));
        float k = d > 0 ? d / 60F : 1F;
        long now = startMs;
        Cfg.load();
        schedCmds(now, t, "cmdInicio");
        sched(now + 200, 9F, t, Cfg.get(t, "detectada"));
        sched(now + (long) (14000 * k), 7F, t, Cfg.get(t, "expansion"));
        long act = now + (long) (40000 * k);
        sched(act, 9F, t, Cfg.get(t, "activa"));
        schedCmds(act, t, "cmdActiva");
        String chat = Cfg.get(t, "chat");
        if (!chat.isBlank()) avisos.add(new Aviso(act, "tellraw @a {\"text\":" + new com.google.gson.JsonPrimitive(chat.replace('&', '\u00a7')) + ",\"color\":\"" + chatColor(t) + "\"}", true));
        sched(act + (long) (9500 * k), 8F, t, Cfg.get(t, "extra"));
        if (d > 0) sched(now + (long) ((d - 6F * k) * 1000), 5F, t, Cfg.get(t, "retirada"));
        say(s, "Anomalia " + t.id + " lanzada (" + (d > 0 ? (int) d + " s" : "fija, apagala con /anomalia fin") + ")");
        return 1;
    }

    private static int stop(CommandSourceStack s) {
        if (active == null) { s.sendFailure(Component.literal("No hay ninguna anomalia activa")); return 0; }
        Tipo t = active;
        active = null;
        avisos.clear();
        Net.sendAll(new Net.Msg(Net.STOP, 0, 0F, 0F, 0F));
        Cfg.load();
        alert(s.getServer(), 5F, t, Cfg.get(t, "neutralizada"));
        runCmds(s.getServer(), Cfg.own(t, "cmdFin"));
        say(s, "Anomalia apagada");
        return 1;
    }

    private static void sched(long due, float secs, Tipo t, String text) {
        if (text == null || text.isBlank()) return;
        avisos.add(new Aviso(due, "tacalert @a " + String.format(Locale.ROOT, "%.1f", secs) + " " + t.id + " " + text, false));
    }

    private static void schedCmds(long due, Tipo t, String key) {
        for (String c : Cfg.own(t, key).split(";")) {
            c = c.trim();
            if (c.startsWith("/")) c = c.substring(1);
            if (!c.isEmpty()) avisos.add(new Aviso(due, c, true));
        }
    }

    private static void runCmds(MinecraftServer srv, String all) {
        for (String c : all.split(";")) {
            c = c.trim();
            if (c.startsWith("/")) c = c.substring(1);
            if (!c.isEmpty()) srv.getCommands().performPrefixedCommand(srv.createCommandSourceStack().withSuppressedOutput(), c);
        }
    }

    private static String chatColor(Tipo t) {
        return switch (t) { case ROJO -> "red"; case AMBAR -> "gold"; case VIOLETA -> "light_purple"; case AZUL -> "aqua"; default -> "green"; };
    }

    public static String statusJson() {
        if (active == null) return "{\"activa\":\"\",\"dur\":0,\"resta\":0,\"destellos\":" + strobe + "}";
        float left = duration > 0 ? Math.max(0F, duration - (System.currentTimeMillis() - startMs) / 1000F) : -1F;
        return "{\"activa\":\"" + active.id + "\",\"dur\":" + duration + ",\"resta\":" + left + ",\"destellos\":" + strobe + "}";
    }

    private static void alert(MinecraftServer srv, float secs, Tipo t, String text) {
        if (!ModList.get().isLoaded("tacui")) return;
        srv.getCommands().performPrefixedCommand(srv.createCommandSourceStack().withSuppressedOutput(),
                "tacalert @a " + String.format(Locale.ROOT, "%.1f", secs) + " " + t.id + " " + text);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || active == null) return;
        long now = System.currentTimeMillis();
        for (var it = avisos.iterator(); it.hasNext(); ) {
            Aviso a = it.next();
            if (a.due() <= now) {
                it.remove();
                if (a.raw() || ModList.get().isLoaded("tacui"))
                    e.getServer().getCommands().performPrefixedCommand(e.getServer().createCommandSourceStack().withSuppressedOutput(), a.cmd());
            }
        }
        if (duration > 0 && now > startMs + (long) (duration * 1000F) + 1500) { Tipo t = active; active = null; avisos.clear(); runCmds(e.getServer(), Cfg.own(t, "cmdFin")); }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        Net.send(p, new Net.Msg(Net.SETTINGS, 0, 0F, 0F, strobe));
        if (active != null) Net.send(p, new Net.Msg(Net.START, active.ordinal(), duration, (System.currentTimeMillis() - startMs) / 1000F, strobe));
    }
}
