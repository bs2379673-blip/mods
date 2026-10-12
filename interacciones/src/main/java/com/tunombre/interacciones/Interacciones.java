package com.tunombre.interacciones;

import com.mojang.logging.LogUtils;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;
import yesman.epicfight.api.animation.AnimationManager;

@Mod(Interacciones.MODID)
public class Interacciones {
    public static final String MODID = "interacciones";
    public static final Logger LOG = LogUtils.getLogger();
    /** si esta instalado, se puede probar saludos/ejecucion con NPCs (ver EasyNpcSupport, nunca tocamos sus clases si esto es false) */
    public static final boolean EASY_NPC_LOADED = ModList.get().isLoaded("easy_npc");
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, MODID);
    public static final RegistryObject<MenuType<TradeMenu>> TRADE = MENUS.register("trade", () -> IForgeMenuType.create(TradeMenu::new));

    public Interacciones() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        MENUS.register(bus);
        bus.addListener((AnimationManager.AnimationRegistryEvent e) -> e.newBuilder(MODID, Anims::build));
        Net.init();
        MinecraftForge.EVENT_BUS.register(Sessions.class);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.tunombre.interacciones.client.ClientSetup.init(bus));
    }
}
