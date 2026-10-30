package com.annis.npc.compat;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

public interface LivingEntityFeatureRendererRegistrationCallback {
    void registerRenderers(EntityType<? extends LivingEntity> entityType, LivingEntityRenderer<?, ?> entityRenderer,
                           RegistrationHelper registrationHelper, EntityRendererProvider.Context context);

    interface RegistrationHelper {
        <T extends LivingEntity> void register(RenderLayer<T, ? extends EntityModel<T>> featureRenderer);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    Event<LivingEntityFeatureRendererRegistrationCallback> EVENT = new Event<>(() -> Compat.modBus.addListener((EntityRenderersEvent.AddLayers e) -> {
        EntityRendererProvider.Context ctx = e.getContext();
        for (PlayerSkin.Model skin : e.getSkins()) {
            EntityRenderer<?> r = e.getSkin(skin);
            if (r instanceof LivingEntityRenderer<?, ?> lr) {
                Helper.fire(EntityType.PLAYER, lr, ctx);
            }
        }
        for (EntityType<?> type : e.getEntityTypes()) {
            EntityRenderer<?> r = e.getRenderer((EntityType) type);
            if (r instanceof LivingEntityRenderer<?, ?> lr) {
                Helper.fire((EntityType) type, lr, ctx);
            }
        }
    }));

    final class Helper {
        private Helper() {
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        static void fire(EntityType<? extends LivingEntity> type, LivingEntityRenderer<?, ?> r, EntityRendererProvider.Context ctx) {
            RegistrationHelper helper = new RegistrationHelper() {
                @Override
                public <T extends LivingEntity> void register(RenderLayer<T, ? extends EntityModel<T>> layer) {
                    ((LivingEntityRenderer) r).addLayer(layer);
                }
            };
            for (LivingEntityFeatureRendererRegistrationCallback c : EVENT.listeners()) {
                c.registerRenderers(type, r, helper, ctx);
            }
        }
    }
}
