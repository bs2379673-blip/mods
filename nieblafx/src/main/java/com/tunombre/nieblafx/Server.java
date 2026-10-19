package com.tunombre.nieblafx;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Locale;

public final class Server {
    private Server() { }

    private static void say(CommandSourceStack s, String m) { s.sendSuccess(() -> Component.literal(m), false); }

    private static void sync() { Fog.save(); Net.sendAll(Fog.json()); }

    private static int setv(CommandSourceStack s, String k, String v) {
        String err = Fog.set(k, v);
        if (err != null) { s.sendFailure(Component.literal(err)); return 0; }
        sync();
        say(s, "Niebla: " + k + " = " + v);
        return 1;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> num(String name, String key) {
        return Commands.literal(name).then(Commands.argument("valor", FloatArgumentType.floatArg()).executes(c ->
                setv(c.getSource(), key, String.format(Locale.ROOT, "%s", FloatArgumentType.getFloat(c, "valor")))));
    }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent e) {
        var root = Commands.literal("niebla").requires(s -> s.hasPermission(2));
        root.then(Commands.literal("estado").executes(c -> {
            Fog.State f = Fog.S;
            say(c.getSource(), String.format(Locale.ROOT, "Niebla global %s | color %s | densidad %.4f | distancia %.0f | base Y %.0f caida %.3f | ruido %.2f | viento %.2f | cielo %.2f | fundido %.1f s | zonas: %d",
                    f.on ? "ACTIVA" : "apagada", f.color, f.density, f.maxDist, f.baseY, f.falloff, f.noise, f.wind, f.sky, f.fade, f.zones.size()));
            return 1;
        }));
        root.then(Commands.literal("on").executes(c -> setv(c.getSource(), "on", "true"))
                .then(Commands.argument("seg", FloatArgumentType.floatArg(0F)).executes(c -> {
                    Fog.set("transicion", String.valueOf(FloatArgumentType.getFloat(c, "seg")));
                    return setv(c.getSource(), "on", "true");
                })));
        root.then(Commands.literal("off").executes(c -> setv(c.getSource(), "on", "false"))
                .then(Commands.argument("seg", FloatArgumentType.floatArg(0F)).executes(c -> {
                    Fog.set("transicion", String.valueOf(FloatArgumentType.getFloat(c, "seg")));
                    return setv(c.getSource(), "on", "false");
                })));
        root.then(Commands.literal("reset").executes(c -> {
            var zones = Fog.S.zones;
            Fog.State n = new Fog.State();
            n.zones = zones;
            Fog.S = n;
            sync();
            say(c.getSource(), "Niebla global restablecida (las zonas se conservan).");
            return 1;
        }));
        root.then(Commands.literal("color").then(Commands.argument("hex", StringArgumentType.word()).executes(c -> setv(c.getSource(), "color", StringArgumentType.getString(c, "hex")))));
        root.then(num("densidad", "densidad")).then(num("distancia", "distancia")).then(num("ruido", "ruido"))
                .then(num("viento", "viento")).then(num("cielo", "cielo")).then(num("transicion", "transicion"))
                .then(num("base", "base")).then(num("caida", "caida"));

        var zona = Commands.literal("zona");
        zona.then(Commands.literal("crear").then(Commands.argument("id", StringArgumentType.word())
                .executes(c -> create(c.getSource(), StringArgumentType.getString(c, "id"), 40F, 12F, 0.08F))
                .then(Commands.argument("radio", FloatArgumentType.floatArg(1F, 2000F))
                        .executes(c -> create(c.getSource(), StringArgumentType.getString(c, "id"), FloatArgumentType.getFloat(c, "radio"), 12F, 0.08F))
                        .then(Commands.argument("alto", FloatArgumentType.floatArg(1F, 512F))
                                .executes(c -> create(c.getSource(), StringArgumentType.getString(c, "id"), FloatArgumentType.getFloat(c, "radio"), FloatArgumentType.getFloat(c, "alto"), 0.08F))
                                .then(Commands.argument("densidad", FloatArgumentType.floatArg(0F, 5F))
                                        .executes(c -> create(c.getSource(), StringArgumentType.getString(c, "id"), FloatArgumentType.getFloat(c, "radio"), FloatArgumentType.getFloat(c, "alto"), FloatArgumentType.getFloat(c, "densidad"))))))));
        RequiredArgumentBuilder<CommandSourceStack, String> idArg = Commands.argument("id", StringArgumentType.word())
                .suggests((c, b) -> SharedSuggestionProvider.suggest(Fog.S.zones.stream().map(z -> z.id), b));
        zona.then(Commands.literal("borrar").then(idArg.executes(c -> {
            String id = StringArgumentType.getString(c, "id");
            boolean ok = Fog.S.zones.removeIf(z -> z.id.equalsIgnoreCase(id));
            if (!ok) { c.getSource().sendFailure(Component.literal("no existe la zona " + id)); return 0; }
            sync(); say(c.getSource(), "Zona " + id + " borrada."); return 1;
        })));
        zona.then(Commands.literal("borrartodas").executes(c -> { Fog.S.zones.clear(); sync(); say(c.getSource(), "Todas las zonas borradas."); return 1; }));
        zona.then(Commands.literal("aqui").then(Commands.argument("id", StringArgumentType.word())
                .suggests((c, b) -> SharedSuggestionProvider.suggest(Fog.S.zones.stream().map(z -> z.id), b)).executes(c -> {
                    Fog.Zone z = Fog.zone(StringArgumentType.getString(c, "id"));
                    if (z == null) { c.getSource().sendFailure(Component.literal("no existe esa zona")); return 0; }
                    Vec3 p = c.getSource().getPosition();
                    z.x = p.x; z.y = p.y; z.z = p.z; z.dim = c.getSource().getLevel().dimension().location().toString();
                    sync(); say(c.getSource(), "Zona " + z.id + " movida a tu posicion."); return 1;
                })));
        zona.then(Commands.literal("lista").executes(c -> {
            if (Fog.S.zones.isEmpty()) say(c.getSource(), "No hay zonas.");
            for (Fog.Zone z : Fog.S.zones)
                say(c.getSource(), String.format(Locale.ROOT, "%s%s [%s] (%.0f %.0f %.0f) radio %.0f alto %.0f densidad %.3f color %s", z.on ? "" : "(apagada) ", z.id, z.dim, z.x, z.y, z.z, z.radius, z.height, z.density, z.color));
            return 1;
        }));
        zona.then(Commands.literal("set").then(Commands.argument("id", StringArgumentType.word())
                .suggests((c, b) -> SharedSuggestionProvider.suggest(Fog.S.zones.stream().map(z -> z.id), b))
                .then(Commands.argument("prop", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(new String[]{"radio", "alto", "densidad", "color", "on"}, b))
                        .then(Commands.argument("valor", StringArgumentType.word()).executes(c -> {
                            String err = Fog.setZone(StringArgumentType.getString(c, "id"), StringArgumentType.getString(c, "prop"), StringArgumentType.getString(c, "valor"));
                            if (err != null) { c.getSource().sendFailure(Component.literal(err)); return 0; }
                            sync(); say(c.getSource(), "Zona actualizada."); return 1;
                        })))));
        root.then(zona);
        e.getDispatcher().register(root);
    }

    private static int create(CommandSourceStack s, String id, float radio, float alto, float dens) {
        if (Fog.zone(id) != null) { s.sendFailure(Component.literal("ya existe la zona " + id + " (usa /niebla zona set o borrala)")); return 0; }
        Vec3 p = s.getPosition();
        Fog.Zone z = new Fog.Zone();
        z.id = id; z.x = p.x; z.y = p.y; z.z = p.z; z.radius = radio; z.height = alto; z.density = dens;
        z.dim = s.getLevel().dimension().location().toString();
        Fog.S.zones.add(z);
        sync();
        say(s, "Zona " + id + " creada en tu posicion (radio " + radio + ", alto " + alto + ").");
        return 1;
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) Net.send(p, Fog.json());
    }
}
