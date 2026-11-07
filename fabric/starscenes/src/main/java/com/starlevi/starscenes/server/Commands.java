package com.starlevi.starscenes.server;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.starlevi.starscenes.StarScenes;
import com.starlevi.starscenes.entity.NpcEntity;
import com.starlevi.starscenes.net.Net;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;

import java.util.List;
import java.util.Locale;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * /starscenes (o /scenes) para poner y editar NPCs, y /smove para moverlos de a poquito.
 * Todo actua sobre el NPC mas cercano (hasta 16 bloques). Agachado + clic derecho abre el menu.
 */
public final class Commands {
    private static final double RANGE = 16;

    private Commands() {
    }

    private static final SuggestionProvider<CommandSourceStack> MODELS = (c, b) -> SharedSuggestionProvider.suggest(SceneLibrary.models(), b);
    private static final SuggestionProvider<CommandSourceStack> SKINS = (c, b) -> SharedSuggestionProvider.suggest(SceneLibrary.skins(), b);
    private static final SuggestionProvider<CommandSourceStack> DIRS = (c, b) -> SharedSuggestionProvider.suggest(
            List.of("adelante", "atras", "izquierda", "derecha", "arriba", "abajo"), b);

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        LiteralArgumentBuilder<CommandSourceStack> root = literal("starscenes").requires(net.minecraft.commands.Commands.hasPermission(
                net.minecraft.commands.Commands.LEVEL_GAMEMASTERS));
        root.then(literal("poner").then(argument("modelo", StringArgumentType.word()).suggests(MODELS)
                .executes(c -> spawn(c, StringArgumentType.getString(c, "modelo"), ""))
                .then(argument("skin", StringArgumentType.word()).suggests(SKINS)
                        .executes(c -> spawn(c, StringArgumentType.getString(c, "modelo"), StringArgumentType.getString(c, "skin"))))));
        root.then(literal("spawn").redirect(root.build().getChild("poner")));
        root.then(literal("quitar").executes(c -> edit(c, "quitar", "")));
        root.then(literal("remove").executes(c -> edit(c, "quitar", "")));
        root.then(literal("cercano").executes(Commands::nearest));
        root.then(literal("nearest").executes(Commands::nearest));
        root.then(literal("lista").executes(Commands::list));
        root.then(literal("list").executes(Commands::list));
        root.then(literal("recargar").executes(Commands::reload));
        root.then(literal("reload").executes(Commands::reload));
        root.then(literal("menu").executes(Commands::menu));
        root.then(literal("modelo").then(argument("modelo", StringArgumentType.word()).suggests(MODELS)
                .executes(c -> edit(c, "modelo", StringArgumentType.getString(c, "modelo")))));
        root.then(literal("skin").then(argument("skin", StringArgumentType.word()).suggests(SKINS)
                .executes(c -> edit(c, "skin", StringArgumentType.getString(c, "skin")))));
        root.then(literal("nombre").then(argument("texto", StringArgumentType.greedyString())
                .executes(c -> edit(c, "nombre", StringArgumentType.getString(c, "texto")))));
        root.then(literal("estilo").then(argument("numero", IntegerArgumentType.integer(0, 99))
                .executes(c -> edit(c, "estilo", String.valueOf(IntegerArgumentType.getInteger(c, "numero"))))));
        root.then(literal("escala").then(argument("tamano", FloatArgumentType.floatArg(0.1f, 10f))
                .executes(c -> edit(c, "escala", String.valueOf(FloatArgumentType.getFloat(c, "tamano"))))));
        root.then(literal("mirar").then(argument("si", BoolArgumentType.bool())
                .executes(c -> edit(c, "mirar", String.valueOf(BoolArgumentType.getBool(c, "si"))))));
        root.then(literal("radio").then(argument("bloques", FloatArgumentType.floatArg(0.5f, 64f))
                .executes(c -> edit(c, "radio", String.valueOf(FloatArgumentType.getFloat(c, "bloques"))))));
        root.then(literal("girar").then(argument("grados", FloatArgumentType.floatArg(-360, 360))
                .executes(c -> edit(c, "girar", String.valueOf(FloatArgumentType.getFloat(c, "grados"))))));
        root.then(literal("mirarme").executes(c -> edit(c, "mirarme", "")));
        root.then(literal("item").then(argument("lugar", StringArgumentType.word())
                .suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("mano", "otra_mano", "cabeza"), b))
                .executes(c -> edit(c, "item", StringArgumentType.getString(c, "lugar")))));
        root.then(move(literal("mover")));
        root.then(literal("dialogo").then(argument("id", StringArgumentType.word()).suggests(DIALOGUES)
                .executes(c -> edit(c, "dialogo", StringArgumentType.getString(c, "id")))));
        root.then(literal("hablar").then(argument("id", StringArgumentType.word()).suggests(DIALOGUES).executes(c -> {
            ServerPlayer p = c.getSource().getPlayerOrException();
            return com.starlevi.starscenes.talk.Dialogues.open(p, Edits.nearest(p, RANGE), StringArgumentType.getString(c, "id")) ? 1 : 0;
        })));
        root.then(literal("tienda").then(argument("id", StringArgumentType.word()).suggests(SHOPS).executes(c -> {
            ServerPlayer p = c.getSource().getPlayerOrException();
            com.starlevi.starscenes.talk.Shops.open(p, null, StringArgumentType.getString(c, "id"));
            return 1;
        })));
        root.then(literal("mision").then(argument("que", StringArgumentType.word()).suggests(QUEST_OPS)
                .then(argument("id", StringArgumentType.word()).suggests(QUESTS).executes(Commands::quest))));
        root.then(literal("animar").then(argument("animacion", StringArgumentType.word()).executes(Commands::play)));
        d.register(root);
        d.register(literal("scenes").requires(net.minecraft.commands.Commands.hasPermission(net.minecraft.commands.Commands.LEVEL_GAMEMASTERS))
                .redirect(d.getRoot().getChild("starscenes")));
        d.register(move(literal("smove").requires(net.minecraft.commands.Commands.hasPermission(net.minecraft.commands.Commands.LEVEL_GAMEMASTERS))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> move(LiteralArgumentBuilder<CommandSourceStack> b) {
        return b.then(argument("direccion", StringArgumentType.word()).suggests(DIRS)
                .executes(c -> edit(c, "mover", StringArgumentType.getString(c, "direccion")))
                .then(argument("bloques", FloatArgumentType.floatArg(0.01f, 16f))
                        .executes(c -> edit(c, "mover", StringArgumentType.getString(c, "direccion") + " " + FloatArgumentType.getFloat(c, "bloques")))));
    }

    private static int spawn(CommandContext<CommandSourceStack> c, String model, String skin) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        String id = SceneLibrary.cleanId(model);
        if (SceneLibrary.info(id) == null) {
            c.getSource().sendFailure(Component.literal("No existe el modelo " + model + ". Hay: " + String.join(", ", SceneLibrary.models())));
            return 0;
        }
        NpcEntity npc = StarScenes.NPC.create(p.level(), EntitySpawnReason.COMMAND);
        if (npc == null) {
            return 0;
        }
        // mirando al jugador
        float yaw = p.getYRot() + 180;
        npc.snapTo(p.getX(), p.getY(), p.getZ(), yaw, 0);
        npc.setModel(id);
        if (!skin.isEmpty()) {
            Edits.apply(p, npc, "skin", skin);
        }
        p.level().addFreshEntity(npc);
        c.getSource().sendSuccess(() -> Component.literal("NPC " + id + " puesto. Agachate y tocalo para editarlo."), true);
        return 1;
    }

    private static int edit(CommandContext<CommandSourceStack> c, String action, String value) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        NpcEntity npc = Edits.nearest(p, RANGE);
        if (npc == null) {
            c.getSource().sendFailure(Component.literal("No hay ningún NPC cerca (hasta " + (int) RANGE + " bloques)"));
            return 0;
        }
        String msg = Edits.apply(p, npc, action, value);
        c.getSource().sendSuccess(() -> Component.literal(msg), false);
        return 1;
    }

    private static int nearest(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        NpcEntity n = Edits.nearest(p, RANGE);
        if (n == null) {
            c.getSource().sendFailure(Component.literal("No hay ningún NPC cerca"));
            return 0;
        }
        String name = n.getCustomName() == null ? "(sin nombre)" : n.getCustomName().getString();
        c.getSource().sendSuccess(() -> Component.literal(String.format(Locale.ROOT, "%s — modelo %s, skin %s, estilo %d, a %.1f bloques (%.2f %.2f %.2f)",
                name, n.model(), n.skin().isEmpty() ? "del modelo" : n.skin(), n.style(), Math.sqrt(n.distanceToSqr(p)), n.getX(), n.getY(), n.getZ())), false);
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> c) {
        c.getSource().sendSuccess(() -> Component.literal("Modelos: " + String.join(", ", SceneLibrary.models())), false);
        c.getSource().sendSuccess(() -> Component.literal("Skins: " + String.join(", ", SceneLibrary.skins())), false);
        for (String m : SceneLibrary.models()) {
            SceneLibrary.Info i = SceneLibrary.info(m);
            c.getSource().sendSuccess(() -> Component.literal("  " + m + ": " + String.join(", ", i.anims())), false);
        }
        return 1;
    }

    private static final SuggestionProvider<CommandSourceStack> DIALOGUES = (c, b) -> SharedSuggestionProvider.suggest(
            com.starlevi.starscenes.talk.Dialogues.ids(), b);
    private static final SuggestionProvider<CommandSourceStack> SHOPS = (c, b) -> SharedSuggestionProvider.suggest(
            com.starlevi.starscenes.talk.Shops.ids(), b);
    private static final SuggestionProvider<CommandSourceStack> QUESTS = (c, b) -> SharedSuggestionProvider.suggest(
            com.starlevi.starscenes.talk.Quests.ids(), b);
    private static final SuggestionProvider<CommandSourceStack> QUEST_OPS = (c, b) -> SharedSuggestionProvider.suggest(
            java.util.List.of("empezar", "entregar", "abandonar", "reiniciar", "estado"), b);

    private static int quest(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        String op = StringArgumentType.getString(c, "que"), id = StringArgumentType.getString(c, "id");
        switch (op) {
            case "empezar" -> com.starlevi.starscenes.talk.Quests.start(p, id);
            case "entregar" -> com.starlevi.starscenes.talk.Quests.deliver(p, null, id);
            case "abandonar" -> com.starlevi.starscenes.talk.Quests.abandon(p, id);
            case "reiniciar" -> {
                com.starlevi.starscenes.talk.PlayerStore.Data d = com.starlevi.starscenes.talk.PlayerStore.of(p);
                d.quests.remove(id);
                d.done.remove(id);
                com.starlevi.starscenes.talk.PlayerStore.changed(p);
            }
            default -> {
            }
        }
        c.getSource().sendSuccess(() -> Component.literal("Misión " + id + ": " + com.starlevi.starscenes.talk.Quests.state(p, id)), false);
        return 1;
    }

    private static int reload(CommandContext<CommandSourceStack> c) {
        String msg = SceneLibrary.scan() + "; " + com.starlevi.starscenes.StarScenes.loadTalk();
        Net.Index idx = SceneLibrary.index();
        for (ServerPlayer p : c.getSource().getServer().getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(p, idx);
        }
        c.getSource().sendSuccess(() -> Component.literal("StarScenes: " + msg), true);
        return 1;
    }

    private static int menu(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        NpcEntity npc = Edits.nearest(p, RANGE);
        if (npc == null) {
            c.getSource().sendFailure(Component.literal("No hay ningún NPC cerca"));
            return 0;
        }
        ServerPlayNetworking.send(p, Net.OpenMenu.of(npc));
        return 1;
    }

    private static int play(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        NpcEntity npc = Edits.nearest(p, RANGE);
        if (npc == null) {
            return 0;
        }
        Net.Play msg = new Net.Play(npc.getId(), StringArgumentType.getString(c, "animacion"));
        for (ServerPlayer o : net.fabricmc.fabric.api.networking.v1.PlayerLookup.tracking(npc)) {
            ServerPlayNetworking.send(o, msg);
        }
        return 1;
    }
}
