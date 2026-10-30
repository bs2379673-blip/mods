package com.annis.npc.forge;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;

/** Arranque en NeoForge: hace lo mismo que los "entrypoints" de Fabric. */
@Mod("annisnpc")
public class ForgeEntry {
    public ForgeEntry(IEventBus bus, ModContainer container) {
        com.annis.npc.compat.Compat.modBus = bus;
        com.annis.npc.ModEntities.init(bus);
        new com.annis.npc.AnnisNpc().onInitialize();
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientEntry.init();
        }
    }
}
