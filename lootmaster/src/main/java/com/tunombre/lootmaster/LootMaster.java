package com.tunombre.lootmaster;

import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * Loot Master: panel de control del loot de Survivors Arsenal (sa_decor). Relleno inicial unico de contenedores sin etiqueta,
 * cambios masivos reversibles, reinicios, y ajustes globales de respawn / loot por jugador. Todo desde /lootmaster.
 */
@Mod(LootMaster.MODID)
public class LootMaster {
    public static final String MODID = "lootmaster";
    public static final Logger LOG = LogUtils.getLogger();
    private static int counter;

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    public static final RegistryObject<Item> WAND = ITEMS.register("rellenador", () -> new Wand.WandItem(new Item.Properties().stacksTo(1)));

    public LootMaster() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener((FMLCommonSetupEvent e) -> e.enqueueWork(Net::init));
        ITEMS.register(FMLJavaModLoadingContext.get().getModEventBus());
        FMLJavaModLoadingContext.get().getModEventBus().addListener((BuildCreativeModeTabContentsEvent e) -> {
            if (e.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) e.accept(WAND);
        });
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(Wand.class);
    }

    @SubscribeEvent
    public void onCommands(RegisterCommandsEvent e) { Cmd.register(e.getDispatcher()); }

    @SubscribeEvent
    public void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        var server = e.getServer();
        if (server == null) return;
        boolean ended = Jobs.tick(server);
        if (ended) {
            Jobs.Job j = Jobs.last();
            String m = (j.cancelled ? "Tarea detenida: " : "Tarea terminada: ") + Jobs.label(j) + " · " + Ops.summary(j);
            LOG.info("[LootMaster] {}", m);
            for (ServerPlayer p : Ops.watchers(server)) { Net.toClient(p, "msg", m); Net.toClient(p, "state", Ops.state(server, p).toString()); }
            for (ServerPlayer p : server.getPlayerList().getPlayers()) if (p.hasPermissions(2) && !Ops.isWatching(p)) p.sendSystemMessage(net.minecraft.network.chat.Component.literal("[LootMaster] " + m));
        } else if (Jobs.current() != null && ++counter % 10 == 0) {
            String js = Ops.jobJson().toString();
            for (ServerPlayer p : Ops.watchers(server)) Net.toClient(p, "job", js);
        }
    }

    @SubscribeEvent
    public void onStopped(ServerStoppedEvent e) { State.reset(); Ops.clearWatchers(); Wand.clear(); }
}
