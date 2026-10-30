package com.annis.vfx.forge;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;

/** Arranque en NeoForge: hace lo mismo que los "entrypoints" de Fabric. */
@Mod("annisvfx")
public class ForgeEntry {
    public ForgeEntry(IEventBus bus, ModContainer container) {
        com.annis.vfx.compat.Compat.modBus = bus;

        new com.annis.vfx.AnnisVfx().onInitialize();
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientEntry.init();
        }
    }
}
