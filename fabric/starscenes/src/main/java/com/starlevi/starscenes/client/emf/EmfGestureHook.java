package com.starlevi.starscenes.client.emf;

import com.starlevi.starscenes.client.PlayerGestures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.player.Player;
import traben.entity_model_features.EMFAnimationApi;
import traben.entity_model_features.models.animation.state.EMFEntityRenderState;
import traben.entity_model_features.models.parts.EMFModelPartRoot;
import traben.entity_model_features.models.parts.EMFModelPartVanilla;

import java.util.Map;
import java.util.UUID;

/**
 * Con EMF (Entity Model Features) y un paquete de animaciones del jugador, el paquete pisa todo lo que
 * otros mods ponen en el modelo. Este gancho de la API de EMF pone los gestos de StarScenes (abrazo,
 * de la mano, cargar...) despues de que el paquete anima, como hace EMF Compat / Touch'n Motion.
 * Solo se carga si EMF esta instalado.
 */
public final class EmfGestureHook extends EMFAnimationApi.EMFAnimationHook {
    private EmfGestureHook() {
    }

    public static void register() {
        try {
            EMFAnimationApi.registerAnimationHook(new EmfGestureHook());
        } catch (Throwable t) {
            com.starlevi.starscenes.StarScenes.LOGGER.warn("StarScenes: no pude engancharme a EMF: {}", t.toString());
        }
    }

    private static int entityId(UUID uuid) {
        Minecraft mc = Minecraft.getInstance();
        if (uuid == null || mc.level == null) {
            return -1;
        }
        Player p = mc.level.getPlayerByUUID(uuid);
        if (p == null || p == mc.player && mc.options.getCameraType().isFirstPerson()) {
            return -1;
        }
        return p.getId();
    }

    @Override
    public void onAnimationEnd(AnimationContext context, boolean wasCancelledByHook) {
        try {
            EMFEntityRenderState state = context.activeState();
            if (state == null) {
                return;
            }
            int id = entityId(state.uuid());
            if (id < 0 || !PlayerGestures.busy(id)) {
                return;
            }
            EMFModelPartRoot root = context.animatingModelRoot();
            Map<String, EMFModelPartVanilla> parts = root.getAllVanillaPartsByNameEMF();
            PlayerGestures.apply(id, name -> (ModelPart) (Object) parts.get(name));
        } catch (Throwable ignored) {
            // un error aca no tiene que apagar las animaciones del paquete
        }
    }
}
