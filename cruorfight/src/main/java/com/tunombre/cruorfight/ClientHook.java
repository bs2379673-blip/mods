package com.tunombre.cruorfight;

import net.minecraft.world.entity.EntityType;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.ForgeRegistries;
import yesman.epicfight.api.client.forgeevent.PatchedRenderersEvent;
import yesman.epicfight.client.renderer.patched.entity.PatchedEntityRenderer;
import yesman.epicfight.client.renderer.patched.entity.PatchedLivingEntityRenderer;

public final class ClientHook {
    private ClientHook() { }

    public static void init() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(EventPriority.LOWEST, (PatchedRenderersEvent.Modify e) -> {
            Class<?> woundLayer;
            try {
                woundLayer = Class.forName("com.raiiiden.cruor.client.body.WoundLayer");
            } catch (Throwable t) {
                CruorFight.LOGGER.warn("CruorFight: no encuentro WoundLayer de Cruor, no hago nada", t);
                return;
            }
            int n = 0;
            for (EntityType<?> type : ForgeRegistries.ENTITY_TYPES.getValues()) {
                PatchedEntityRenderer r = e.get(type);
                if (r instanceof PatchedLivingEntityRenderer<?, ?, ?, ?, ?> living) {
                    try {
                        ((PatchedLivingEntityRenderer) living).addPatchedLayerAlways(woundLayer, new WoundPatchedLayer(living));
                        n++;
                    } catch (Throwable t) {
                        CruorFight.LOGGER.warn("CruorFight: fallo con {}", type, t);
                    }
                }
            }
            CruorFight.LOGGER.info("CruorFight: capa de heridas puesta en {} renderers de Epic Fight", n);
        });
    }
}
