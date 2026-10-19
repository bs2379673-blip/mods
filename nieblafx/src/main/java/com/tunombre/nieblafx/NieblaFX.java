package com.tunombre.nieblafx;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(NieblaFX.MODID)
public class NieblaFX {
    public static final String MODID = "nieblafx";
    public static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    public NieblaFX() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        Net.init();
        Fog.load();
        MinecraftForge.EVENT_BUS.register(Server.class);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.tunombre.nieblafx.client.NieblaClient.init(bus));
    }
}
