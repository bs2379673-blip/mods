package com.starlevi.starmodels;

import com.starlevi.starmodels.net.Net;
import com.starlevi.starmodels.server.AvatarLibrary;
import com.starlevi.starmodels.server.Commands;
import com.starlevi.starmodels.server.Outfits;
import com.starlevi.starmodels.server.Uploads;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * StarModels: personajes exportados de Roblox Studio (cuerpo R15, ropa 3D, cara y accesorios) como
 * modelo del jugador, en su calidad original, siguiendo las animaciones del jugador (de Minecraft, EMF,
 * Fresh Animations, Punchy...). Workshop de accesorios: sacarlos, ponerse los de otros personajes y
 * ajustarlos. El servidor los lee de config/starmodels/personajes y los manda a los jugadores.
 * Propiedad de Starlevi. Todos los derechos reservados.
 */
public final class StarModels implements ModInitializer {
    public static final String MOD_ID = "starmodels";
    public static final Logger LOGGER = LoggerFactory.getLogger("StarModels");

    @Override
    public void onInitialize() {
        Outfits.init();
        Net.register();
        CommandRegistrationCallback.EVENT.register((d, ctx, env) -> Commands.register(d));
        ServerLifecycleEvents.SERVER_STARTING.register(s -> AvatarLibrary.scan());
        ServerTickEvents.END_SERVER_TICK.register(Uploads::tick);
        ServerPlayConnectionEvents.JOIN.register((h, sender, s) -> Outfits.join(h.getPlayer()));
        ServerPlayConnectionEvents.DISCONNECT.register((h, s) -> Uploads.left(h.getPlayer()));
    }
}
