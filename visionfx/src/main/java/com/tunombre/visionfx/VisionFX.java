package com.tunombre.visionfx;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(VisionFX.MODID)
public class VisionFX {
    public static final String MODID = "visionfx";

    public VisionFX() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        Net.init();
        MinecraftForge.EVENT_BUS.register(Server.class);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.tunombre.visionfx.client.FxClient.init(bus));
    }
}
