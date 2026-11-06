package com.starlevi.starscenes;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * StarScenes: NPCs animados desde modelos de Blockbench (.bbmodel), para Fabric 1.21.11.
 * Propiedad de Starlevi. Todos los derechos reservados.
 */
public final class StarScenes implements ModInitializer {
    public static final String MOD_ID = "starscenes";
    public static final Logger LOGGER = LoggerFactory.getLogger("StarScenes");

    @Override
    public void onInitialize() {
        LOGGER.info("StarScenes cargando");
    }
}
