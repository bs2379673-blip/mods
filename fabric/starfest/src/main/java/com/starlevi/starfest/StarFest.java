package com.starlevi.starfest;

import com.starlevi.starfest.net.Net;
import com.starlevi.starfest.server.Commands;
import com.starlevi.starfest.server.ShowServer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * StarFest: conciertos y boliches con musica del servidor y luces que siguen el ritmo.
 * Propiedad de Starlevi. Todos los derechos reservados.
 */
public final class StarFest implements ModInitializer {
    public static final String MOD_ID = "starfest";
    public static final Logger LOGGER = LoggerFactory.getLogger("StarFest");

    @Override
    public void onInitialize() {
        Registry.register();
        Net.register();
        CommandRegistrationCallback.EVENT.register((d, ctx, env) -> Commands.register(d));
        ServerLifecycleEvents.SERVER_STARTED.register(ShowServer::start);
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> ShowServer.stopAll());
        ServerTickEvents.END_SERVER_TICK.register(ShowServer::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((h, s) -> ShowServer.left(h.getPlayer()));
        LOGGER.info("StarFest listo");
    }
}
