package com.annis.npc.forge;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

/** Arranque en Forge: hace lo mismo que los "entrypoints" de Fabric. */
@Mod("annisnpc")
public class ForgeEntry {
    public ForgeEntry() {
        var bus = FMLJavaModLoadingContext.get().getModEventBus();
        com.annis.npc.compat.Compat.modBus = bus;
        com.annis.npc.ModEntities.init(bus);
        new com.annis.npc.AnnisNpc().onInitialize();
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientEntry.init();
        }
    }
}
