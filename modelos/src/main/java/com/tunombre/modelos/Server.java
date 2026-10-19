package com.tunombre.modelos;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** comandos /modelos y el mantenimiento de Epic Fight apagado mientras el jugador tiene un modelo puesto */
public final class Server {
    private Server() { }

    private static int tick;
    private static final SuggestionProvider<CommandSourceStack> MODELS = (c, b) -> SharedSuggestionProvider.suggest(Store.list(), b);

    static boolean cpmPresent(MinecraftServer s) { return s.getCommands().getDispatcher().getRoot().getChild("cpm") != null; }

    static void sendMenu(ServerPlayer to, boolean open) {
        MinecraftServer s = to.server;
        Data d = Data.get(s);
        List<Net.Who> ps = new ArrayList<>();
        for (ServerPlayer p : s.getPlayerList().getPlayers()) ps.add(new Net.Who(p.getGameProfile().getName(), d.applied.getOrDefault(p.getUUID(), "")));
        ps.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        Net.send(to, new Net.Data(open, cpmPresent(s), Modelos.EPIC, Store.list(), ps));
    }

    @SubscribeEvent
    public static void onStarting(net.minecraftforge.event.server.ServerStartingEvent e) { Store.ensure(); }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent e) { register(e.getDispatcher()); }

    /** jugadores con el candado de Epic Fight puesto (tienen un modelo de CPM) */
    private static final java.util.Set<java.util.UUID> LOCKED = java.util.concurrent.ConcurrentHashMap.newKeySet();

    static boolean isLocked(ServerPlayer p) { return LOCKED.contains(p.getUUID()); }

    private static java.lang.reflect.Method cpmGet;
    private static java.lang.reflect.Field cpmData;

    /** true si CPM tiene un modelo cargado para el jugador, lo haya puesto este mod, el comando /cpm o el propio jugador */
    static boolean cpmHasModel(ServerPlayer p) {
        try {
            Object conn = p.connection;
            if (conn == null) return false;
            if (cpmGet == null) cpmGet = Class.forName("com.tom.cpm.shared.network.NetH$ServerNetH").getMethod("cpm$getEncodedModelData");
            if (!cpmGet.getDeclaringClass().isInstance(conn)) return false;
            Object pd = cpmGet.invoke(conn);
            if (pd == null) return false;
            if (cpmData == null) cpmData = pd.getClass().getField("data");
            byte[] b = (byte[]) cpmData.get(pd);
            return b != null && b.length > 0;
        } catch (Throwable t) {
            return false;
        }
    }

    /** fuerza el estado de Epic Fight de un jugador segun tenga o no modelo; avisa a su cliente del candado */
    static void enforce(ServerPlayer p, Data d) {
        boolean has = d.applied.containsKey(p.getUUID()) || cpmHasModel(p);
        java.util.UUID id = p.getUUID();
        if (has) {
            if (LOCKED.add(id)) Net.send(p, new Net.Lock(true));
            if (EpicSupport.isEpic(p)) {
                EpicSupport.vanilla(p, true);
                if (d.efOff.add(id)) d.setDirty();
            }
        } else {
            if (LOCKED.remove(id)) Net.send(p, new Net.Lock(false));
            if (d.efOff.remove(id)) {
                d.setDirty();
                EpicSupport.vanilla(p, false);
            }
        }
    }

    /** cada medio segundo: se fuerza el estado de todos (modelo puesto = Epic Fight apagado y bloqueado; sin modelo = se devuelve) */
    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !Modelos.EPIC || ++tick % 10 != 0) return;
        Data d = Data.get(e.getServer());
        for (ServerPlayer p : e.getServer().getPlayerList().getPlayers()) enforce(p, d);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) { LOCKED.remove(e.getEntity().getUUID()); }

    private static void say(CommandSourceStack s, String m) { s.sendSuccess(() -> Component.literal(m), true); }

    private static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("modelos").requires(s -> s.hasPermission(2))
                .then(Commands.literal("gui").executes(c -> { sendMenu(c.getSource().getPlayerOrException(), true); return 1; }))
                .then(Commands.literal("recargar").executes(c -> { say(c.getSource(), "Modelos en la carpeta: " + Store.list().size() + " (" + String.join(", ", Store.list()) + ")"); return 1; }))
                .then(Commands.literal("lista").executes(c -> {
                    Data dd = Data.get(c.getSource().getServer());
                    say(c.getSource(), "Modelos disponibles: " + Store.list());
                    for (ServerPlayer p : c.getSource().getServer().getPlayerList().getPlayers())
                        if (dd.applied.containsKey(p.getUUID())) say(c.getSource(), "  " + p.getGameProfile().getName() + " -> " + dd.applied.get(p.getUUID()));
                    return 1;
                }))
                .then(Commands.literal("poner").then(Commands.argument("jugadores", EntityArgument.players())
                        .then(Commands.argument("modelo", StringArgumentType.word()).suggests(MODELS)
                                .executes(c -> apply(c.getSource(), EntityArgument.getPlayers(c, "jugadores"), StringArgumentType.getString(c, "modelo"), false))
                                .then(Commands.literal("temporal").executes(c -> apply(c.getSource(), EntityArgument.getPlayers(c, "jugadores"), StringArgumentType.getString(c, "modelo"), true))))))
                .then(Commands.literal("quitar").then(Commands.argument("jugadores", EntityArgument.players())
                        .executes(c -> remove(c.getSource(), EntityArgument.getPlayers(c, "jugadores"))))));
    }

    private static boolean run(CommandSourceStack src, String cmd, StringBuilder err) {
        try {
            src.getServer().getCommands().getDispatcher().execute(cmd, src.getServer().createCommandSourceStack().withSuppressedOutput());
            return true;
        } catch (CommandSyntaxException ex) {
            err.append(ex.getRawMessage().getString());
            return false;
        }
    }

    private static int apply(CommandSourceStack src, Collection<ServerPlayer> ps, String model, boolean temp) {
        MinecraftServer s = src.getServer();
        if (!cpmPresent(s)) { src.sendFailure(Component.literal("Custom Player Models no esta instalado en el servidor (no existe /cpm)")); return 0; }
        String b64 = Store.read(model);
        if (b64 == null || b64.isEmpty()) { src.sendFailure(Component.literal("No encuentro el modelo \"" + model + "\" en config/modelos/")); return 0; }
        Data d = Data.get(s);
        int ok = 0;
        for (ServerPlayer p : ps) {
            String name = p.getGameProfile().getName();
            // forzado ("-f"): el jugador no puede cambiarlo. Temporal: ademas se borra al desconectarse. Se prueban las dos formas de escribir las banderas.
            String[] flags = temp ? new String[]{"-f -t", "-ft", "-t"} : new String[]{"-f"};
            StringBuilder err = new StringBuilder();
            boolean done = false;
            for (String f : flags) {
                err.setLength(0);
                if (run(src, "cpm setskin " + f + " " + name + " " + b64, err)) { done = true; break; }
            }
            if (!done) { src.sendFailure(Component.literal("CPM no acepto el modelo para " + name + ": " + err)); continue; }
            d.applied.put(p.getUUID(), model);
            if (Modelos.EPIC) enforce(p, d);
            ok++;
        }
        d.setDirty();
        if (ok > 0) say(src, "Modelo " + model + (temp ? " (temporal)" : "") + " puesto a " + ok + " jugador(es)" + (Modelos.EPIC ? "; Epic Fight apagado para ellos" : ""));
        return ok;
    }

    private static int remove(CommandSourceStack src, Collection<ServerPlayer> ps) {
        MinecraftServer s = src.getServer();
        Data d = Data.get(s);
        int ok = 0;
        for (ServerPlayer p : ps) {
            StringBuilder err = new StringBuilder();
            if (cpmPresent(s) && !run(src, "cpm setskin -r " + p.getGameProfile().getName(), err)) {
                src.sendFailure(Component.literal("CPM no pudo quitar el modelo de " + p.getGameProfile().getName() + ": " + err));
                continue;
            }
            d.applied.remove(p.getUUID());
            if (Modelos.EPIC) enforce(p, d);
            ok++;
        }
        d.setDirty();
        if (ok > 0) say(src, "Modelo quitado a " + ok + " jugador(es)" + (Modelos.EPIC ? "; Epic Fight vuelve a activarse" : ""));
        return ok;
    }
}
