package com.tunombre.anomalias;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(Anomalias.MODID)
public class Anomalias {
    public static final String MODID = "anomalias";
    public static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    public Anomalias() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        Net.init();
        MinecraftForge.EVENT_BUS.register(Server.class);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.tunombre.anomalias.client.AnomaliaClient.init(bus));
    }
}
