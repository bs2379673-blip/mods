package com.annis.compat;

import com.annis.compat.client.ClipLibrary;
import com.annis.compat.client.TestHook;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Annis Compat: animaciones de Punchy para lo que otros mods animan por su cuenta. */
@Mod(AnnisCompat.MOD_ID)
public final class AnnisCompat {
    public static final String MOD_ID = "annis_compat";
    public static final Logger LOGGER = LoggerFactory.getLogger("Annis Compat");

    public AnnisCompat(IEventBus bus) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            Client.init(bus);
        }
    }

    private static final class Client {
        static void init(IEventBus bus) {
            bus.addListener((RegisterClientReloadListenersEvent e) ->
                    e.registerReloadListener((ResourceManagerReloadListener) ClipLibrary::load));
            if (System.getProperty("annis.test") != null) {
                TestHook.init();
            }
        }
    }
}
