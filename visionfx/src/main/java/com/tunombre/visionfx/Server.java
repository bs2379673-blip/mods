package com.tunombre.visionfx;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Collection;
import java.util.function.BiConsumer;

/** logica del servidor: guarda el estado, lo sincroniza y expone /visionfx */
public final class Server {
    private Server() { }

    static Params effective(VisionData d, ServerPlayer p) {
        Params e = d.global.copy();
        Boolean o = d.overrides.get(p.getUUID());
        if (o != null) e.enabled = o;
        Boolean q = d.ps1Over.get(p.getUUID());
        if (q != null) e.ps1 = q;
        Integer m = d.modeOver.get(p.getUUID());
        if (m != null) e.mode = m;
        return e;
    }

    static void push(ServerPlayer p) { Net.send(p, effective(VisionData.get(p.server), p)); }

    static void pushAll(net.minecraft.server.MinecraftServer s) {
        VisionData d = VisionData.get(s);
        d.setDirty();
        for (ServerPlayer p : s.getPlayerList().getPlayers()) Net.send(p, effective(d, p));
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) { if (e.getEntity() instanceof ServerPlayer p) push(p); }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent e) { if (e.getEntity() instanceof ServerPlayer p) push(p); }

    @SubscribeEvent
    public static void onDim(PlayerEvent.PlayerChangedDimensionEvent e) { if (e.getEntity() instanceof ServerPlayer p) push(p); }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent e) { register(e.getDispatcher()); }

    private static void say(CommandSourceStack s, String m) { s.sendSuccess(() -> Component.literal(m), true); }

    private static LiteralArgumentBuilder<CommandSourceStack> num(String name, BiConsumer<Params, Float> set, float max) {
        return Commands.literal(name).then(Commands.argument("valor", FloatArgumentType.floatArg(0F, max)).executes(c -> {
            var s = c.getSource().getServer();
            float v = FloatArgumentType.getFloat(c, "valor");
            set.accept(VisionData.get(s).global, v);
            pushAll(s);
            say(c.getSource(), "VisionFX: " + name + " = " + v);
            return 1;
        }));
    }

    private static void register(CommandDispatcher<CommandSourceStack> disp) {
        var root = Commands.literal("visionfx").requires(s -> s.hasPermission(2));
        root.then(Commands.literal("on").executes(c -> {
            var s = c.getSource().getServer(); VisionData.get(s).global.enabled = true; pushAll(s); say(c.getSource(), "VisionFX encendido para todos"); return 1;
        }));
        root.then(Commands.literal("off").executes(c -> {
            var s = c.getSource().getServer(); VisionData.get(s).global.enabled = false; pushAll(s); say(c.getSource(), "VisionFX apagado para todos"); return 1;
        }));
        root.then(Commands.literal("reset").executes(c -> {
            var s = c.getSource().getServer(); var d = VisionData.get(s); d.global = new Params(); d.overrides.clear(); d.ps1Over.clear(); d.modeOver.clear(); pushAll(s);
            say(c.getSource(), "VisionFX: valores por defecto y sin anulaciones por jugador"); return 1;
        }));
        root.then(Commands.literal("status").executes(c -> {
            var s = c.getSource().getServer(); var d = VisionData.get(s); Params g = d.global;
            say(c.getSource(), String.format("VisionFX %s | fuerza %.2f | contraste %.2f | desaturar %.2f | brillo %.2f | cromatica %.3f | vineta %.2f | oscuridad %s | anulaciones: %d | PS1 %s (res %.0f, niebla %.0f, dither %.2f, anulaciones %d) | modo de evento: %s (anulaciones %d) | saturacion %.2f | gamma %.2f | tonos %.2f",
                    g.enabled ? "ENCENDIDO" : "APAGADO", g.strength, g.contrast, g.desaturate, g.brightness, g.chromatic, g.vignette, g.dark ? "si" : "no", d.overrides.size(), g.ps1 ? "ENCENDIDO" : "apagado", g.ps1Res, g.ps1Fog, g.ps1Dither, d.ps1Over.size(), Modes.nameOf(g.mode), d.modeOver.size(), g.saturation, g.gamma, g.toneAmt));
            return 1;
        }));
        var set = Commands.literal("set");
        set.then(num("fuerza", (p, v) -> p.strength = Math.min(1F, v), 1F));
        set.then(num("contraste", (p, v) -> p.contrast = v, 3F));
        set.then(num("desaturar", (p, v) -> p.desaturate = Math.min(1F, v), 1F));
        set.then(num("brillo", (p, v) -> p.brightness = v, 2F));
        set.then(num("cromatica", (p, v) -> p.chromatic = v, 0.2F));
        set.then(num("vineta", (p, v) -> p.vignette = v, 2F));
        set.then(num("saturacion", (p, v) -> p.saturation = v, 2.5F));
        set.then(num("gamma", (p, v) -> p.gamma = Math.max(0.3F, v), 2.5F));
        set.then(num("tonos", (p, v) -> p.toneAmt = Math.min(1F, v), 1F));
        set.then(Commands.literal("oscuridad").then(Commands.argument("valor", BoolArgumentType.bool()).executes(c -> {
            var s = c.getSource().getServer(); boolean v = BoolArgumentType.getBool(c, "valor");
            VisionData.get(s).global.dark = v; pushAll(s);
            say(c.getSource(), "VisionFX: depende de la oscuridad = " + v); return 1;
        })));
        root.then(set);
        root.then(Commands.literal("player").then(Commands.argument("jugadores", EntityArgument.players())
                .then(Commands.literal("on").executes(c -> over(c.getSource(), EntityArgument.getPlayers(c, "jugadores"), Boolean.TRUE)))
                .then(Commands.literal("off").executes(c -> over(c.getSource(), EntityArgument.getPlayers(c, "jugadores"), Boolean.FALSE)))
                .then(Commands.literal("default").executes(c -> over(c.getSource(), EntityArgument.getPlayers(c, "jugadores"), null)))));
        // ---- plantillas de color: /visionfx plantilla [nombre]
        root.then(Commands.literal("plantilla")
                .executes(c -> {
                    StringBuilder sb = new StringBuilder("Plantillas de color (/visionfx plantilla <nombre>):");
                    Templates.DESC.forEach((k, v) -> sb.append("\n  ").append(k).append(" - ").append(v));
                    say(c.getSource(), sb.toString());
                    return Templates.ALL.size();
                })
                .then(Commands.argument("nombre", StringArgumentType.word())
                        .suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(Templates.ALL.keySet(), b))
                        .executes(c -> {
                            String n = StringArgumentType.getString(c, "nombre").toLowerCase(java.util.Locale.ROOT);
                            Params t = Templates.ALL.get(n);
                            if (t == null) { c.getSource().sendFailure(Component.literal("No existe esa plantilla (usa /visionfx plantilla para ver la lista)")); return 0; }
                            var s = c.getSource().getServer();
                            VisionData d = VisionData.get(s);
                            d.global.applyColors(t);
                            d.global.enabled = true;
                            pushAll(s);
                            say(c.getSource(), "VisionFX: plantilla '" + n + "' aplicada (" + Templates.DESC.get(n) + ")");
                            return 1;
                        })));

        // ---- modos de evento: /visionfx modo off | <nombre> [jugadores]
        root.then(Commands.literal("modo")
                .executes(c -> {
                    StringBuilder sb = new StringBuilder("Modos de evento (/visionfx modo <nombre> [jugadores] | off):");
                    Modes.DESC.forEach((k, v) -> sb.append("\n  ").append(k).append(" - ").append(v));
                    say(c.getSource(), sb.toString());
                    return Modes.IDS.size();
                })
                .then(Commands.literal("off")
                        .executes(c -> setMode(c.getSource(), 0, null))
                        .then(Commands.argument("jugadores", EntityArgument.players()).executes(c -> setMode(c.getSource(), 0, EntityArgument.getPlayers(c, "jugadores")))))
                .then(Commands.argument("nombre", StringArgumentType.word())
                        .suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(Modes.IDS.keySet(), b))
                        .executes(c -> setMode(c.getSource(), modeId(c.getSource(), StringArgumentType.getString(c, "nombre")), null))
                        .then(Commands.argument("jugadores", EntityArgument.players())
                                .executes(c -> setMode(c.getSource(), modeId(c.getSource(), StringArgumentType.getString(c, "nombre")), EntityArgument.getPlayers(c, "jugadores"))))));

        // ---- vision PlayStation 1: /visionfx ps1 on|off | player <jugadores> on|off|default | set resolucion|niebla|dither <valor>
        var ps1 = Commands.literal("ps1");
        ps1.then(Commands.literal("on").executes(c -> {
            var s = c.getSource().getServer(); VisionData.get(s).global.ps1 = true; pushAll(s); say(c.getSource(), "Vision PS1 encendida para todos"); return 1;
        }));
        ps1.then(Commands.literal("off").executes(c -> {
            var s = c.getSource().getServer(); VisionData.get(s).global.ps1 = false; pushAll(s); say(c.getSource(), "Vision PS1 apagada para todos"); return 1;
        }));
        ps1.then(Commands.literal("player").then(Commands.argument("jugadores", EntityArgument.players())
                .then(Commands.literal("on").executes(c -> overPs1(c.getSource(), EntityArgument.getPlayers(c, "jugadores"), Boolean.TRUE)))
                .then(Commands.literal("off").executes(c -> overPs1(c.getSource(), EntityArgument.getPlayers(c, "jugadores"), Boolean.FALSE)))
                .then(Commands.literal("default").executes(c -> overPs1(c.getSource(), EntityArgument.getPlayers(c, "jugadores"), null)))));
        var ps1set = Commands.literal("set");
        ps1set.then(num("resolucion", (p, v) -> p.ps1Res = Math.max(60F, v), 1080F));
        ps1set.then(num("niebla", (p, v) -> p.ps1Fog = v, 512F));
        ps1set.then(num("dither", (p, v) -> p.ps1Dither = Math.min(1F, v), 1F));
        ps1.then(ps1set);
        root.then(ps1);
        disp.register(root);
    }

    private static int over(CommandSourceStack src, Collection<ServerPlayer> ps, Boolean v) {
        var s = src.getServer(); var d = VisionData.get(s);
        for (ServerPlayer p : ps) { if (v == null) d.overrides.remove(p.getUUID()); else d.overrides.put(p.getUUID(), v); }
        pushAll(s);
        say(src, "VisionFX: " + ps.size() + " jugador(es) -> " + (v == null ? "segun el valor global" : v ? "forzado ENCENDIDO" : "forzado APAGADO"));
        return ps.size();
    }

    private static int overPs1(CommandSourceStack src, Collection<ServerPlayer> ps, Boolean v) {
        var s = src.getServer(); var d = VisionData.get(s);
        for (ServerPlayer p : ps) { if (v == null) d.ps1Over.remove(p.getUUID()); else d.ps1Over.put(p.getUUID(), v); }
        pushAll(s);
        say(src, "Vision PS1: " + ps.size() + " jugador(es) -> " + (v == null ? "segun el valor global" : v ? "forzada ENCENDIDA" : "forzada APAGADA"));
        return ps.size();
    }

    private static int modeId(CommandSourceStack src, String n) {
        Integer id = Modes.IDS.get(n.toLowerCase(java.util.Locale.ROOT));
        if (id == null) src.sendFailure(Component.literal("No existe ese modo (usa /visionfx modo para ver la lista)"));
        return id == null ? -1 : id;
    }

    /** ps == null: para todos (modo global, y se borran las anulaciones); si no, solo para esos jugadores */
    private static int setMode(CommandSourceStack src, int id, Collection<ServerPlayer> ps) {
        if (id < 0) return 0;
        var s = src.getServer(); var d = VisionData.get(s);
        if (ps == null) {
            d.global.mode = id; d.modeOver.clear();
            pushAll(s);
            say(src, id == 0 ? "VisionFX: modo de evento apagado para todos" : "VisionFX: modo '" + Modes.nameOf(id) + "' para todos");
            return 1;
        }
        for (ServerPlayer p : ps) d.modeOver.put(p.getUUID(), id);
        pushAll(s);
        say(src, "VisionFX: " + ps.size() + " jugador(es) -> modo " + (id == 0 ? "apagado" : "'" + Modes.nameOf(id) + "'"));
        return ps.size();
    }
}
