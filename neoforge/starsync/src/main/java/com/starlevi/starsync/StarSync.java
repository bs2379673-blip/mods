package com.starlevi.starsync;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * StarSync: pantallas de WaterFrames vinculadas (mismo contenido y tiempo en todas).
 * Propiedad de Starlevi. Todos los derechos reservados.
 */
@Mod(StarSync.MOD_ID)
public final class StarSync {
    public static final String MOD_ID = "starsync";
    public static final Logger LOGGER = LoggerFactory.getLogger("StarSync");
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    public static final DeferredItem<LinkerItem> LINKER = ITEMS.register("vinculador", () -> new LinkerItem(new Item.Properties().stacksTo(1)));

    public StarSync(IEventBus bus) {
        ITEMS.register(bus);
        bus.addListener((BuildCreativeModeTabContentsEvent e) -> {
            if (e.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
                e.accept(LINKER.get());
            }
        });
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent e) -> Groups.load(e.getServer()));
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> Groups.unload());
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> {
            Groups g = Groups.get();
            if (g != null) {
                try {
                    g.tick(e.getServer());
                } catch (Throwable t) {
                    LOGGER.warn("StarSync: {}", t.toString());
                }
            }
        });
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> e.getDispatcher().register(
                literal("starsync").requires(s -> s.hasPermission(2))
                        .then(literal("grupos").executes(c -> {
                            Groups g = Groups.get();
                            StringBuilder sb = new StringBuilder();
                            for (Groups.Group gr : g.all().values()) {
                                sb.append(gr.id).append(" (").append(gr.members.size()).append(" pantallas, ")
                                        .append(gr.oneSound ? "suena la primera" : "suenan todas").append(")  ");
                            }
                            String out = sb.length() == 0 ? "No hay grupos. Usa el vinculador." : sb.toString();
                            c.getSource().sendSuccess(() -> Component.literal(out), false);
                            return g.all().size();
                        }))
                        .then(literal("vincular").then(argument("grupo", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(Groups.get().all().keySet(), b))
                                .then(argument("pos", net.minecraft.commands.arguments.coordinates.BlockPosArgument.blockPos()).executes(c -> {
                                    net.minecraft.core.BlockPos pos = net.minecraft.commands.arguments.coordinates.BlockPosArgument.getLoadedBlockPos(c, "pos");
                                    net.minecraft.server.level.ServerLevel level = c.getSource().getLevel();
                                    if (!(level.getBlockEntity(pos) instanceof me.srrapero720.waterframes.common.block.entity.DisplayTile)) {
                                        c.getSource().sendFailure(Component.literal("Ahi no hay una pantalla de WaterFrames"));
                                        return 0;
                                    }
                                    String id = StringArgumentType.getString(c, "grupo");
                                    Groups all = Groups.get();
                                    Groups.Group g = all.group(id);
                                    if (g == null) {
                                        g = all.create(id);
                                    }
                                    Groups.Group cur = all.groupOf(level.dimension(), pos);
                                    if (cur != g) {
                                        all.toggle(g, level.dimension(), pos);
                                    }
                                    Groups.Group gg = g;
                                    c.getSource().sendSuccess(() -> Component.literal("Pantalla en " + gg.id + " (" + gg.members.size() + " pantallas)"), true);
                                    return 1;
                                }))))
                        .then(literal("sonido").then(argument("grupo", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(Groups.get().all().keySet(), b))
                                .then(literal("una").executes(c -> sound(c.getSource(), StringArgumentType.getString(c, "grupo"), true)))
                                .then(literal("todas").executes(c -> sound(c.getSource(), StringArgumentType.getString(c, "grupo"), false)))))
                        .then(literal("borrar").then(argument("grupo", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(Groups.get().all().keySet(), b))
                                .executes(c -> {
                                    String id = StringArgumentType.getString(c, "grupo");
                                    Groups.get().remove(id);
                                    c.getSource().sendSuccess(() -> Component.literal("Grupo " + id + " borrado"), true);
                                    return 1;
                                })))));
    }

    private static int sound(net.minecraft.commands.CommandSourceStack s, String id, boolean one) {
        Groups.Group g = Groups.get().group(id);
        if (g == null) {
            s.sendFailure(Component.literal("No existe el grupo " + id));
            return 0;
        }
        g.oneSound = one;
        Groups.get().setDirty();
        s.sendSuccess(() -> Component.literal(id + ": " + (one ? "suena solo la primera pantalla" : "suenan todas las pantallas")), true);
        return 1;
    }
}
