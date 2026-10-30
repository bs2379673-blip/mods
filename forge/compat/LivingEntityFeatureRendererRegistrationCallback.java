package __PKG__;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.client.event.EntityRenderersEvent;

public interface LivingEntityFeatureRendererRegistrationCallback {
    void registerRenderers(EntityType<? extends LivingEntity> entityType, LivingEntityRenderer<?, ?> entityRenderer,
                           RegistrationHelper registrationHelper, EntityRendererProvider.Context context);

    interface RegistrationHelper {
        <T extends LivingEntity> void register(RenderLayer<T, ? extends EntityModel<T>> featureRenderer);
    }

    Event<LivingEntityFeatureRendererRegistrationCallback> EVENT = new Event<>(() -> Compat.modBus.addListener((EntityRenderersEvent.AddLayers e) -> {
        EntityRendererProvider.Context ctx = e.getContext();
        for (String skin : e.getSkins()) {
            LivingEntityRenderer<?, ?> r = e.getSkin(skin);
            if (r != null) {
                fire(EntityType.PLAYER, r, ctx);
            }
        }
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            if (type == EntityType.PLAYER) {
                continue;
            }
            EntityRenderer<?> r;
            try {
                r = e.getRenderer((EntityType) type);
            } catch (Throwable t) {
                continue;
            }
            if (r instanceof LivingEntityRenderer<?, ?> lr) {
                fire((EntityType) type, lr, ctx);
            }
        }
    }));

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void fire(EntityType<? extends LivingEntity> type, LivingEntityRenderer<?, ?> r, EntityRendererProvider.Context ctx) {
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
