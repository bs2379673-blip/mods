package com.starlevi.starriders;

import com.starlevi.starriders.net.Net;
import com.starlevi.starriders.server.Commands;
import com.starlevi.starriders.server.Mounts;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * StarRiders: monturas especiales. Las dan los admins (con un silbato ligado al jugador) y, con una
 * probabilidad minima, aparece una salvaje por el mundo que hay que domar. Monturas: el Fenix y el Guardian del Bosque.
 * Propiedad de Starlevi. Todos los derechos reservados.
 */
@Mod(StarRiders.MOD_ID)
public final class StarRiders {
    public static final String MOD_ID = "starriders";
    public static final Logger LOGGER = LoggerFactory.getLogger("StarRiders");

    public StarRiders(IEventBus modBus) {
        ModRegistry.register(modBus);
        modBus.addListener(Net::register);
        modBus.addListener((EntityAttributeCreationEvent e) -> {
            e.put(ModRegistry.PHOENIX.get(), PhoenixEntity.createAttributes().build());
            e.put(ModRegistry.GUARDIAN.get(), GuardianEntity.createAttributes().build());
            e.put(ModRegistry.SERPENT.get(), SerpentEntity.createAttributes().build());
        });
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> Commands.register(e.getDispatcher()));
        NeoForge.EVENT_BUS.addListener((ServerStartingEvent e) -> Config.load());
        Mounts.init();
        if (FMLEnvironment.dist.isClient()) {
            com.starlevi.starriders.client.StarRidersClient.init(modBus);
        }
    }
}
