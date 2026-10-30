package com.annis.interact.forge;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;

/** Arranque en NeoForge: hace lo mismo que los "entrypoints" de Fabric. */
@Mod("annisinteract")
public class ForgeEntry {
    public ForgeEntry(IEventBus bus, ModContainer container) {
        if (com.annis.npc.compat.Compat.modBus == null) com.annis.npc.compat.Compat.modBus = bus;

        new com.annis.interact.AnnisInteract().onInitialize();
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientEntry.init();
        }
    }
}
