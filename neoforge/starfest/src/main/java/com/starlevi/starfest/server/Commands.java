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
import net.neoforged.neoforge.network.PacketDistributor;

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

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(literal("starfest").requires(s -> s.hasPermission(2))
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

    private static int fireworks(CommandContext<CommandSourceStack> c, int radius) {
        BlockPos p = BlockPos.containing(c.getSource().getPosition());
        for (ServerPlayer pl : c.getSource().getServer().getPlayerList().getPlayers()) {
            if (pl.level() == c.getSource().getLevel() && pl.blockPosition().distSqr(p) < (double) (radius + 64) * (radius + 64)) {
                PacketDistributor.sendToPlayer(pl, new Net.Fx("fuegos", p.getX(), p.getY(), p.getZ(), radius));
            }
        }
        return 1;
    }
}
