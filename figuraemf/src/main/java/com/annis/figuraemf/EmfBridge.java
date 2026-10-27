package com.annis.figuraemf;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.render.entity.model.EntityModel;

import java.lang.reflect.Method;

/**
 * Adelanta la animacion de EMF para que Figura lea la pose ya animada.
 *
 * En 1.21.1 EMF anima las partes del modelo cuando se dibujan, pero Figura copia la pose de
 * cabeza/cuerpo/brazos justo despues de setAngles, antes de eso: por eso los modelos de Figura
 * se quedaban con la pose vanilla. Aqui se llama a la animacion de EMF en ese punto.
 * EMF no la repite despues (controla que cada modelo se anime una vez por entidad renderizada).
 *
 * Todo por reflexion: no hace falta EMF ni Figura para compilar y, si una version no coincide,
 * se desactiva solo.
 */
public final class EmfBridge {
    private static final boolean ACTIVE = FabricLoader.getInstance().isModLoaded("figura")
            && FabricLoader.getInstance().isModLoaded("entity_model_features");
    private static boolean broken;
    private static int errors;

    private static Method isEmfModel;
    private static Method getRoot;
    private static Method oneTime;
    private static Method hasAnimation;
    private static Method animate;
    private static Class<?> resolvedFor;

    private EmfBridge() {
    }

    public static void animateEarly(EntityModel<?> model) {
        if (!ACTIVE || broken || model == null) {
            return;
        }
        try {
            Class<?> c = model.getClass();
            if (resolvedFor != c) {
                isEmfModel = c.getMethod("emf$isEMFModel");
                getRoot = c.getMethod("emf$getEMFRootModel");
                resolvedFor = c;
            }
            if (!(Boolean) isEmfModel.invoke(model)) {
                return;
            }
            Object root = getRoot.invoke(model);
            if (root == null) {
                return;
            }
            if (animate == null) {
                Class<?> rc = root.getClass();
                hasAnimation = rc.getMethod("hasAnimation");
                oneTime = rc.getMethod("oneTimeRunnable");
                animate = rc.getMethod("animate");
            }
            if (!(Boolean) hasAnimation.invoke(root)) {
                return;
            }
            oneTime.invoke(root);
            animate.invoke(root);
        } catch (NoSuchMethodException e) {
            broken = true;
            FiguraEmfFix.LOGGER.warn("Esta version de EMF no es compatible con el arreglo: {}", e.toString());
        } catch (Throwable t) {
            if (++errors >= 5) {
                broken = true;
                FiguraEmfFix.LOGGER.warn("Arreglo desactivado tras varios errores", t);
            }
        }
    }
}
