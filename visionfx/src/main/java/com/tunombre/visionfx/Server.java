package com.tunombre.visionfx;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
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
            var s = c.getSource().getServer(); var d = VisionData.get(s); d.global = new Params(); d.overrides.clear(); pushAll(s);
            say(c.getSource(), "VisionFX: valores por defecto y sin anulaciones por jugador"); return 1;
        }));
        root.then(Commands.literal("status").executes(c -> {
            var s = c.getSource().getServer(); var d = VisionData.get(s); Params g = d.global;
            say(c.getSource(), String.format("VisionFX %s | fuerza %.2f | contraste %.2f | desaturar %.2f | brillo %.2f | cromatica %.3f | vineta %.2f | oscuridad %s | anulaciones: %d",
                    g.enabled ? "ENCENDIDO" : "APAGADO", g.strength, g.contrast, g.desaturate, g.brightness, g.chromatic, g.vignette, g.dark ? "si" : "no", d.overrides.size()));
            return 1;
        }));
        var set = Commands.literal("set");
        set.then(num("fuerza", (p, v) -> p.strength = Math.min(1F, v), 1F));
        set.then(num("contraste", (p, v) -> p.contrast = v, 3F));
        set.then(num("desaturar", (p, v) -> p.desaturate = Math.min(1F, v), 1F));
        set.then(num("brillo", (p, v) -> p.brightness = v, 2F));
        set.then(num("cromatica", (p, v) -> p.chromatic = v, 0.2F));
        set.then(num("vineta", (p, v) -> p.vignette = v, 2F));
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
        disp.register(root);
    }

    private static int over(CommandSourceStack src, Collection<ServerPlayer> ps, Boolean v) {
        var s = src.getServer(); var d = VisionData.get(s);
        for (ServerPlayer p : ps) { if (v == null) d.overrides.remove(p.getUUID()); else d.overrides.put(p.getUUID(), v); }
        pushAll(s);
        say(src, "VisionFX: " + ps.size() + " jugador(es) -> " + (v == null ? "segun el valor global" : v ? "forzado ENCENDIDO" : "forzado APAGADO"));
        return ps.size();
    }
}
