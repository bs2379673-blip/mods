package com.starlevi.starfest.server;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.starlevi.starfest.net.Net;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import java.util.List;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * /starfest musica lista | tocar <cancion> [radio] [volumen] | todas [radio] [volumen] |
 * aleatorio [radio] [volumen] | siguiente | parar ;  /starfest fuegos
 * La zona se centra donde estas parado (o donde corre el comando).
 */
public final class Commands {
    public static final String ZONE = "principal";

    private Commands() {
    }

    private static final SuggestionProvider<CommandSourceStack> SONGS =
            (ctx, b) -> SharedSuggestionProvider.suggest(MusicLibrary.names(), b);
    private static final SuggestionProvider<CommandSourceStack> SKIES = (ctx, b) -> {
        SkyLibrary.scan();
        return SharedSuggestionProvider.suggest(SkyLibrary.names(), b);
    };
    private static final SuggestionProvider<CommandSourceStack> DANCES =
            (ctx, b) -> SharedSuggestionProvider.suggest(List.of("dance", "happy", "wave", "laugh", "high_five"), b);

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(literal("starfest").requires(net.minecraft.commands.Commands.hasPermission(net.minecraft.commands.Commands.LEVEL_GAMEMASTERS))
                .then(literal("musica")
                        .then(literal("lista").executes(Commands::list))
                        .then(literal("tocar").then(argument("cancion", StringArgumentType.word()).suggests(SONGS)
                                .executes(c -> playOne(c, 48, 1.0f))
                                .then(argument("radio", IntegerArgumentType.integer(4, 512))
                                        .executes(c -> playOne(c, IntegerArgumentType.getInteger(c, "radio"), 1.0f))
                                        .then(argument("volumen", FloatArgumentType.floatArg(0.05f, 1.0f))
                                                .executes(c -> playOne(c, IntegerArgumentType.getInteger(c, "radio"),
                                                        FloatArgumentType.getFloat(c, "volumen")))))))
                        .then(listMode("todas", ShowServer.Mode.TODAS))
                        .then(listMode("aleatorio", ShowServer.Mode.ALEATORIO))
                        .then(literal("siguiente").executes(c -> {
                            ShowServer.Zone z = ShowServer.zone(ZONE);
                            if (z == null || !ShowServer.next(z)) {
                                c.getSource().sendFailure(Component.literal("No hay musica sonando"));
                                return 0;
                            }
                            return 1;
                        }))
                        .then(literal("parar").executes(c -> {
                            ShowServer.stop(ZONE);
                            c.getSource().sendSuccess(() -> Component.literal("Musica detenida"), true);
                            return 1;
                        })))
                .then(literal("cielo")
                        .then(literal("lista").executes(c -> {
                            SkyLibrary.scan();
                            List<String> n = SkyLibrary.names();
                            c.getSource().sendSuccess(() -> Component.literal("Cielos (" + n.size() + "): " + String.join(", ", n)
                                    + "  -  carpeta: " + SkyLibrary.folder()), false);
                            return n.size();
                        }))
                        .then(literal("parar").executes(c -> {
                            ShowServer.stopSky(ZONE);
                            c.getSource().sendSuccess(() -> Component.literal("Cielo quitado"), true);
                            return 1;
                        }))
                        .then(argument("nombre", StringArgumentType.word()).suggests(SKIES)
                                .executes(c -> sky(c, 160))
                                .then(argument("radio", IntegerArgumentType.integer(8, 2048))
                                        .executes(c -> sky(c, IntegerArgumentType.getInteger(c, "radio"))))))
                .then(literal("bailarines")
                        .then(literal("agregar").then(argument("quienes", net.minecraft.commands.arguments.EntityArgument.entities())
                                .executes(c -> addDancers(c, "dance"))
                                .then(argument("animacion", StringArgumentType.greedyString()).suggests(DANCES)
                                        .executes(c -> addDancers(c, StringArgumentType.getString(c, "animacion"))))))
                        .then(literal("quitar").then(argument("quienes", net.minecraft.commands.arguments.EntityArgument.entities())
                                .executes(c -> {
                                    int n = 0;
                                    for (net.minecraft.world.entity.Entity e : net.minecraft.commands.arguments.EntityArgument.getEntities(c, "quienes")) {
                                        if (Dancers.remove(c.getSource().getServer(), e)) {
                                            n++;
                                        }
                                    }
                                    int k = n;
                                    c.getSource().sendSuccess(() -> Component.literal(k + " ya no bailan con la musica"), true);
                                    return n;
                                })))
                        .then(literal("lista").executes(c -> {
                            int n = Dancers.all().size();
                            c.getSource().sendSuccess(() -> Component.literal(n + " bailarines" + (Dancers.SCENES ? "" : " (hace falta StarScenes para que bailen)")), false);
                            return n;
                        }))
                        .then(literal("limpiar").executes(c -> {
                            int n = Dancers.clear(c.getSource().getServer());
                            c.getSource().sendSuccess(() -> Component.literal("Se quitaron " + n + " bailarines"), true);
                            return n;
                        })))
                .then(literal("fuegos").executes(c -> fireworks(c, 48))
                        .then(argument("radio", IntegerArgumentType.integer(4, 512))
                                .executes(c -> fireworks(c, IntegerArgumentType.getInteger(c, "radio"))))));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> listMode(String word, ShowServer.Mode mode) {
        return literal(word).executes(c -> playList(c, mode, 48, 1.0f))
                .then(argument("radio", IntegerArgumentType.integer(4, 512))
                        .executes(c -> playList(c, mode, IntegerArgumentType.getInteger(c, "radio"), 1.0f))
                        .then(argument("volumen", FloatArgumentType.floatArg(0.05f, 1.0f))
                                .executes(c -> playList(c, mode, IntegerArgumentType.getInteger(c, "radio"),
                                        FloatArgumentType.getFloat(c, "volumen")))));
    }

    private static int list(CommandContext<CommandSourceStack> c) {
        MusicLibrary.scan();
        List<String> n = MusicLibrary.names();
        c.getSource().sendSuccess(() -> Component.literal(n.isEmpty()
                ? "No hay canciones. Pone archivos .ogg en " + MusicLibrary.folder()
                : "Canciones (" + n.size() + "): " + String.join(", ", n)), false);
        return n.size();
    }

    private static int playOne(CommandContext<CommandSourceStack> c, int radius, float volume) {
        MusicLibrary.scan();
        String name = StringArgumentType.getString(c, "cancion");
        if (MusicLibrary.get(name) == null) {
            c.getSource().sendFailure(Component.literal("No existe la cancion '" + name + "' (mira /starfest musica lista)"));
            return 0;
        }
        return start(c, ShowServer.Mode.UNA, List.of(name.toLowerCase(java.util.Locale.ROOT)), radius, volume);
    }

    private static int playList(CommandContext<CommandSourceStack> c, ShowServer.Mode mode, int radius, float volume) {
        MusicLibrary.scan();
        List<String> n = MusicLibrary.names();
        if (n.isEmpty()) {
            c.getSource().sendFailure(Component.literal("No hay canciones en " + MusicLibrary.folder()));
            return 0;
        }
        return start(c, mode, n, radius, volume);
    }

    private static int start(CommandContext<CommandSourceStack> c, ShowServer.Mode mode, List<String> tracks, int radius, float volume) {
        CommandSourceStack s = c.getSource();
        BlockPos center = BlockPos.containing(s.getPosition());
        ShowServer.Zone z = ShowServer.play(ZONE, s.getLevel().dimension(), center, radius, volume, mode, tracks);
        if (z == null || z.track == null) {
            s.sendFailure(Component.literal("No se pudo poner la musica"));
            return 0;
        }
        String what = z.track.name();
        s.sendSuccess(() -> Component.literal("Suena '" + what + "' en un radio de " + radius + " bloques"), true);
        return 1;
    }

    private static int sky(CommandContext<CommandSourceStack> c, int radius) {
        SkyLibrary.scan();
        String name = StringArgumentType.getString(c, "nombre");
        if (SkyLibrary.get(name) == null) {
            c.getSource().sendFailure(Component.literal("No existe el cielo '" + name + "' (mira /starfest cielo lista)"));
            return 0;
        }
        CommandSourceStack s = c.getSource();
        ShowServer.sky(ZONE, s.getLevel().dimension(), BlockPos.containing(s.getPosition()), radius, name.toLowerCase(java.util.Locale.ROOT));
        s.sendSuccess(() -> Component.literal("Cielo '" + name + "' en un radio de " + radius + " bloques"), true);
        return 1;
    }

    private static int addDancers(CommandContext<CommandSourceStack> c, String anim) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        if (!Dancers.SCENES) {
            c.getSource().sendFailure(Component.literal("Hace falta StarScenes para que bailen"));
            return 0;
        }
        int n = 0;
        for (net.minecraft.world.entity.Entity e : net.minecraft.commands.arguments.EntityArgument.getEntities(c, "quienes")) {
            if (!(e instanceof ServerPlayer)) {
                Dancers.add(e, anim.trim());
                n++;
            }
        }
        int k = n;
        c.getSource().sendSuccess(() -> Component.literal(k + " bailaran '" + anim.trim() + "' cuando suene musica"), true);
        return n;
    }

    private static int fireworks(CommandContext<CommandSourceStack> c, int radius) {
        BlockPos p = BlockPos.containing(c.getSource().getPosition());
        for (ServerPlayer pl : c.getSource().getServer().getPlayerList().getPlayers()) {
            if (pl.level() == c.getSource().getLevel() && pl.blockPosition().distSqr(p) < (double) (radius + 64) * (radius + 64)) {
                ServerPlayNetworking.send(pl, new Net.Fx("fuegos", p.getX(), p.getY(), p.getZ(), radius));
            }
        }
        return 1;
    }
}
