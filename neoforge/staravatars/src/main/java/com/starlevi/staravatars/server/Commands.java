package com.starlevi.staravatars.server;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.starlevi.staravatars.net.Net;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Collection;
import java.util.List;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * /staravatars lista | usar <personaje> [jugadores] | quitar [jugadores] | recargar.
 * Cada jugador puede ponerse o sacarse un personaje a si mismo; para otros hace falta ser operador.
 */
public final class Commands {
    private Commands() {
    }

    private static int use(CommandSourceStack src, Collection<ServerPlayer> who, String avatar) {
        if (!avatar.isEmpty() && !AvatarLibrary.exists(avatar)) {
            src.sendFailure(Component.literal("No existe el personaje " + avatar + " (mira /staravatars lista)"));
            return 0;
        }
        for (ServerPlayer p : who) {
            Outfits.set(p, avatar);
        }
        src.sendSuccess(() -> Component.literal(avatar.isEmpty() ? "Personaje quitado (" + who.size() + ")"
                : "Personaje " + avatar + " puesto (" + who.size() + ")"), true);
        return who.size();
    }

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(literal("staravatars")
                .then(literal("lista").executes(c -> {
                    List<Net.Avatar> all = AvatarLibrary.all();
                    c.getSource().sendSuccess(() -> Component.literal("Personajes (" + all.size() + "):"), false);
                    for (Net.Avatar a : all) {
                        c.getSource().sendSuccess(() -> Component.literal(" " + a.id() + " — " + a.name()), false);
                    }
                    return all.size();
                }))
                .then(literal("usar")
                        .then(argument("personaje", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(AvatarLibrary.all().stream().map(Net.Avatar::id), b))
                                .executes(c -> use(c.getSource(), List.of(c.getSource().getPlayerOrException()),
                                        StringArgumentType.getString(c, "personaje")))
                                .then(argument("jugadores", EntityArgument.players()).requires(s -> s.hasPermission(2))
                                        .executes(c -> use(c.getSource(), EntityArgument.getPlayers(c, "jugadores"),
                                                StringArgumentType.getString(c, "personaje"))))))
                .then(literal("quitar")
                        .executes(c -> use(c.getSource(), List.of(c.getSource().getPlayerOrException()), ""))
                        .then(argument("jugadores", EntityArgument.players()).requires(s -> s.hasPermission(2))
                                .executes(c -> use(c.getSource(), EntityArgument.getPlayers(c, "jugadores"), ""))))
                .then(literal("recargar").requires(s -> s.hasPermission(2)).executes(c -> {
                    int n = AvatarLibrary.scan();
                    PacketDistributor.sendToAllPlayers(new Net.Index(AvatarLibrary.all()));
                    c.getSource().sendSuccess(() -> Component.literal("StarAvatars: " + n + " personajes"), true);
                    return n;
                })));
    }
}
