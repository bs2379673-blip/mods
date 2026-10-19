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

    /** solo cliente: el servidor le avisa que tiene un modelo puesto y no puede usar Epic Fight */
    public static volatile java.util.Set<java.util.UUID> clientModels = java.util.Set.of();

    public Modelos() {
        Net.init();
        MinecraftForge.EVENT_BUS.register(Server.class);
        if (EPIC) {
            MinecraftForge.EVENT_BUS.register(EpicSupport.class);
            net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> MinecraftForge.EVENT_BUS.register(com.tunombre.modelos.client.EpicClient.class));
        }
    }
}
