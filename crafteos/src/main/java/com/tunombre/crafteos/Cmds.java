package com.tunombre.crafteos;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /crafteos ... (operadores, nivel 2) + armado de las paginas del menu */
public final class Cmds {
    private Cmds() { }

    private static final SuggestionProvider<CommandSourceStack> KEYS = (c, b) -> SharedSuggestionProvider.suggest(Recipes.groups().keySet().stream().limit(5000).toList(), b);
    private static final SuggestionProvider<CommandSourceStack> OFFS = (c, b) -> SharedSuggestionProvider.suggest(new ArrayList<>(Recipes.disabledSet()), b);

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent e) { register(e.getDispatcher()); }

    private static void say(CommandSourceStack s, String m) { s.sendSuccess(() -> Component.literal(m), true); }

    private static int toggle(CommandSourceStack src, String key, boolean off) {
        if (!key.endsWith(":*") && !Recipes.groups().containsKey(key)) { src.sendFailure(Component.literal("No hay ninguna receta con la clave \"" + key + "\" (para un mod entero usa modid:*)")); return 0; }
        if (!Recipes.set(key, off)) { say(src, key + " ya estaba " + (off ? "desactivado" : "activado")); return 0; }
        int removed = Recipes.change(src.getServer());
        say(src, key + (off ? " DESACTIVADO" : " ACTIVADO") + " (recetas desactivadas en total: " + removed + ")");
        return 1;
    }

    private static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("crafteos").requires(s -> s.hasPermission(2))
                .then(Commands.literal("gui").executes(c -> { sendPage(c.getSource().getPlayerOrException(), new Net.Query("", 0, 0, 0, 12, true)); return 1; }))
                .then(Commands.literal("desactivar").then(Commands.argument("clave", StringArgumentType.greedyString()).suggests(KEYS)
                        .executes(c -> toggle(c.getSource(), StringArgumentType.getString(c, "clave").trim(), true))))
                .then(Commands.literal("activar").then(Commands.argument("clave", StringArgumentType.greedyString()).suggests(OFFS)
                        .executes(c -> toggle(c.getSource(), StringArgumentType.getString(c, "clave").trim(), false))))
                .then(Commands.literal("lista").executes(c -> {
                    if (Recipes.disabledSet().isEmpty()) { say(c.getSource(), "No hay crafteos desactivados."); return 0; }
                    say(c.getSource(), "Desactivados (" + Recipes.disabledSet().size() + "): " + String.join(", ", Recipes.disabledSet()));
                    return Recipes.disabledSet().size();
                }))
                .then(Commands.literal("recargar").executes(c -> {
                    Recipes.load();
                    int removed = Recipes.change(c.getSource().getServer());
                    say(c.getSource(), "Lista recargada desde config/crafteos/desactivados.json: " + removed + " recetas desactivadas.");
                    return 1;
                })));
    }

    static void sendPage(ServerPlayer p, Net.Query q) {
        Net.LAST.put(p.getUUID(), q);
        String f = q.search().toLowerCase(Locale.ROOT).trim();
        List<Net.Row> all = new ArrayList<>();
        if (q.tipo() == 5) {
            java.util.Map<String, int[]> mods = new java.util.TreeMap<>();
            for (Recipes.Group g : Recipes.groups().values()) { int[] a = mods.computeIfAbsent(g.mod, k -> new int[2]); a[0] += g.recipes.size(); a[1] |= g.kinds; }
            for (var en : mods.entrySet()) {
                String key = en.getKey() + ":*";
                boolean off = Recipes.isDisabled(key);
                if (!f.isEmpty() && !key.contains(f)) continue;
                if (q.estado() == 1 && !off || q.estado() == 2 && off) continue;
                all.add(new Net.Row(key, en.getKey(), en.getValue()[1], en.getValue()[0], off));
            }
        } else {
            for (Recipes.Group g : Recipes.groups().values()) {
                boolean off = Recipes.isDisabled(g.key) || Recipes.isDisabled(g.mod + ":*");
                if (!f.isEmpty() && !g.key.toLowerCase(Locale.ROOT).contains(f)) continue;
                if (q.estado() == 1 && !off || q.estado() == 2 && off) continue;
                int k = g.kinds;
                boolean ok = switch (q.tipo()) {
                    case 1 -> (k & Recipes.kindBit("craft")) != 0;
                    case 2 -> (k & Recipes.kindBit("smelt")) != 0;
                    case 3 -> (k & (Recipes.kindBit("smith") | Recipes.kindBit("stone"))) != 0;
                    case 4 -> (k & Recipes.kindBit("other")) != 0;
                    default -> true;
                };
                if (!ok) continue;
                all.add(new Net.Row(g.key, g.mod, g.kinds, g.recipes.size(), off));
            }
        }
        int per = Math.max(3, Math.min(40, q.perPage()));
        int pages = Math.max(1, (all.size() + per - 1) / per);
        int page = Math.max(0, Math.min(q.page(), pages - 1));
        List<Net.Row> rows = all.subList(page * per, Math.min(all.size(), (page + 1) * per));
        Net.send(p, new Net.Page(q.open(), all.size(), page, Recipes.disabledSet().size(), new ArrayList<>(rows)));
    }
}
