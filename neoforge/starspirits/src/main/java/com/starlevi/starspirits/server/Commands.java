package com.starlevi.starspirits.server;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.starlevi.starspirits.Config;
import com.starlevi.starspirits.SpiritData;
import com.starlevi.starspirits.SpiritDef;
import com.starlevi.starspirits.block.ExtractionBlockEntity;
import com.starlevi.starspirits.block.PotBlockEntity;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.List;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * /starspirits lista | activo &lt;n&gt; (todos)
 * /starspirits dar &lt;jugadores&gt; &lt;espiritu&gt; | quitar &lt;jugador&gt; &lt;n|todos&gt; | recargar | puntos [radio] | espiritus (admins)
 */
public final class Commands {
    private Commands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(literal("starspirits")
                .then(literal("lista").executes(Commands::list))
                .then(literal("activo").then(argument("n", IntegerArgumentType.integer(1)).executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    SpiritData data = Caravan.data(p);
                    int n = IntegerArgumentType.getInteger(c, "n");
                    if (n > data.entries.size()) {
                        c.getSource().sendFailure(Component.literal("No tenés tantos espíritus"));
                        return 0;
                    }
                    Caravan.setActive(p, data.entries.get(n - 1).id());
                    return 1;
                })))
                .then(literal("espiritus").requires(s -> s.hasPermission(2)).executes(c -> {
                    for (SpiritDef def : SpiritLibrary.all()) {
                        c.getSource().sendSuccess(() -> Component.literal(" " + def.id + " — " + def.name + " ")
                                .append(Component.literal(def.rarity.label).withStyle(def.rarity.chat))
                                .append(Component.literal(def.enabled ? "" : " (deshabilitado)")), false);
                    }
                    return 1;
                }))
                .then(literal("dar").requires(s -> s.hasPermission(2))
                        .then(argument("jugadores", EntityArgument.players())
                                .then(argument("espiritu", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(SpiritLibrary.all().stream().map(x -> x.id), b))
                                        .executes(c -> {
                                            String t = StringArgumentType.getString(c, "espiritu");
                                            if (SpiritLibrary.get(t) == null) {
                                                c.getSource().sendFailure(Component.literal("No existe el espíritu " + t));
                                                return 0;
                                            }
                                            int n = 0;
                                            for (ServerPlayer p : EntityArgument.getPlayers(c, "jugadores")) {
                                                Caravan.give(p, t);
                                                n++;
                                            }
                                            int fn = n;
                                            c.getSource().sendSuccess(() -> Component.literal("Espíritu " + t + " dado a " + fn + " jugador(es)"), true);
                                            return n;
                                        }))))
                .then(literal("quitar").requires(s -> s.hasPermission(2))
                        .then(argument("jugador", EntityArgument.player())
                                .then(literal("todos").executes(c -> {
                                    ServerPlayer p = EntityArgument.getPlayer(c, "jugador");
                                    for (var id : Caravan.ids(p)) {
                                        Caravan.poof(id);
                                        Caravan.data(p).remove(id);
                                    }
                                    c.getSource().sendSuccess(() -> Component.literal("Espíritus quitados"), true);
                                    return 1;
                                }))
                                .then(argument("n", IntegerArgumentType.integer(1)).executes(c -> {
                                    ServerPlayer p = EntityArgument.getPlayer(c, "jugador");
                                    SpiritData data = Caravan.data(p);
                                    int n = IntegerArgumentType.getInteger(c, "n");
                                    if (n > data.entries.size()) {
                                        c.getSource().sendFailure(Component.literal("No tiene tantos espíritus"));
                                        return 0;
                                    }
                                    var id = data.entries.get(n - 1).id();
                                    Caravan.poof(id);
                                    data.remove(id);
                                    c.getSource().sendSuccess(() -> Component.literal("Espíritu quitado"), true);
                                    return 1;
                                }))))
                .then(literal("salvaje").requires(s -> s.hasPermission(2))
                        .then(argument("tipo", StringArgumentType.word()).executes(c -> {
                            String t = StringArgumentType.getString(c, "tipo");
                            if (SpiritLibrary.get(t) == null) {
                                c.getSource().sendFailure(Component.literal("No existe el espíritu " + t));
                                return 0;
                            }
                            com.starlevi.starspirits.server.Wild.spawn(c.getSource().getLevel(), c.getSource().getPosition(), t);
                            c.getSource().sendSuccess(() -> Component.literal("Apareció " + t + " salvaje"), true);
                            return 1;
                        })))
                .then(literal("pilar").requires(s -> s.hasPermission(2))
                        .then(argument("pos", net.minecraft.commands.arguments.coordinates.BlockPosArgument.blockPos()).executes(c -> {
                            net.minecraft.core.BlockPos bp = net.minecraft.commands.arguments.coordinates.BlockPosArgument.getLoadedBlockPos(c, "pos");
                            c.getSource().getLevel().setBlock(bp, com.starlevi.starspirits.ModRegistry.PILLAR.get().defaultBlockState(), 3);
                            c.getSource().sendSuccess(() -> Component.literal("Pilar Rúnico puesto"), true);
                            return 1;
                        })))
                .then(literal("vincular").requires(s -> s.hasPermission(2))
                        .then(argument("pos", net.minecraft.commands.arguments.coordinates.BlockPosArgument.blockPos())
                                .then(argument("jugador", EntityArgument.player())
                                        .then(argument("tipo", StringArgumentType.word()).executes(c -> {
                                            net.minecraft.core.BlockPos bp = net.minecraft.commands.arguments.coordinates.BlockPosArgument.getLoadedBlockPos(c, "pos");
                                            if (c.getSource().getLevel().getBlockEntity(bp) instanceof com.starlevi.starspirits.block.PillarBlockEntity be
                                                    && be.startRitual(EntityArgument.getPlayer(c, "jugador"), StringArgumentType.getString(c, "tipo"))) {
                                                c.getSource().sendSuccess(() -> Component.literal("Ritual empezado"), true);
                                                return 1;
                                            }
                                            c.getSource().sendFailure(Component.literal("Ahí no hay un Pilar Rúnico libre"));
                                            return 0;
                                        })))))
                .then(literal("recargar").requires(s -> s.hasPermission(2)).executes(c -> {
                    Config.load();
                    SpiritLibrary.scan(true);
                    for (ServerPlayer p : c.getSource().getServer().getPlayerList().getPlayers()) {
                        SpiritLibrary.send(p);
                    }
                    c.getSource().sendSuccess(() -> Component.literal("StarSpirits: configuración y espíritus recargados ("
                            + SpiritLibrary.all().size() + ")"), true);
                    return 1;
                }))
                .then(literal("extraer").requires(s -> s.hasPermission(2))
                        .then(argument("pos", BlockPosArgument.blockPos())
                                .then(argument("jugador", EntityArgument.player())
                                        .executes(c -> extract(c, null))
                                        .then(argument("espiritu", StringArgumentType.word())
                                                .suggests((c, b) -> SharedSuggestionProvider.suggest(SpiritLibrary.all().stream().map(x -> x.id), b))
                                                .executes(c -> extract(c, StringArgumentType.getString(c, "espiritu")))))))
                .then(literal("plantar").requires(s -> s.hasPermission(2))
                        .then(argument("pos", BlockPosArgument.blockPos())
                                .then(argument("jugador", EntityArgument.player())
                                        .then(argument("n", IntegerArgumentType.integer(1)).executes(c -> {
                                            ServerPlayer p = EntityArgument.getPlayer(c, "jugador");
                                            SpiritData data = Caravan.data(p);
                                            int n = IntegerArgumentType.getInteger(c, "n");
                                            BlockPos pos = BlockPosArgument.getLoadedBlockPos(c, "pos");
                                            if (n > data.entries.size() || !(c.getSource().getLevel().getBlockEntity(pos) instanceof PotBlockEntity pot) || !pot.empty()) {
                                                c.getSource().sendFailure(Component.literal("Hace falta una maceta vacía y un espíritu que exista"));
                                                return 0;
                                            }
                                            data.potPick = data.entries.get(n - 1).id();
                                            pot.use(p);
                                            return 1;
                                        })))))
                .then(literal("puntos").requires(s -> s.hasPermission(2))
                        .executes(c -> resetPoints(c, 48))
                        .then(argument("radio", IntegerArgumentType.integer(1, 256)).executes(c -> resetPoints(c, IntegerArgumentType.getInteger(c, "radio"))))));
    }

    /** Admins / eventos: extrae ya en ese punto para ese jugador (y opcionalmente fuerza el espiritu). */
    private static int extract(CommandContext<CommandSourceStack> c, String forced) throws CommandSyntaxException {
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(c, "pos");
        ServerPlayer p = EntityArgument.getPlayer(c, "jugador");
        if (!(c.getSource().getLevel().getBlockEntity(pos) instanceof ExtractionBlockEntity e)) {
            c.getSource().sendFailure(Component.literal("Ahí no hay un punto de extracción"));
            return 0;
        }
        if (forced != null && SpiritLibrary.get(forced) == null) {
            c.getSource().sendFailure(Component.literal("No existe el espíritu " + forced));
            return 0;
        }
        e.reset();
        e.forced = forced;
        e.use(p);
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        SpiritData data = Caravan.data(p);
        if (data.entries.isEmpty()) {
            c.getSource().sendSuccess(() -> Component.literal("Todavía no tenés espíritus. Buscá un punto de extracción ✿"), false);
            return 0;
        }
        SpiritData.Entry active = data.activeEntry();
        c.getSource().sendSuccess(() -> Component.literal("Tus espíritus (" + data.entries.size() + "):"), false);
        for (int i = 0; i < data.entries.size(); i++) {
            SpiritData.Entry e = data.entries.get(i);
            SpiritDef def = SpiritLibrary.get(e.type());
            MutableComponent line = Component.literal(" " + (i + 1) + ". ").append(Component.literal(def == null ? e.type() : def.name)
                    .withStyle(def == null ? net.minecraft.ChatFormatting.GRAY : def.rarity.chat));
            if (def != null) {
                line.append(Component.literal(" (" + def.rarity.label + ")"));
            }
            if (active != null && active.id().equals(e.id())) {
                line.append(Component.literal(" ✦ activo"));
            }
            c.getSource().sendSuccess(() -> line, false);
        }
        return data.entries.size();
    }

    private static int resetPoints(CommandContext<CommandSourceStack> c, int radius) {
        ServerLevel level = c.getSource().getLevel();
        BlockPos center = BlockPos.containing(c.getSource().getPosition());
        List<ExtractionBlockEntity> found = new ArrayList<>();
        int cr = (radius >> 4) + 1;
        ChunkPos cc = new ChunkPos(center);
        for (int x = -cr; x <= cr; x++) {
            for (int z = -cr; z <= cr; z++) {
                LevelChunk ch = level.getChunkSource().getChunkNow(cc.x + x, cc.z + z);
                if (ch == null) {
                    continue;
                }
                for (BlockEntity be : ch.getBlockEntities().values()) {
                    if (be instanceof ExtractionBlockEntity e && be.getBlockPos().distSqr(center) <= (double) radius * radius) {
                        found.add(e);
                    }
                }
            }
        }
        found.forEach(ExtractionBlockEntity::reset);
        c.getSource().sendSuccess(() -> Component.literal(found.size() + " puntos de extracción listos"), true);
        return found.size();
    }
}
