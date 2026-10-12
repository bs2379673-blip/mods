package com.tunombre.spawncontrol;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/** /spawns ... (operadores, nivel 2). El mob se escribe como minecraft:zombie, "modid:todos" (todos los de ese mod) o "todos:todos" (todos). */
final class Cmds {
    private Cmds() { }

    private static final Map<String, String> TYPES = Map.of(
            "natural", "NATURAL", "chunk", "CHUNK_GENERATION", "estructura", "STRUCTURE", "spawner", "SPAWNER",
            "patrulla", "PATROL", "evento", "EVENT", "refuerzo", "REINFORCEMENT", "jinete", "JOCKEY");

    private static final SuggestionProvider<CommandSourceStack> MOBS = (c, b) -> {
        List<String> l = new ArrayList<>();
        java.util.Set<String> ns = new java.util.TreeSet<>();
        for (var en : ForgeRegistries.ENTITY_TYPES.getEntries()) {
            if (en.getValue().getCategory() == MobCategory.MISC) continue;
            ResourceLocation id = en.getKey().location();
            l.add(id.toString());
            ns.add(id.getNamespace() + ":todos");
        }
        l.addAll(ns);
        l.add("todos:todos");
        return SharedSuggestionProvider.suggest(l, b);
    };

    private static LiteralArgumentBuilder<CommandSourceStack> lit(String n) { return Commands.literal(n); }

    private static RequiredArgumentBuilder<CommandSourceStack, ResourceLocation> mob() {
        return Commands.argument("mob", ResourceLocationArgument.id()).suggests(MOBS);
    }

    static String key(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        String k = Rules.key(ResourceLocationArgument.getId(c, "mob"));
        if (!Rules.validKey(k)) throw new net.minecraft.commands.CommandRuntimeException(Component.literal("No existe ese mob: " + k));
        return k;
    }

    private static void ok(CommandContext<CommandSourceStack> c, String msg) {
        c.getSource().sendSuccess(() -> Component.literal(msg), true);
    }

    private static int changed(CommandContext<CommandSourceStack> c, String msg) {
        Rules.save();
        ok(c, msg);
        return 1;
    }

    private static List<String> images() {
        List<String> out = new ArrayList<>();
        Path d = FMLPaths.CONFIGDIR.get().resolve("tacui").resolve("images");
        try (Stream<Path> s = Files.list(d)) {
            s.map(p -> p.getFileName().toString()).filter(n -> n.toLowerCase(Locale.ROOT).matches(".*\\.(png|gif)")).sorted().forEach(out::add);
        } catch (Exception ignored) { }
        return out;
    }

    static String summary(String key, Rule r) {
        StringBuilder sb = new StringBuilder(Rules.cmdId(key)).append(": ").append(r.enabled ? "ACTIVO" : "DESACTIVADO");
        if (r.releaseDay > 0 && !r.released) sb.append(" (se libera el dia ").append(r.releaseDay).append(")");
        if (r.onlyZones) sb.append(", solo en zonas");
        if (!r.zones.isEmpty()) sb.append(", ").append(r.zones.size()).append(" zona(s)");
        if (r.max > 0) sb.append(", max ").append(r.max);
        if (r.prob < 100) sb.append(", ").append(r.prob).append("%");
        if (r.hasAnnouncement()) sb.append(", con aviso");
        return sb.toString();
    }

    static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(lit("spawns").requires(s -> s.hasPermission(2))
                .then(lit("gui").executes(c -> {
                    Net.send(c.getSource().getPlayerOrException(), true);
                    return 1;
                }))
                .then(lit("lista").executes(c -> {
                    if (Rules.all().isEmpty()) { ok(c, "No hay reglas: todos los mobs spawnean como siempre."); return 0; }
                    for (Map.Entry<String, Rule> en : new TreeMap<>(Rules.all()).entrySet())
                        c.getSource().sendSuccess(() -> Component.literal(summary(en.getKey(), en.getValue())), false);
                    return Rules.all().size();
                }))
                .then(lit("mobs")
                        .executes(c -> {
                            TreeMap<String, Integer> ns = new TreeMap<>();
                            for (var en : ForgeRegistries.ENTITY_TYPES.getEntries())
                                if (en.getValue().getCategory() != MobCategory.MISC) ns.merge(en.getKey().location().getNamespace(), 1, Integer::sum);
                            c.getSource().sendSuccess(() -> Component.literal("Mods con mobs: " + ns + "  (usa /spawns mobs <modid>)"), false);
                            return ns.size();
                        })
                        .then(Commands.argument("modid", StringArgumentType.word()).executes(c -> {
                            String m = StringArgumentType.getString(c, "modid");
                            List<String> l = new ArrayList<>();
                            for (var en : ForgeRegistries.ENTITY_TYPES.getEntries()) {
                                ResourceLocation id = en.getKey().location();
                                if (id.getNamespace().equals(m) && en.getValue().getCategory() != MobCategory.MISC) {
                                    Rule r = Rules.all().get(id.toString());
                                    l.add(id.getPath() + " (" + en.getValue().getCategory().getName() + (r == null ? "" : r.enabled ? ", activo" : ", DESACTIVADO") + ")");
                                }
                            }
                            java.util.Collections.sort(l);
                            c.getSource().sendSuccess(() -> Component.literal(l.isEmpty() ? "Ese mod no tiene mobs" : m + ": " + String.join(", ", l)), false);
                            return l.size();
                        })))
                .then(lit("info").then(mob().executes(c -> {
                    String k = key(c);
                    Rule r = Rules.all().get(k);
                    if (r == null) { ok(c, Rules.cmdId(k) + ": sin regla (spawnea como siempre)"); return 0; }
                    c.getSource().sendSuccess(() -> Component.literal(summary(k, r)), false);
                    for (Zone z : r.zones) c.getSource().sendSuccess(() -> Component.literal("  zona " + z.describe()), false);
                    c.getSource().sendSuccess(() -> Component.literal("  tipos de spawn controlados: " + r.types), false);
                    if (r.hasAnnouncement()) c.getSource().sendSuccess(() -> Component.literal("  aviso: imagen=" + (r.aImage.isEmpty() ? "-" : r.aImage) + " " + r.aSecs + "s " + r.aMode
                            + " sonido=" + (r.aSound.isEmpty() ? "-" : r.aSound) + " texto=" + (r.aText.isEmpty() ? "-" : r.aText)), false);
                    return 1;
                })))
                .then(lit("activar").then(mob()
                        .executes(c -> activate(c, true))
                        .then(lit("silencio").executes(c -> activate(c, false)))))
                .then(lit("desactivar").then(mob().executes(c -> {
                    String k = key(c);
                    Rule r = Rules.getOrCreate(k);
                    r.enabled = false;
                    r.releaseDay = -1;
                    return changed(c, Rules.cmdId(k) + " DESACTIVADO: no spawneara de forma natural.");
                })))
                .then(lit("reset").then(mob().executes(c -> {
                    String k = key(c);
                    Rules.all().remove(k);
                    return changed(c, Rules.cmdId(k) + ": regla borrada, vuelve a spawnear como siempre.");
                })))
                .then(lit("dia").then(mob()
                        .then(lit("no").executes(c -> {
                            String k = key(c);
                            Rule r = Rules.getOrCreate(k);
                            r.releaseDay = -1;
                            return changed(c, "Liberacion programada cancelada para " + Rules.cmdId(k));
                        }))
                        .then(Commands.argument("dia", IntegerArgumentType.integer(1, 100000)).executes(c -> {
                            String k = key(c);
                            Rule r = Rules.getOrCreate(k);
                            r.enabled = false;
                            r.released = false;
                            r.releaseDay = IntegerArgumentType.getInteger(c, "dia");
                            return changed(c, Rules.cmdId(k) + " desactivado hasta el dia " + r.releaseDay + ": entonces se activa solo y lanza el aviso.");
                        }))))
                .then(lit("max").then(mob().then(Commands.argument("n", IntegerArgumentType.integer(0, 100000)).executes(c -> {
                    String k = key(c);
                    Rules.getOrCreate(k).max = IntegerArgumentType.getInteger(c, "n");
                    return changed(c, Rules.cmdId(k) + ": maximo vivos = " + IntegerArgumentType.getInteger(c, "n") + " (0 = sin limite)");
                }))))
                .then(lit("prob").then(mob().then(Commands.argument("porcentaje", IntegerArgumentType.integer(0, 100)).executes(c -> {
                    String k = key(c);
                    Rules.getOrCreate(k).prob = IntegerArgumentType.getInteger(c, "porcentaje");
                    return changed(c, Rules.cmdId(k) + ": deja pasar el " + IntegerArgumentType.getInteger(c, "porcentaje") + "% de sus spawns");
                }))))
                .then(lit("tipos").then(mob().then(Commands.argument("tipo", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(TYPES.keySet(), b))
                        .then(Commands.argument("controlar", BoolArgumentType.bool()).executes(c -> {
                            String k = key(c);
                            String t = TYPES.get(StringArgumentType.getString(c, "tipo").toLowerCase(Locale.ROOT));
                            if (t == null) { c.getSource().sendFailure(Component.literal("Tipos: " + TYPES.keySet())); return 0; }
                            Rule r = Rules.getOrCreate(k);
                            if (BoolArgumentType.getBool(c, "controlar")) r.types.add(t); else r.types.remove(t);
                            if (r.types.isEmpty()) r.types.add("NATURAL");
                            return changed(c, Rules.cmdId(k) + " controla spawns de tipo: " + r.types);
                        })))))
                .then(lit("zona")
                        .then(lit("aqui").then(mob().then(Commands.argument("nombre", StringArgumentType.word())
                                .then(Commands.argument("radio", DoubleArgumentType.doubleArg(1, 5000))
                                        .executes(c -> addZone(c, c.getSource().getPosition(), true))
                                        .then(lit("permitir").executes(c -> addZone(c, c.getSource().getPosition(), true)))
                                        .then(lit("prohibir").executes(c -> addZone(c, c.getSource().getPosition(), false)))))))
                        .then(lit("punto").then(mob().then(Commands.argument("nombre", StringArgumentType.word())
                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                        .then(Commands.argument("radio", DoubleArgumentType.doubleArg(1, 5000))
                                                .executes(c -> addZone(c, Vec3.atCenterOf(BlockPosArgument.getBlockPos(c, "pos")), true))
                                                .then(lit("permitir").executes(c -> addZone(c, Vec3.atCenterOf(BlockPosArgument.getBlockPos(c, "pos")), true)))
                                                .then(lit("prohibir").executes(c -> addZone(c, Vec3.atCenterOf(BlockPosArgument.getBlockPos(c, "pos")), false))))))))
                        .then(lit("borrar").then(mob().then(Commands.argument("nombre", StringArgumentType.word())
                                .suggests((c, b) -> {
                                    Rule r = Rules.all().get(Rules.key(ResourceLocationArgument.getId(c, "mob")));
                                    return SharedSuggestionProvider.suggest(r == null ? List.<String>of() : r.zones.stream().map(z -> z.name).toList(), b);
                                })
                                .executes(c -> {
                                    String k = key(c), n = StringArgumentType.getString(c, "nombre");
                                    Rule r = Rules.all().get(k);
                                    if (r == null || !r.zones.removeIf(z -> z.name.equals(n))) { c.getSource().sendFailure(Component.literal("No hay una zona " + n + " en " + k)); return 0; }
                                    return changed(c, "Zona " + n + " borrada de " + Rules.cmdId(k));
                                }))))
                        .then(lit("solo").then(mob().then(Commands.argument("solo", BoolArgumentType.bool()).executes(c -> {
                            String k = key(c);
                            boolean v = BoolArgumentType.getBool(c, "solo");
                            Rules.getOrCreate(k).onlyZones = v;
                            return changed(c, Rules.cmdId(k) + (v ? ": SOLO spawneara dentro de sus zonas permitidas." : ": spawneara en cualquier sitio (menos en zonas prohibidas)."));
                        }))))
                        .then(lit("ver").then(mob().executes(c -> {
                            String k = key(c);
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            SpawnControl.VIEWERS.put(p.getUUID(), new Object[]{k, 400});
                            ok(c, "Mostrando los bordes de las zonas de " + Rules.cmdId(k) + " durante 20 s (verde = permitida, rojo = prohibida).");
                            return 1;
                        }))))
                .then(lit("aviso").then(mob()
                        .then(lit("imagen").then(Commands.argument("archivo", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(images(), b))
                                .executes(c -> setImage(c, 5F, "center"))
                                .then(Commands.argument("segundos", FloatArgumentType.floatArg(0.3F, 120F))
                                        .executes(c -> setImage(c, FloatArgumentType.getFloat(c, "segundos"), "center"))
                                        .then(Commands.argument("modo", StringArgumentType.word())
                                                .suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("center", "full", "corner"), b))
                                                .executes(c -> setImage(c, FloatArgumentType.getFloat(c, "segundos"), StringArgumentType.getString(c, "modo")))))))
                        .then(lit("sonido").then(Commands.argument("id", ResourceLocationArgument.id()).executes(c -> {
                            String k = key(c);
                            Rules.getOrCreate(k).aSound = ResourceLocationArgument.getId(c, "id").toString();
                            return changed(c, "Sonido del aviso de " + Rules.cmdId(k) + " = " + Rules.getOrCreate(k).aSound);
                        })))
                        .then(lit("texto").then(Commands.argument("texto", StringArgumentType.greedyString()).executes(c -> {
                            String k = key(c);
                            Rules.getOrCreate(k).aText = StringArgumentType.getString(c, "texto");
                            return changed(c, "Texto del aviso de " + Rules.cmdId(k) + " = " + Rules.getOrCreate(k).aText);
                        })))
                        .then(lit("borrar").executes(c -> {
                            String k = key(c);
                            Rule r = Rules.getOrCreate(k);
                            r.aImage = ""; r.aSound = ""; r.aText = ""; r.aSecs = 5F; r.aMode = "center";
                            return changed(c, "Aviso de " + Rules.cmdId(k) + " borrado");
                        }))
                        .then(lit("probar").executes(c -> {
                            String k = key(c);
                            Rule r = Rules.all().get(k);
                            if (r == null || !r.hasAnnouncement()) { c.getSource().sendFailure(Component.literal("Ese mob no tiene aviso")); return 0; }
                            String how = Announce.fire(c.getSource().getServer(), k, r);
                            ok(c, "Aviso lanzado a todos: " + (how.isEmpty() ? "nada que mostrar" : how));
                            return 1;
                        }))))
                .then(lit("recargar").executes(c -> {
                    Rules.load();
                    ok(c, "Reglas recargadas desde config/spawncontrol/rules.json (" + Rules.all().size() + ")");
                    return Rules.all().size();
                })));
    }

    private static int activate(CommandContext<CommandSourceStack> c, boolean announce) throws CommandSyntaxException {
        String k = key(c);
        Rule r = Rules.getOrCreate(k);
        r.enabled = true;
        r.released = true;
        r.releaseDay = -1;
        Rules.save();
        String how = announce && r.hasAnnouncement() ? Announce.fire(c.getSource().getServer(), k, r) : "";
        ok(c, Rules.cmdId(k) + " ACTIVADO" + (how.isEmpty() ? "" : " + aviso (" + how + ")"));
        return 1;
    }

    private static int setImage(CommandContext<CommandSourceStack> c, float secs, String mode) throws CommandSyntaxException {
        String k = key(c);
        Rule r = Rules.getOrCreate(k);
        r.aImage = StringArgumentType.getString(c, "archivo");
        r.aSecs = secs;
        r.aMode = mode;
        String warn = Announce.tacuiPresent(c.getSource().getServer()) ? "" : " (ojo: tacui no esta instalado, se mostrara un titulo en su lugar)";
        return changed(c, "Aviso de " + Rules.cmdId(k) + ": imagen " + r.aImage + ", " + secs + " s, " + mode + warn);
    }

    private static int addZone(CommandContext<CommandSourceStack> c, Vec3 pos, boolean allow) throws CommandSyntaxException {
        String k = key(c);
        Rule r = Rules.getOrCreate(k);
        Zone z = new Zone();
        z.name = StringArgumentType.getString(c, "nombre");
        z.dim = c.getSource().getLevel().dimension().location().toString();
        z.x = pos.x; z.y = pos.y; z.z = pos.z;
        z.r = DoubleArgumentType.getDouble(c, "radio");
        z.allow = allow;
        r.zones.removeIf(o -> o.name.equals(z.name));
        r.zones.add(z);
        return changed(c, "Zona " + z.describe() + " anadida a " + Rules.cmdId(k) + (allow && !r.onlyZones ? "  (usa /spawns zona solo " + Rules.cmdId(k) + " true para que SOLO spawnee ahi)" : ""));
    }
}
