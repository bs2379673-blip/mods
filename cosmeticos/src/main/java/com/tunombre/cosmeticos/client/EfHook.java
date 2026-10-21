package com.tunombre.cosmeticos.client;

import net.minecraft.world.entity.EntityType;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import yesman.epicfight.api.client.forgeevent.PatchedRenderersEvent;
import yesman.epicfight.client.renderer.patched.entity.PatchedEntityRenderer;
import yesman.epicfight.client.renderer.patched.entity.PatchedLivingEntityRenderer;

/** engancha la capa de cosmeticos al renderer del jugador de Epic Fight (solo se carga si Epic Fight esta instalado) */
final class EfHook {
    private EfHook() { }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static void register() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(EventPriority.LOWEST, (PatchedRenderersEvent.Modify e) -> {
            PatchedEntityRenderer r = e.get(EntityType.PLAYER);
            if (r instanceof PatchedLivingEntityRenderer living) living.addCustomLayer(new CosPatchedLayer());
        });
    }
}
