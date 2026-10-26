package com.annis.npc;

import com.annis.npc.command.NpcCommands;
import com.annis.npc.net.NpcNetworking;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AnnisNpc implements ModInitializer {
    public static final String MOD_ID = "annisnpc";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        ModEntities.register();
        NpcNetworking.registerPayloads();
        NpcNetworking.registerServer();
        NpcCommands.register();
        LOGGER.info("Annis NPC cargado");
    }
}
