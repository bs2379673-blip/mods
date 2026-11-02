package com.starlevi.starriders.server;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.starlevi.starriders.Config;
import com.starlevi.starriders.PhoenixEntity;
import com.starlevi.starriders.RiderData;
import com.starlevi.starriders.WhistleItem;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.List;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * /starriders lista (todos)
 * /starriders dar &lt;jugadores&gt; [tipo] | silbato &lt;jugador&gt; | quitar &lt;jugador&gt; &lt;n|todos&gt; |
 * salvaje &lt;pos&gt; | recargar (admins)
 */
public final class Commands {
    private static final List<String> TYPES = Mounts.TYPES;

    private Commands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(literal("starriders")
                .then(literal("lista").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    List<RiderData.Mount> ms = Mounts.data(p).mounts;
                    if (ms.isEmpty()) {
                        c.getSource().sendSuccess(() -> Component.literal("Todavía no tenés monturas"), false);
                        return 0;
                    }
                    for (int i = 0; i < ms.size(); i++) {
                        int n = i + 1;
                        String name = Mounts.displayName(ms.get(i).type());
                        c.getSource().sendSuccess(() -> Component.literal(" " + n + ". " + name), false);
                    }
                    return ms.size();
                }))
                .then(literal("dar").requires(s -> s.hasPermission(2))
                        .then(argument("jugadores", EntityArgument.players())
                                .executes(c -> give(c.getSource(), EntityArgument.getPlayers(c, "jugadores"), "fenix"))
                                .then(argument("tipo", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(TYPES, b))
                                        .executes(c -> give(c.getSource(), EntityArgument.getPlayers(c, "jugadores"),
                                                StringArgumentType.getString(c, "tipo"))))))
                .then(literal("silbato").requires(s -> s.hasPermission(2))
                        .then(argument("jugador", EntityArgument.player()).executes(c -> {
                            ServerPlayer p = EntityArgument.getPlayer(c, "jugador");
                            for (RiderData.Mount m : Mounts.data(p).mounts) {
                                p.getInventory().placeItemBackInInventory(WhistleItem.make(p, m));
                            }
                            c.getSource().sendSuccess(() -> Component.literal("Silbatos nuevos entregados"), true);
                            return 1;
                        })))
                .then(literal("quitar").requires(s -> s.hasPermission(2))
                        .then(argument("jugador", EntityArgument.player())
                                .then(literal("todos").executes(c -> {
                                    ServerPlayer p = EntityArgument.getPlayer(c, "jugador");
                                    for (RiderData.Mount m : Mounts.data(p).mounts) {
                                        PhoenixEntity ph = Mounts.live().get(m.id());
                                        if (ph != null) {
                                            Mounts.recall(ph, false);
                                        }
                                    }
                                    Mounts.data(p).mounts.clear();
                                    c.getSource().sendSuccess(() -> Component.literal("Monturas quitadas"), true);
                                    return 1;
                                }))
                                .then(argument("n", IntegerArgumentType.integer(1)).executes(c -> {
                                    ServerPlayer p = EntityArgument.getPlayer(c, "jugador");
                                    List<RiderData.Mount> ms = Mounts.data(p).mounts;
                                    int n = IntegerArgumentType.getInteger(c, "n");
                                    if (n > ms.size()) {
                                        c.getSource().sendFailure(Component.literal("No tiene tantas monturas"));
                                        return 0;
                                    }
                                    RiderData.Mount m = ms.remove(n - 1);
                                    PhoenixEntity ph = Mounts.live().get(m.id());
                                    if (ph != null) {
                                        Mounts.recall(ph, false);
                                    }
                                    c.getSource().sendSuccess(() -> Component.literal("Montura quitada"), true);
                                    return 1;
                                }))))
                .then(literal("llamar").requires(s -> s.hasPermission(2))
                        .then(argument("jugador", EntityArgument.player()).executes(c -> {
                            ServerPlayer p = EntityArgument.getPlayer(c, "jugador");
                            List<RiderData.Mount> ms = Mounts.data(p).mounts;
                            if (ms.isEmpty()) {
                                c.getSource().sendFailure(Component.literal("No tiene monturas"));
                                return 0;
                            }
                            Mounts.whistle(p, ms.get(0).id());
                            return 1;
                        })))
                .then(literal("salvaje").requires(s -> s.hasPermission(2))
                        .then(argument("pos", Vec3Argument.vec3()).executes(c -> {
                            Vec3 at = Vec3Argument.getVec3(c, "pos");
                            Mounts.spawnWild(c.getSource().getLevel(), at, null);
                            c.getSource().sendSuccess(() -> Component.literal("Apareció una montura salvaje"), true);
                            return 1;
                        }).then(argument("tipo", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(TYPES, b))
                                .executes(c -> {
                                    String type = StringArgumentType.getString(c, "tipo");
                                    if (!TYPES.contains(type)) {
                                        c.getSource().sendFailure(Component.literal("No existe la montura " + type));
                                        return 0;
                                    }
                                    Mounts.spawnWild(c.getSource().getLevel(), Vec3Argument.getVec3(c, "pos"), type);
                                    c.getSource().sendSuccess(() -> Component.literal("Apareció: " + Mounts.displayName(type) + " salvaje"), true);
                                    return 1;
                                }))))
                .then(literal("recargar").requires(s -> s.hasPermission(2)).executes(c -> {
                    Config.load();
                    c.getSource().sendSuccess(() -> Component.literal("StarRiders: configuración recargada"), true);
                    return 1;
                })));
    }

    private static int give(CommandSourceStack src, java.util.Collection<ServerPlayer> players, String type) {
        if (!TYPES.contains(type)) {
            src.sendFailure(Component.literal("No existe la montura " + type));
            return 0;
        }
        for (ServerPlayer p : players) {
            Mounts.give(p, type);
        }
        src.sendSuccess(() -> Component.literal("Montura " + type + " dada a " + players.size() + " jugador(es)"), true);
        return players.size();
    }
}
