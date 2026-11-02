package com.starlevi.starspirits.client;

import com.starlevi.starspirits.ModRegistry;
import com.starlevi.starspirits.SpiritEntity;
import com.starlevi.starspirits.block.ExtractionBlockEntity;
import com.starlevi.starspirits.net.Net;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Arranque del lado del cliente: dibujos, particulas y recepcion de los espiritus del servidor. */
public final class StarSpiritsClient {
    private StarSpiritsClient() {
    }

    public static void init(IEventBus modBus) {
        Net.client = new Net.ClientSide() {
            @Override
            public void defs(Net.Defs d) {
                ClientDefs.receive(d);
            }

            @Override
            public void chunk(Net.Chunk c) {
                ClientDefs.chunk(c);
            }
        };
        SpiritEntity.clientTick = SpiritEffects::tick;
        ExtractionBlockEntity.clientParticles = SpiritEffects::extraction;
        modBus.addListener((EntityRenderersEvent.RegisterRenderers e) -> {
            e.registerEntityRenderer(ModRegistry.SPIRIT.get(), SpiritRenderer::new);
            e.registerBlockEntityRenderer(ModRegistry.EXTRACTION_BE.get(), ExtractionRenderer::new);
            e.registerBlockEntityRenderer(ModRegistry.POT_BE.get(), PotRenderer::new);
        });
    }
}
