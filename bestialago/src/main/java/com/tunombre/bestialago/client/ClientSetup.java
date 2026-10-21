package com.tunombre.bestialago.client;

import com.tunombre.bestialago.BestiaLago;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;

/** Solo cliente: modelo, renderer, temblor de camara cuando te agarra. */
public final class ClientSetup {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(new ResourceLocation(BestiaLago.MODID, "bestia"), "main");

    private ClientSetup() { }

    public static void init(IEventBus bus) {
        bus.addListener((EntityRenderersEvent.RegisterLayerDefinitions e) -> e.registerLayerDefinition(LAYER, BestiaModel::createBodyLayer));
        bus.addListener((EntityRenderersEvent.RegisterRenderers e) -> e.registerEntityRenderer(BestiaLago.BESTIA.get(), BestiaRenderer::new));
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.client.event.ClientPlayerNetworkEvent.Clone e) -> { BestiaLago.clientHeld = false; });
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingIn e) -> { BestiaLago.clientHeld = false; });
        MinecraftForge.EVENT_BUS.addListener(HoldFx::onCamera);
    }
}
