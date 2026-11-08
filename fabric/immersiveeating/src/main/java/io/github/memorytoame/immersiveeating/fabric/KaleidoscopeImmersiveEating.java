package io.github.memorytoame.immersiveeating.fabric;

import net.fabricmc.api.ModInitializer;

import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class KaleidoscopeImmersiveEating implements ModInitializer {
	public static final String MOD_ID = "food";
	public static final String AUTHOR = "XianYue | NightEpiphany";


	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);


	@Override
	public void onInitialize() {
		Food.initialize();
		LOGGER.info("KaleidoscopeImmersiveEating initialized. Author: " + AUTHOR);
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
