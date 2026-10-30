package com.annis.interact.forge;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

/** Arranque en Forge: hace lo mismo que los "entrypoints" de Fabric. */
@Mod("annisinteract")
public class ForgeEntry {
    public ForgeEntry() {
        var bus = FMLJavaModLoadingContext.get().getModEventBus();
        if (com.annis.npc.compat.Compat.modBus == null) com.annis.npc.compat.Compat.modBus = bus;

        new com.annis.interact.AnnisInteract().onInitialize();
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientEntry.init();
        }
    }
}
