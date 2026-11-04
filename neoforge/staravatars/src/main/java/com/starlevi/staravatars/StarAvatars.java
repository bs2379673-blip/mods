package com.starlevi.staravatars;

import com.starlevi.staravatars.net.Net;
import com.starlevi.staravatars.server.AvatarLibrary;
import com.starlevi.staravatars.server.Commands;
import com.starlevi.staravatars.server.Outfits;
import com.starlevi.staravatars.server.Uploads;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * StarAvatars: personajes exportados de Roblox Studio (cuerpo R15, ropa, cara y accesorios) como
 * modelo del jugador, en su calidad original. El servidor los lee de config/staravatars/personajes y
 * los manda a los jugadores; cada uno elige el suyo.
 * Propiedad de Starlevi. Todos los derechos reservados.
 */
@Mod(StarAvatars.MOD_ID)
public final class StarAvatars {
    public static final String MOD_ID = "staravatars";
    public static final Logger LOGGER = LoggerFactory.getLogger("StarAvatars");

    public StarAvatars(IEventBus modBus) {
        modBus.addListener(Net::register);
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> Commands.register(e.getDispatcher()));
        NeoForge.EVENT_BUS.addListener((ServerStartingEvent e) -> AvatarLibrary.scan());
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> Uploads.tick(e.getServer()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer sp) {
                Outfits.join(sp);
            }
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer sp) {
                Uploads.left(sp);
            }
        });
        if (FMLEnvironment.dist.isClient()) {
            com.starlevi.staravatars.client.StarAvatarsClient.init(modBus);
        }
    }
}
