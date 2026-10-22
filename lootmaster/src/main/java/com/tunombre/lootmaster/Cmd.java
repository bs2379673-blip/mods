package com.tunombre.lootmaster;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.ogaba.sa_decor.common.loot.DecorLootConfig;
import com.ogaba.sa_decor.common.loot.DecorLootConfigManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** /lootmaster (abre el panel) y atajos para scripts / command blocks. */
public final class Cmd {
    private Cmd() { }

    private static List<String> ids() {
        List<String> l = new ArrayList<>();
        for (DecorLootConfig c : DecorLootConfigManager.values()) l.add(c.id());
        return l;
    }

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("lootmaster").requires(s -> s.hasPermission(2))
                .executes(c -> {
                    var p = c.getSource().getPlayerOrException();
                    Ops.open(p);
                    return 1;
                })
                .then(Commands.literal("estado").executes(c -> {
                    var s = c.getSource().getServer();
                    var j = Jobs.current();
                    c.getSource().sendSuccess(() -> Component.literal(j != null ? "En curso: " + Jobs.label(j) + " " + j.doneUnits + "/" + j.total + " · " + Ops.summary(j)
                            : "Sin tareas. Evento activo: " + (State.get(s).d.evProfile.isEmpty() ? "ninguno" : State.get(s).d.evProfile)), false);
                    return 1;
                }))
                .then(Commands.literal("parar").executes(c -> { Jobs.stop(); c.getSource().sendSuccess(() -> Component.literal("Deteniendo…"), false); return 1; }))
                .then(Commands.literal("revertir").executes(c -> run(c.getSource(), Jobs.Kind.REVERT, "")))
                .then(Commands.literal("reset").executes(c -> run(c.getSource(), Jobs.Kind.RESET, ""))
                        .then(Commands.argument("perfil", StringArgumentType.word()).suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(ids(), b))
                                .executes(c -> run(c.getSource(), Jobs.Kind.RESET, StringArgumentType.getString(c, "perfil")))))
                .then(Commands.literal("evento").then(Commands.argument("perfil", StringArgumentType.word()).suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(ids(), b))
                        .executes(c -> run(c.getSource(), Jobs.Kind.EVENT, StringArgumentType.getString(c, "perfil"))))));
    }

    private static int run(CommandSourceStack src, Jobs.Kind k, String arg) {
        if (k == Jobs.Kind.EVENT && !DecorLootConfigManager.has(arg)) {
            src.sendFailure(Component.literal("El perfil «" + arg + "» no existe."));
            return 0;
        }
        String err = Jobs.start(src.getServer(), k, arg, "todas");
        if (err != null) { src.sendFailure(Component.literal(err)); return 0; }
        src.sendSuccess(() -> Component.literal("Tarea iniciada: " + Jobs.label(Jobs.current()) + ". Mira /lootmaster estado."), true);
        return 1;
    }
}
