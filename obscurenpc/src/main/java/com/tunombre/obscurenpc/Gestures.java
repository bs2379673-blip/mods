package com.tunombre.obscurenpc;

import yesman.epicfight.api.animation.AnimationManager;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.gameasset.Animations;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Catalogo de gestos: los propios (talk_a, wave...) y cualquier animacion de Epic Fight o de otro mod por su nombre. */
public final class Gestures {
    /** gestos de un solo golpe (terminan en reposo) */
    public static final String[] ONESHOT = {"talk_a", "talk_b", "talk_c", "talk_d", "talk_e", "point", "wave", "think", "salute", "shrug",
            "nod", "shake_head", "look_around", "facepalm", "beckon", "stop", "scan", "plead", "nervous", "grieve", "defiant", "command", "listen"};
    /** posturas que se mantienen (bucle) */
    public static final String[] LOOPS = {"cross_arms", "hands_back", "hands_hip", "think_pose", "guard"};

    public static final Map<String, AnimationManager.AnimationAccessor<? extends StaticAnimation>> MINE = new LinkedHashMap<>();

    private static final Map<String, String> ALIAS = Map.ofEntries(
            Map.entry("comer", "BIPED_EAT"), Map.entry("beber", "BIPED_DRINK"), Map.entry("cavar", "BIPED_DIG"),
            Map.entry("sentarse", "BIPED_SIT"), Map.entry("dormir", "BIPED_SLEEPING"), Map.entry("arrodillarse", "BIPED_KNEEL"),
            Map.entry("saltar", "BIPED_JUMP"), Map.entry("rodar", "BIPED_ROLL_FORWARD"), Map.entry("paso_atras", "BIPED_STEP_BACKWARD"),
            Map.entry("paso_adelante", "BIPED_STEP_FORWARD"), Map.entry("paso_izq", "BIPED_STEP_LEFT"), Map.entry("paso_der", "BIPED_STEP_RIGHT"),
            Map.entry("caer", "BIPED_KNOCKDOWN"), Map.entry("golpeado", "BIPED_HIT_LONG"), Map.entry("catalejo", "BIPED_SPYGLASS_USE"),
            Map.entry("mapa", "BIPED_HOLD_MAP_TWOHAND"), Map.entry("lanzar", "BIPED_MOB_THROW"));

    private Gestures() { }

    @SuppressWarnings("unchecked")
    public static AnimationManager.AnimationAccessor<? extends StaticAnimation> find(String id) {
        if (id == null || id.isBlank()) return null;
        String k = id.trim().toLowerCase(Locale.ROOT);
        var mine = MINE.get(k);
        if (mine != null) return mine;
        String field = ALIAS.get(k);
        if (field == null && k.startsWith("biped_")) field = k.toUpperCase(Locale.ROOT);
        if (field != null) {
            try {
                Object o = Animations.class.getField(field).get(null);
                if (o instanceof AnimationManager.AnimationAccessor<?> acc) return (AnimationManager.AnimationAccessor<? extends StaticAnimation>) acc;
            } catch (ReflectiveOperationException | RuntimeException ignored) { }
        }
        if (k.contains(":")) {
            try {
                AnimationManager.AnimationAccessor<StaticAnimation> acc = AnimationManager.byKey(k);
                if (acc != null) return acc;
            } catch (RuntimeException ignored) { }
        }
        return null;
    }
}
