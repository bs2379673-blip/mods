package com.annis.interact;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Catalogo de interacciones especiales. */
public record Interaction(String id, String name, String animA, String animB, Place place, int ticks,
                          Kind kind, boolean romantic, int hearts, int heartsGain, int page, boolean swap) {

    /** Como se colocan los dos. */
    public enum Place {
        /** Frente a frente a "dist" bloques. */
        FACE,
        /** Frente a frente, desplazado a un lado (beso en la mejilla). */
        KISS,
        /** El otro a la derecha, mirando hacia el mismo lado. */
        RIGHT,
        /** El otro va en brazos (montado delante). */
        CARRY_FRONT,
        /** El otro va a caballito (montado detras). */
        CARRY_BACK,
        /** El que ayuda se pone a los pies del caido. */
        HELP
    }

    public enum Kind {
        /** Dura "ticks" y termina sola. */
        SCENE,
        /** Sigue hasta que alguien pulse Shift. */
        LOOP,
        /** De la mano: caminan juntos. */
        FOLLOW,
        /** En brazos o a caballito. */
        CARRY,
        /** Levantar a un caido. */
        REVIVE,
        /** Mimos: uno toca al otro con el raton hasta que alguien pulse Shift. */
        TOUCH
    }

    private static final Map<String, Interaction> BY_ID = new LinkedHashMap<>();

    public static final Interaction HUG = add(new Interaction("abrazo", "Abrazo", "abrazo", "abrazo_cuello",
            Place.FACE, 80, Kind.SCENE, false, 15, 1, 0, false));
    public static final Interaction KISS = add(new Interaction("beso", "Beso en la mejilla", "beso_mejilla", "recibir_beso",
            Place.KISS, 60, Kind.SCENE, true, 45, 2, 0, false));
    public static final Interaction HIGH_FIVE = add(new Interaction("chocar_cinco", "Chocar los cinco", "chocar_cinco", "chocar_cinco",
            Place.FACE, 45, Kind.SCENE, false, -5, 1, 0, false));
    public static final Interaction HANDSHAKE = add(new Interaction("apreton", "Apreton de manos", "apreton_manos", "apreton_manos",
            Place.FACE, 50, Kind.SCENE, false, -20, 1, 0, false));
    public static final Interaction PAT = add(new Interaction("palmadita", "Palmadita en el hombro", "palmadita", "recibir_palmadita",
            Place.FACE, 45, Kind.SCENE, false, 0, 1, 0, false));
    public static final Interaction HAND = add(new Interaction("de_la_mano", "Ir de la mano", "de_la_mano_d", "de_la_mano_i",
            Place.RIGHT, 0, Kind.FOLLOW, true, 30, 1, 0, false));
    public static final Interaction CARRY = add(new Interaction("en_brazos", "Llevar en brazos", "cargar_brazos", "en_brazos",
            Place.CARRY_FRONT, 0, Kind.CARRY, true, 45, 1, 0, false));
    public static final Interaction PIGGY = add(new Interaction("caballito", "A caballito", "llevar_caballito", "a_caballito",
            Place.CARRY_BACK, 0, Kind.CARRY, false, 25, 1, 0, false));
    public static final Interaction WALTZ = add(new Interaction("vals", "Bailar en pareja", "vals_guia", "vals_pareja",
            Place.FACE, 0, Kind.LOOP, true, 35, 1, 0, false));
    public static final Interaction STARS = add(new Interaction("estrellas", "Mirar las estrellas", "estrellas_a", "estrellas_b",
            Place.RIGHT, 0, Kind.LOOP, true, 35, 1, 0, false));
    public static final Interaction SIT = add(new Interaction("sentarse", "Sentarse juntos", "sentarse_abrazo", "sentarse_apoyo",
            Place.RIGHT, 0, Kind.LOOP, false, 20, 1, 0, false));
    public static final Interaction HELP = add(new Interaction("ayudar", "Ayudar a levantarse", "ayudar_levantar", "levantarse",
            Place.HELP, 50, Kind.REVIVE, false, -100, 2, 0, false));

    // ---- segunda pagina de la rueda ----
    public static final Interaction PROPOSE = add(new Interaction("propuesta", "Proponer matrimonio", "propuesta", "recibir_propuesta",
            Place.FACE, 100, Kind.SCENE, true, 60, 3, 1, false));
    public static final Interaction ASK_CARRY = add(new Interaction("pedir_brazos", "Que me lleve en brazos", "en_brazos", "cargar_brazos",
            Place.CARRY_FRONT, 0, Kind.CARRY, true, 45, 1, 1, true));
    public static final Interaction ASK_PIGGY = add(new Interaction("pedir_caballito", "Que me lleve a caballito", "a_caballito", "llevar_caballito",
            Place.CARRY_BACK, 0, Kind.CARRY, false, 25, 1, 1, true));
    public static final Interaction SLOW = add(new Interaction("baile_lento", "Baile lento abrazados", "lento_a", "lento_b",
            Place.FACE, 0, Kind.LOOP, true, 45, 1, 1, false));
    public static final Interaction TOUCH = add(new Interaction("mimos", "Mimos (tocar con el raton)", "mimos_toca", "mimos_recibe",
            Place.FACE, 0, Kind.TOUCH, true, 40, 1, 1, false));
    public static final Interaction LEAN = add(new Interaction("hombro", "Apoyarme en su hombro", "hombro_apoya", "hombro_rodea",
            Place.RIGHT, 0, Kind.LOOP, true, 30, 1, 1, true));
    public static final Interaction HAND_KISS = add(new Interaction("besar_mano", "Besar la mano", "besar_mano", "recibir_beso_mano",
            Place.FACE, 70, Kind.SCENE, true, 35, 2, 1, false));

    private static Interaction add(Interaction i) {
        BY_ID.put(i.id, i);
        return i;
    }

    public static Interaction get(String id) {
        return BY_ID.get(id);
    }

    public static List<Interaction> all() {
        return Collections.unmodifiableList(new ArrayList<>(BY_ID.values()));
    }

    /** Distancia o desplazamiento principal de la colocacion (bloques). */
    public float dist() {
        return switch (this.id) {
            case "abrazo" -> Layout.HUG;
            case "chocar_cinco" -> Layout.HIGH_FIVE;
            case "apreton" -> Layout.SHAKE;
            case "palmadita" -> Layout.PAT;
            case "de_la_mano" -> Layout.HAND_SIDE;
            case "vals" -> Layout.WALTZ;
            case "estrellas" -> Layout.STARS_SIDE;
            case "sentarse" -> Layout.SIT_SIDE;
            case "ayudar" -> Layout.HELP;
            case "propuesta" -> Layout.PROPOSE;
            case "baile_lento" -> Layout.SLOW;
            case "mimos" -> Layout.TOUCH;
            case "hombro" -> Layout.LEAN_SIDE;
            case "besar_mano" -> Layout.HAND_KISS;
            default -> 0.0f;
        };
    }

    public boolean timed() {
        return this.kind == Kind.SCENE || this.kind == Kind.REVIVE;
    }
}
