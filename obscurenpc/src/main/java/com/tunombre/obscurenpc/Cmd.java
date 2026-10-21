package com.tunombre.obscurenpc;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** /onpc: controla NPCs desde comandos, command blocks, escenas (mod intro) y datapacks. */
public final class Cmd {
    private Cmd() { }

    private static List<Actor> npcs(CommandContext<CommandSourceStack> c, String name) throws CommandSyntaxException {
        List<Actor> l = new ArrayList<>();
        for (Entity e : EntityArgument.getEntities(c, name)) if (e instanceof Actor a) l.add(a);
        if (l.isEmpty()) throw new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(Component.literal("Ningún NPC de Obscure NPC coincide con el selector")).create();
        return l;
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, ?> targets() { return Commands.argument("npcs", EntityArgument.entities()); }

    private static int ok(CommandContext<CommandSourceStack> c, String msg, int n) {
        c.getSource().sendSuccess(() -> Component.literal(msg), false);
        return n;
    }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent e) {
        var d = e.getDispatcher();
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("onpc");
        root.then(Commands.literal("responder").then(Commands.argument("n", IntegerArgumentType.integer(1, 20)).executes(c -> {
            Dialogs.choose(c.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(c, "n"));
            return 1;
        })));
        root.then(Commands.literal("crear").requires(s -> s.hasPermission(2))
                .then(Commands.argument("nombre", StringArgumentType.word()).executes(c -> crear(c, c.getSource().getPosition(), "ef"))
                        .then(Commands.argument("pos", Vec3Argument.vec3()).executes(c -> crear(c, Vec3Argument.getVec3(c, "pos"), "ef"))
                                .then(Commands.argument("motor", StringArgumentType.word()).suggests((cc, b) -> SharedSuggestionProvider.suggest(List.of("ef", "normal"), b))
                                        .executes(c -> crear(c, Vec3Argument.getVec3(c, "pos"), StringArgumentType.getString(c, "motor")))))));
        root.then(Commands.literal("decir").requires(s -> s.hasPermission(2)).then(targets().then(Commands.argument("texto", StringArgumentType.greedyString()).executes(c -> {
            List<Actor> l = npcs(c, "npcs");
            ServerPlayer p = c.getSource().getEntity() instanceof ServerPlayer sp ? sp : null;
            for (Actor a : l) a.speak(StringArgumentType.getString(c, "texto"), p, true);
            return l.size();
        }))));
        root.then(Commands.literal("ir").requires(s -> s.hasPermission(2)).then(targets().then(Commands.argument("pos", Vec3Argument.vec3())
                .executes(c -> ir(c, 1.0)).then(Commands.argument("vel", DoubleArgumentType.doubleArg(0.1, 3.0)).executes(c -> ir(c, DoubleArgumentType.getDouble(c, "vel")))))));
        root.then(Commands.literal("mirar").requires(s -> s.hasPermission(2)).then(targets().then(Commands.argument("pos", Vec3Argument.vec3()).executes(c -> {
            Vec3 v = Vec3Argument.getVec3(c, "pos");
            List<Actor> l = npcs(c, "npcs");
            for (Actor a : l) a.routine().setLook(v);
            return l.size();
        }))));
        root.then(Commands.literal("mirar_fin").requires(s -> s.hasPermission(2)).then(targets().executes(c -> {
            List<Actor> l = npcs(c, "npcs");
            for (Actor a : l) a.routine().setLook(null);
            return l.size();
        })));
        root.then(Commands.literal("gesto").requires(s -> s.hasPermission(2)).then(targets().then(Commands.argument("id", StringArgumentType.word())
                .suggests((cc, b) -> SharedSuggestionProvider.suggest(concat(Gestures.ONESHOT, Gestures.LOOPS), b)).executes(c -> {
                    List<Actor> l = npcs(c, "npcs");
                    for (Actor a : l) a.gesture(StringArgumentType.getString(c, "id"));
                    return l.size();
                }))));
        root.then(Commands.literal("postura").requires(s -> s.hasPermission(2)).then(targets().then(Commands.argument("id", StringArgumentType.word())
                .suggests((cc, b) -> SharedSuggestionProvider.suggest(Stances.NAMES, b)).executes(c -> {
                    List<Actor> l = npcs(c, "npcs");
                    for (Actor a : l) a.setStance(Stances.byName(StringArgumentType.getString(c, "id")));
                    return l.size();
                }))));
        root.then(Commands.literal("dialogo").requires(s -> s.hasPermission(2)).then(targets().then(Commands.argument("id", StringArgumentType.word())
                .suggests((cc, b) -> SharedSuggestionProvider.suggest(Dialogs.fileIds(), b))
                .executes(c -> dialogo(c, c.getSource().getPlayerOrException()))
                .then(Commands.argument("jugador", EntityArgument.player()).executes(c -> dialogo(c, EntityArgument.getPlayer(c, "jugador")))))));
        root.then(Commands.literal("rutina").requires(s -> s.hasPermission(2)).then(targets().then(Commands.argument("id", StringArgumentType.word())
                .suggests((cc, b) -> SharedSuggestionProvider.suggest(Routine.fileIds(), b)).executes(c -> {
                    List<Actor> l = npcs(c, "npcs");
                    int n = 0;
                    for (Actor a : l) if (a.routine().start(StringArgumentType.getString(c, "id"))) n++;
                    if (n == 0) c.getSource().sendFailure(Component.literal("No existe esa rutina en config/obscurenpc/rutinas"));
                    return n;
                }))));
        root.then(Commands.literal("rutina_parar").requires(s -> s.hasPermission(2)).then(targets().executes(c -> {
            List<Actor> l = npcs(c, "npcs");
            for (Actor a : l) a.routine().stop();
            return l.size();
        })));
        root.then(Commands.literal("set").requires(s -> s.hasPermission(2)).then(targets().then(Commands.argument("campo", StringArgumentType.word())
                .suggests((cc, b) -> SharedSuggestionProvider.suggest(Arrays.stream(NpcData.class.getFields()).map(f -> f.getName()).toList(), b))
                .then(Commands.argument("valor", StringArgumentType.greedyString()).executes(c -> {
                    List<Actor> l = npcs(c, "npcs");
                    String k = StringArgumentType.getString(c, "campo"), v = StringArgumentType.getString(c, "valor");
                    int n = 0;
                    for (Actor a : l) {
                        if (Editor.setField(a.data(), k, v)) {
                            if (k.equals("postura")) a.setStance(a.data().postura);
                            a.applyData();
                            n++;
                        }
                    }
                    if (n == 0) c.getSource().sendFailure(Component.literal("Campo o valor no válido: " + k));
                    return n;
                })))));
        root.then(Commands.literal("equipar").requires(s -> s.hasPermission(2)).then(targets().then(Commands.argument("slot", StringArgumentType.word())
                .suggests((cc, b) -> SharedSuggestionProvider.suggest(List.of("mano", "mano2", "cabeza", "pecho", "piernas", "pies"), b))
                .then(Commands.argument("item", StringArgumentType.greedyString()).executes(c -> {
                    List<Actor> l = npcs(c, "npcs");
                    var item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new net.minecraft.resources.ResourceLocation(StringArgumentType.getString(c, "item").trim()));
                    if (item == null) { c.getSource().sendFailure(Component.literal("Ítem desconocido")); return 0; }
                    String snbt = Actor.stackToSnbt(new net.minecraft.world.item.ItemStack(item));
                    for (Actor a : l) {
                        switch (StringArgumentType.getString(c, "slot")) {
                            case "mano2" -> a.data().eq_mano2 = snbt;
                            case "cabeza" -> a.data().eq_cabeza = snbt;
                            case "pecho" -> a.data().eq_pecho = snbt;
                            case "piernas" -> a.data().eq_piernas = snbt;
                            case "pies" -> a.data().eq_pies = snbt;
                            default -> a.data().eq_mano = snbt;
                        }
                        a.applyData();
                    }
                    return l.size();
                })))));
        root.then(Commands.literal("tp").requires(s -> s.hasPermission(2)).then(targets().then(Commands.argument("pos", Vec3Argument.vec3()).executes(c -> {
            Vec3 v = Vec3Argument.getVec3(c, "pos");
            List<Actor> l = npcs(c, "npcs");
            for (Actor a : l) a.teleportTo(v.x, v.y, v.z);
            return l.size();
        }))));
        root.then(Commands.literal("borrar").requires(s -> s.hasPermission(2)).then(targets().executes(c -> {
            List<Actor> l = npcs(c, "npcs");
            for (Actor a : l) a.discard();
            return l.size();
        })));
        root.then(Commands.literal("bandera").requires(s -> s.hasPermission(2))
                .then(Commands.literal("poner").then(Commands.argument("f", StringArgumentType.word()).executes(c -> { State.set(StringArgumentType.getString(c, "f")); return 1; })))
                .then(Commands.literal("quitar").then(Commands.argument("f", StringArgumentType.word()).executes(c -> { State.clear(StringArgumentType.getString(c, "f")); return 1; })))
                .then(Commands.literal("lista").executes(c -> ok(c, "Banderas globales: " + State.all(), State.all().size()))));
        root.then(Commands.literal("historia").requires(s -> s.hasPermission(2))
                .executes(c -> ok(c, "NPCs de la historia: " + Historia.describe() + ". Usa /onpc historia instalar | todos | <id> | reinstalar", 1))
                .then(Commands.literal("instalar").executes(c -> ok(c, "Archivos nuevos instalados: " + Historia.install(false), 1)))
                .then(Commands.literal("reinstalar").executes(c -> ok(c, "Archivos reescritos (se pierden tus cambios en ellos): " + Historia.install(true), 1)))
                .then(Commands.argument("quien", StringArgumentType.word())
                        .suggests((cc, b) -> { List<String> l = new ArrayList<>(Historia.ids()); l.add("todos"); return SharedSuggestionProvider.suggest(l, b); })
                        .executes(c -> {
                            Historia.install(false);
                            int n = Historia.spawn(c.getSource().getLevel(), c.getSource().getPosition(), StringArgumentType.getString(c, "quien"));
                            return ok(c, n == 0 ? "No existe ese personaje (usa /onpc historia)" : n + " NPC de la historia colocados", n);
                        })));
        root.then(Commands.literal("recargar").requires(s -> s.hasPermission(2)).executes(c -> {
            Dialogs.reload();
            return ok(c, "Diálogos, conocimiento y rutinas recargados", 1);
        }));
        root.then(Commands.literal("lista").requires(s -> s.hasPermission(2)).executes(c -> ok(c, "Gestos: " + String.join(", ", concat(Gestures.ONESHOT, Gestures.LOOPS))
                + " · también cualquier animación de Epic Fight (comer, beber, cavar, sentarse...) o 'espacio:ruta'. Posturas: " + String.join(", ", Stances.NAMES), 1)));
        var node = d.register(root);
        d.register(Commands.literal("obscurenpc").redirect(node));
    }

    private static List<String> concat(String[] a, String[] b) {
        List<String> l = new ArrayList<>(Arrays.asList(a));
        l.addAll(Arrays.asList(b));
        return l;
    }

    private static int crear(CommandContext<CommandSourceStack> c, Vec3 pos, String motor) {
        NpcData data = new NpcData();
        data.nombre = StringArgumentType.getString(c, "nombre");
        data.id = data.nombre.toLowerCase(java.util.Locale.ROOT);
        ServerLevel lvl = c.getSource().getLevel();
        Actor a = ObscureNpc.create(lvl, motor, data);
        if (a == null) return 0;
        a.moveTo(pos.x, pos.y, pos.z, 0, 0);
        a.finalizeSpawn(lvl, lvl.getCurrentDifficultyAt(a.blockPosition()), net.minecraft.world.entity.MobSpawnType.COMMAND, null, null);
        lvl.addFreshEntity(a);
        return ok(c, "NPC '" + data.nombre + "' creado", 1);
    }

    private static int ir(CommandContext<CommandSourceStack> c, double vel) throws CommandSyntaxException {
        Vec3 v = Vec3Argument.getVec3(c, "pos");
        List<Actor> l = npcs(c, "npcs");
        for (Actor a : l) a.goTo(v.x, v.y, v.z, vel);
        return l.size();
    }

    private static int dialogo(CommandContext<CommandSourceStack> c, ServerPlayer p) throws CommandSyntaxException {
        List<Actor> l = npcs(c, "npcs");
        for (Actor a : l) Dialogs.start(a, p, StringArgumentType.getString(c, "id"));
        return l.size();
    }
}
