package com.tunombre.crafteos;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;

@Mod(Crafteos.MODID)
public class Crafteos {
    public static final String MODID = "crafteos";

    public Crafteos() {
        Net.init();
        MinecraftForge.EVENT_BUS.register(Recipes.class);
        MinecraftForge.EVENT_BUS.register(Cmds.class);
    }
}
