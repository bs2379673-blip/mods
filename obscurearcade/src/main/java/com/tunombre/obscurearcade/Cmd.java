package com.tunombre.obscurearcade;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** /arcade: estilos, recargar, cancelar duelos y empezar un duelo entre dos jugadores (util para eventos y command blocks). */
public final class Cmd {
    private Cmd() { }

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("arcade").requires(s -> s.hasPermission(2))
                .then(Commands.literal("estilos").executes(c -> {
                    c.getSource().sendSuccess(() -> Component.literal("Estilos: " + String.join(", ", Styles.ids())), false);
                    return 1;
                }))
                .then(Commands.literal("recargar").executes(c -> {
                    Styles.reload();
                    c.getSource().sendSuccess(() -> Component.literal("Estilos recargados"), true);
                    return 1;
                }))
                .then(Commands.literal("cancelar").executes(c -> {
                    Duels.cancelAll();
                    c.getSource().sendSuccess(() -> Component.literal("Duelos cancelados"), true);
                    return 1;
                }))
                .then(Commands.literal("duelo")
                        .then(Commands.argument("a", EntityArgument.player())
                                .then(Commands.argument("b", EntityArgument.player()).executes(c -> {
                                    ServerPlayer a = EntityArgument.getPlayer(c, "a"), b = EntityArgument.getPlayer(c, "b");
                                    String r = Duels.start(a, b);
                                    c.getSource().sendSuccess(() -> Component.literal(r), true);
                                    return 1;
                                })))));
    }
}
