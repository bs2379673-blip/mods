package com.tunombre.obscureui;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(ObscureUI.MODID)
public class ObscureUI {
    public static final String MODID = "obscureui";
    public static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    public ObscureUI() {
        Net.init();
        var bus = FMLJavaModLoadingContext.get().getModEventBus();
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.tunombre.obscureui.client.ObscureClient.init(bus));
    }
}
