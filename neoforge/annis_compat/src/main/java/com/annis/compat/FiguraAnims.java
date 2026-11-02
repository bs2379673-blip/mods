package com.annis.compat;

import net.minecraft.world.entity.Entity;
import net.neoforged.fml.ModList;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Figura: dice si el avatar de un jugador esta reproduciendo una animacion propia que mueve el cuerpo
 * (cabeza, torso, brazos o piernas). Una animacion que solo mueve accesorios (cola, orejas, alas)
 * no cuenta: con esas el avatar sigue usando las animaciones de Punchy. Sin Figura no hace nada.
 */
public final class FiguraAnims {
    private static final Set<String> BODY = Set.of("Head", "Body", "LeftArm", "RightArm", "LeftLeg", "RightLeg");
    private static final Map<Object, Boolean> MOVES_BODY = Collections.synchronizedMap(new WeakHashMap<>());

    private static Method getAvatar, isStopped;
    private static Field animations, animationParts, parentType;
    private static final boolean ENABLED = init();

    private FiguraAnims() {
    }

    private static boolean init() {
        if (!ModList.get().isLoaded("figura")) {
            return false;
        }
        try {
            ClassLoader cl = FiguraAnims.class.getClassLoader();
            Class<?> manager = Class.forName("org.figuramc.figura.avatar.AvatarManager", false, cl);
            Class<?> avatar = Class.forName("org.figuramc.figura.avatar.Avatar", false, cl);
            Class<?> anim = Class.forName("org.figuramc.figura.animation.Animation", false, cl);
            getAvatar = manager.getMethod("getAvatar", Entity.class);
            animations = avatar.getField("animations");
            isStopped = anim.getMethod("isStopped");
            try {
                animationParts = anim.getDeclaredField("animationParts");
                animationParts.setAccessible(true);
                Class<?> part = Class.forName("org.figuramc.figura.model.FiguraModelPart", false, cl);
                parentType = part.getField("parentType");
            } catch (Throwable t) {
                // sin acceso a las partes: cualquier animacion en marcha cuenta
                animationParts = null;
                AnnisCompat.LOGGER.info("Annis Compat: Figura sin detalle de partes ({}); cualquier animacion pausa Punchy", t.toString());
            }
            AnnisCompat.LOGGER.info("Annis Compat: Figura detectado, sus animaciones propias pausan Punchy");
            return true;
        } catch (Throwable t) {
            AnnisCompat.LOGGER.warn("Annis Compat: no se pudo enlazar con Figura: {}", t.toString());
            return false;
        }
    }

    public static boolean enabled() {
        return ENABLED;
    }

    /** true si el avatar de esta entidad tiene en marcha una animacion que mueve el cuerpo. */
    public static boolean playing(Entity e) {
        if (!ENABLED) {
            return false;
        }
        try {
            Object avatar = getAvatar.invoke(null, e);
            if (avatar == null) {
                return false;
            }
            Object map = animations.get(avatar);
            if (!(map instanceof Map<?, ?> m) || m.isEmpty()) {
                return false;
            }
            for (Object a : List.copyOf(m.values())) {
                if (a != null && !(Boolean) isStopped.invoke(a) && movesBody(a)) {
                    return true;
                }
            }
        } catch (Throwable t) {
            // avatar cargando o cambiando
        }
        return false;
    }

    private static boolean movesBody(Object anim) {
        if (animationParts == null) {
            return true;
        }
        Boolean known = MOVES_BODY.get(anim);
        if (known != null) {
            return known;
        }
        boolean body = false;
        try {
            Object list = animationParts.get(anim);
            if (list instanceof Collection<?> c) {
                for (Object entry : c) {
                    if (entry instanceof Map.Entry<?, ?> en && en.getKey() != null) {
                        Object type = parentType.get(en.getKey());
                        if (type != null && BODY.contains(type instanceof Enum<?> en2 ? en2.name() : type.toString())) {
                            body = true;
                            break;
                        }
                    }
                }
            }
        } catch (Throwable t) {
            body = true;
        }
        MOVES_BODY.put(anim, body);
        return body;
    }
}
