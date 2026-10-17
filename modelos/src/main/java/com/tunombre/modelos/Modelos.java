package com.tunombre.modelos;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

@Mod(Modelos.MODID)
public class Modelos {
    public static final String MODID = "modelos";
    /** Epic Fight es opcional: sus clases solo se tocan desde EpicSupport y solo si esto es true */
    public static final boolean EPIC = ModList.get().isLoaded("epicfight");

    public Modelos() {
        Net.init();
        MinecraftForge.EVENT_BUS.register(Server.class);
    }
}
