package com.tunombre.efzombies;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ZombieRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import yesman.epicfight.api.client.forgeevent.PatchedRenderersEvent;
import yesman.epicfight.api.client.model.Meshes;
import yesman.epicfight.client.renderer.patched.entity.PHumanoidRenderer;

/** Solo cliente: renderer vanilla (textura) y renderer de Epic Fight del trepador. */
public final class ClientSetup {
    public static final ResourceLocation TEXTURE = new ResourceLocation(EfZombies.MODID, "textures/entity/trepador.png");

    private ClientSetup() { }

    static void init(IEventBus bus) {
        bus.addListener(ClientSetup::onRenderers);
        bus.addListener(ClientSetup::onPatchedRenderers);
    }

    private static void onRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(EfZombies.TREPADOR.get(), TrepadorRenderer::new);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void onPatchedRenderers(PatchedRenderersEvent.Add event) {
        EntityRendererProvider.Context ctx = event.getContext();
        event.addPatchedEntityRenderer(EfZombies.TREPADOR.get(), type -> new PHumanoidRenderer(Meshes.BIPED_OLD_TEX, ctx, type).initLayerLast(ctx, type));
    }

    /** el de vanilla: Epic Fight le pide la textura y las capas. */
    public static class TrepadorRenderer extends ZombieRenderer {
        public TrepadorRenderer(EntityRendererProvider.Context ctx) {
            super(ctx);
        }

        @Override
        public ResourceLocation getTextureLocation(Zombie z) {
            return TEXTURE;
        }
    }
}
