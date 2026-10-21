package com.tunombre.obscurenpc.client;

import com.tunombre.obscurenpc.ObscureNpc;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import yesman.epicfight.api.client.forgeevent.PatchedRenderersEvent;

/** Solo cliente: renderers (vanilla y Epic Fight), nombre y burbuja. */
public final class ClientSetup {
    private ClientSetup() { }

    public static void init(IEventBus bus) {
        bus.addListener(ClientSetup::onRenderers);
        bus.addListener(ClientSetup::onPatchedRenderers);
        MinecraftForge.EVENT_BUS.register(BubbleRender.class);
    }

    private static void onRenderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(ObscureNpc.NPC_EF.get(), NpcRenderer::new);
        e.registerEntityRenderer(ObscureNpc.NPC_VANILLA.get(), NpcRenderer::new);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void onPatchedRenderers(PatchedRenderersEvent.Add event) {
        EntityRendererProvider.Context ctx = event.getContext();
        event.addPatchedEntityRenderer(ObscureNpc.NPC_EF.get(), type -> new EfNpcRenderer(ctx, type).initLayerLast(ctx, type));
    }
}
