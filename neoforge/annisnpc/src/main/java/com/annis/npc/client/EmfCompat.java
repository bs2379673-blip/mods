package com.annis.npc.client;

import com.annis.npc.AnnisNpc;
import com.annis.npc.entity.AnnisNpcEntity;
import com.annis.npc.compat.FabricLoader;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.UUID;

/**
 * Compatibilidad con Entity Model Features (EMF) y packs tipo Fresh Moves, sin depender de EMF al compilar.
 *
 * - Si EMF anima el modelo del NPC (caminar, reposo...), esas animaciones se respetan y la capa
 *   procedural propia se apaga para no duplicar movimiento.
 * - Mientras el NPC hace un emote o una actividad, se pausan las animaciones de EMF para ese NPC
 *   (API publica de pausa de EMF); al terminar se reanudan solas.
 */
public final class EmfCompat {
    private static final boolean LOADED = FabricLoader.getInstance().isModLoaded("entity_model_features");
    private static boolean failed;
    private static Set<UUID> paused;
    private static Method isEmfModel;
    private static Method getRoot;
    private static Method hasAnimation;
    private static boolean modelLookupDone;

    private EmfCompat() {
    }

    public static boolean loaded() {
        return LOADED && !failed;
    }

    @SuppressWarnings("unchecked")
    private static Set<UUID> pausedSet() {
        if (paused != null || failed) {
            return paused;
        }
        try {
            Class<?> handler = Class.forName("traben.entity_model_features.utils.EMFAnimationPauseHandler");
            Field f = handler.getField("entitiesPaused");
            paused = (Set<UUID>) f.get(null);
        } catch (Throwable t) {
            failed = true;
            AnnisNpc.LOGGER.warn("EMF presente pero sin API de pausa compatible: {}", t.toString());
        }
        return paused;
    }

    /** true si el modelo del NPC esta siendo animado por EMF ahora mismo. */
    public static boolean emfAnimates(Object model) {
        if (!loaded()) {
            return false;
        }
        try {
            if (!modelLookupDone) {
                modelLookupDone = true;
                try {
                    Class<?> c = model.getClass();
                    isEmfModel = c.getMethod("emf$isEMFModel");
                    getRoot = c.getMethod("emf$getEMFRootModel");
                } catch (NoSuchMethodException e) {
                    isEmfModel = null; // esta version de EMF no expone la interfaz: solo se usa la pausa
                }
            }
            if (isEmfModel == null || !(Boolean) isEmfModel.invoke(model)) {
                return false;
            }
            Object root = getRoot.invoke(model);
            if (root == null) {
                return false;
            }
            if (hasAnimation == null) {
                hasAnimation = root.getClass().getMethod("hasAnimation");
            }
            return (Boolean) hasAnimation.invoke(root);
        } catch (Throwable t) {
            failed = true;
            AnnisNpc.LOGGER.warn("No se pudo consultar el modelo de EMF: {}", t.toString());
            return false;
        }
    }

    /** Pausa o reanuda las animaciones de EMF para este NPC. */
    /** Pausados por animaciones de Annis y por otros mods que animan al jugador (se juntan los motivos). */
    private static final Set<UUID> BY_ANNIS = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final Set<UUID> BY_OTHER = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** Otro mod esta animando al jugador con el modelo normal (Cinematic Interactions...): EMF no lo pisa. */
    public static void setOtherPaused(UUID id, boolean pause) {
        update(id, BY_OTHER, pause);
    }

    public static void setPaused(net.minecraft.world.entity.Entity npc, boolean pause) {
        update(npc.getUUID(), BY_ANNIS, pause);
    }

    private static void update(UUID id, Set<UUID> reason, boolean pause) {
        if (!loaded()) {
            return;
        }
        Set<UUID> set = pausedSet();
        if (set == null) {
            return;
        }
        if (pause) {
            reason.add(id);
        } else {
            reason.remove(id);
        }
        if (BY_ANNIS.contains(id) || BY_OTHER.contains(id)) {
            set.add(id);
        } else {
            set.remove(id);
        }
    }
}
