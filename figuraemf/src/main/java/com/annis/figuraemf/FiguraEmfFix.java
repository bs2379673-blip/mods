package com.annis.figuraemf;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FiguraEmfFix implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("figuraemf");

    @Override
    public void onInitializeClient() {
        FabricLoader loader = FabricLoader.getInstance();
        LOGGER.info("Figura + EMF Fix cargado (Figura: {}, EMF: {})",
                loader.isModLoaded("figura"), loader.isModLoaded("entity_model_features"));
    }
}
