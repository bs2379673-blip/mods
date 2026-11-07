package com.starlevi.starbody;

import com.starlevi.starbody.net.Net;
import com.starlevi.starbody.server.TraitStore;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * StarBody: rasgos marcados sobre la skin (pelo con fisicas suaves; despues ojos y cuerpo), para
 * jugadores y para los aldeanos. Propiedad de Starlevi. Todos los derechos reservados.
 */
public final class StarBody implements ModInitializer {
    public static final String MOD_ID = "starbody";
    public static final Logger LOGGER = LoggerFactory.getLogger("StarBody");

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.playC2S().register(Net.SetTraits.TYPE, Net.SetTraits.CODEC);
        PayloadTypeRegistry.playS2C().register(Net.SkinTraits.TYPE, Net.SkinTraits.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Net.SetTraits.TYPE, (p, ctx) -> TraitStore.set(ctx.player(), p.skin(), p.data()));
        ServerLifecycleEvents.SERVER_STARTING.register(TraitStore::start);
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> TraitStore.stop());
        ServerPlayConnectionEvents.JOIN.register((h, sender, s) -> TraitStore.join(h.getPlayer()));
    }
}
