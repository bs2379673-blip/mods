package com.tunombre.concierto;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(Concierto.MODID)
public class Concierto {
    public static final String MODID = "concierto";

    public Concierto() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        Net.init();
        com.tunombre.dancers.Dancers.init(bus);
        MinecraftForge.EVENT_BUS.register(Show.class);
        MinecraftForge.EVENT_BUS.register(Players.class);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.tunombre.concierto.client.ConciertoClient.init(bus));
    }
}
