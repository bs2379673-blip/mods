package com.starlevi.starspirits;

import com.starlevi.starspirits.net.Net;
import com.starlevi.starspirits.server.Caravan;
import com.starlevi.starspirits.server.Benefits;
import com.starlevi.starspirits.server.Commands;
import com.starlevi.starspirits.server.SpiritLibrary;
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
 * StarSpirits: espiritus de plantas (hongos, flores, arbustitos) que salen de puntos de extraccion,
 * te siguen corriendo y te dan beneficios. Los espiritus se definen en el servidor
 * (config/starspirits/espiritus) y los jugadores los reciben solos.
 * Propiedad de Starlevi. Todos los derechos reservados.
 */
@Mod(StarSpirits.MOD_ID)
public final class StarSpirits {
    public static final String MOD_ID = "starspirits";
    public static final Logger LOGGER = LoggerFactory.getLogger("StarSpirits");

    public StarSpirits(IEventBus modBus) {
        ModRegistry.register(modBus);
        modBus.addListener(Net::register);
        modBus.addListener((EntityAttributeCreationEvent e) -> e.put(ModRegistry.SPIRIT.get(), SpiritEntity.createAttributes().build()));
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> Commands.register(e.getDispatcher()));
        NeoForge.EVENT_BUS.addListener((ServerStartingEvent e) -> {
            Config.load();
            SpiritLibrary.scan(true);
        });
        Caravan.init();
        com.starlevi.starspirits.server.Wild.init();
        Benefits.init();
        if (FMLEnvironment.dist.isClient()) {
            com.starlevi.starspirits.client.StarSpiritsClient.init(modBus);
        }
    }
}
