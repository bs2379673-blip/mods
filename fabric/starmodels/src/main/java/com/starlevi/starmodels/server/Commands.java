package com.starlevi.starmodels.server;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.starlevi.starmodels.net.Net;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.List;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * /starmodels lista | usar &lt;personaje&gt; [jugadores] | quitar [jugadores] | recargar.
 * Cada jugador puede ponerse o sacarse un personaje a si mismo; para otros hace falta ser operador.
 */
public final class Commands {
    private Commands() {
    }

    private static int use(CommandSourceStack src, Collection<ServerPlayer> who, String avatar) {
        if (!avatar.isEmpty() && !AvatarLibrary.exists(avatar)) {
            src.sendFailure(Component.literal("No existe el personaje " + avatar + " (mira /starmodels lista)"));
            return 0;
        }
        for (ServerPlayer p : who) {
            Outfits.set(p, Outfits.plain(avatar));
        }
        src.sendSuccess(() -> Component.literal(avatar.isEmpty() ? "Personaje quitado (" + who.size() + ")"
                : "Personaje " + avatar + " puesto (" + who.size() + ")"), true);
        return who.size();
    }

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(literal("starmodels")
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
                                .then(argument("jugadores", EntityArgument.players())
                                        .requires(net.minecraft.commands.Commands.hasPermission(net.minecraft.commands.Commands.LEVEL_GAMEMASTERS))
                                        .executes(c -> use(c.getSource(), EntityArgument.getPlayers(c, "jugadores"),
                                                StringArgumentType.getString(c, "personaje"))))))
                .then(literal("quitar")
                        .executes(c -> use(c.getSource(), List.of(c.getSource().getPlayerOrException()), ""))
                        .then(argument("jugadores", EntityArgument.players())
                                .requires(net.minecraft.commands.Commands.hasPermission(net.minecraft.commands.Commands.LEVEL_GAMEMASTERS))
                                .executes(c -> use(c.getSource(), EntityArgument.getPlayers(c, "jugadores"), ""))))
                .then(literal("recargar")
                        .requires(net.minecraft.commands.Commands.hasPermission(net.minecraft.commands.Commands.LEVEL_GAMEMASTERS))
                        .executes(c -> {
                            int n = AvatarLibrary.scan();
                            Net.Index msg = new Net.Index(AvatarLibrary.all());
                            for (ServerPlayer p : PlayerLookup.all(c.getSource().getServer())) {
                                ServerPlayNetworking.send(p, msg);
                            }
                            c.getSource().sendSuccess(() -> Component.literal("StarModels: " + n + " personajes"), true);
                            return n;
                        })));
    }
}
