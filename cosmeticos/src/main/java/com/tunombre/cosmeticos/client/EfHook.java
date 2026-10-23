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
        // el enganchado por la guadaña no hace la animación de "sentado" (monta al gancho): queda quieto, colgando o tirado
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(EventPriority.LOW, (yesman.epicfight.api.client.forgeevent.UpdatePlayerMotionEvent.BaseLayer e) -> {
            if (e.getPlayerPatch().getOriginal().getVehicle() instanceof com.tunombre.cosmeticos.Hook) e.setMotion(yesman.epicfight.api.animation.LivingMotions.IDLE);
        });
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(EventPriority.LOW, (yesman.epicfight.api.client.forgeevent.UpdatePlayerMotionEvent.CompositeLayer e) -> {
            if (e.getPlayerPatch().getOriginal().getVehicle() instanceof com.tunombre.cosmeticos.Hook) e.setMotion(yesman.epicfight.api.animation.LivingMotions.IDLE);
        });
        FMLJavaModLoadingContext.get().getModEventBus().addListener(EventPriority.LOWEST, (PatchedRenderersEvent.Modify e) -> {
            PatchedEntityRenderer r = e.get(EntityType.PLAYER);
            if (r instanceof PatchedLivingEntityRenderer living) {
                // sin esto Epic Fight dibuja tambien la capa vanilla con el modelo normal (copia pequeña en la cabeza)
                living.addPatchedLayerAlways(CosLayer.class, new yesman.epicfight.client.renderer.patched.layer.EmptyLayer());
                living.addCustomLayer(new CosPatchedLayer());
            }
        });
    }
}
