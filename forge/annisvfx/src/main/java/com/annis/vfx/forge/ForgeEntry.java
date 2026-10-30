package com.annis.vfx.forge;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

/** Arranque en Forge: hace lo mismo que los "entrypoints" de Fabric. */
@Mod("annisvfx")
public class ForgeEntry {
    public ForgeEntry() {
        var bus = FMLJavaModLoadingContext.get().getModEventBus();
        com.annis.vfx.compat.Compat.modBus = bus;

        new com.annis.vfx.AnnisVfx().onInitialize();
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientEntry.init();
        }
    }
}
