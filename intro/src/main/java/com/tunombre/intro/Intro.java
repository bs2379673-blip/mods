package com.tunombre.intro;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(Intro.MODID)
public class Intro {
    public static final String MODID = "intro";

    public Intro() {
        Net.init();
        MinecraftForge.EVENT_BUS.register(Director.class);
        var bus = FMLJavaModLoadingContext.get().getModEventBus();
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.tunombre.intro.client.IntroClient.init(bus));
    }
}
