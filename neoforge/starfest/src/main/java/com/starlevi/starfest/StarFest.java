package com.starlevi.starfest;

import com.starlevi.starfest.net.Net;
import com.starlevi.starfest.server.Commands;
import com.starlevi.starfest.server.ShowServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * StarFest: conciertos y boliches con musica del servidor y luces que siguen el ritmo.
 * Propiedad de Starlevi. Todos los derechos reservados.
 */
@Mod(StarFest.MOD_ID)
public final class StarFest {
    public static final String MOD_ID = "starfest";
    public static final Logger LOGGER = LoggerFactory.getLogger("StarFest");

    public StarFest(IEventBus modBus) {
        Registry.register(modBus);
        modBus.addListener(Net::register);
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> Commands.register(e.getDispatcher()));
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent e) -> ShowServer.start(e.getServer()));
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> ShowServer.stopAll());
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> ShowServer.tick(e.getServer()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer sp) {
                ShowServer.left(sp);
            }
        });
        if (FMLEnvironment.dist == Dist.CLIENT) {
            com.starlevi.starfest.client.StarFestClient.init(modBus);
        }
    }
}
