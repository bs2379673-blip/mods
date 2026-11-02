package com.annis.compat;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.ModList;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Pausa Punchy Moves en un jugador cuando otro mod trae su propia animacion:
 * <ul>
 * <li>Cinematic Interactions: mientras esta en una estacion (mesa, yunque, horno, fogata...).</li>
 * <li>Figura: mientras su avatar reproduce una animacion propia que mueve el cuerpo.</li>
 * </ul>
 * Se guarda la pose justo antes de que Punchy anime y se devuelve justo despues, con un fundido corto
 * al entrar y al salir. Sin Figura, Punchy sigue animando y los modelos de Figura lo siguen (Figura
 * copia la pose de cada parte del jugador despues de que Punchy la movio).
 */
public final class PunchyPause {
    private static final float FADE_TICKS = 4.0f;
    private static final MethodHandle AT_STATION = findStation();
    private static final MethodHandle ROOT = findRoot();
    private static final Map<Player, State> STATES = Collections.synchronizedMap(new WeakHashMap<>());

    private static final class State {
        boolean paused;
        boolean figura;
        boolean saved;
        float weight;
        float lastAge = Float.NaN;
        final float[][] pose = new float[12][6];
        final boolean[] visible = new boolean[12];
    }

    private PunchyPause() {
    }

    private static MethodHandle findStation() {
        if (!ModList.get().isLoaded("cinematic")) {
            return null;
        }
        try {
            Class<?> c = Class.forName("dev.dyslexic.cinematic.client.ClientPayloads");
            Class<?> kind = Class.forName("dev.dyslexic.cinematic.station.StationKind");
            return MethodHandles.publicLookup().findStatic(c, "atStation", MethodType.methodType(kind, int.class));
        } catch (Throwable t) {
            return null;
        }
    }

    /** La parte raiz que Punchy agrega al modelo (inclina y mueve todo el cuerpo). */
    private static MethodHandle findRoot() {
        try {
            Class<?> c = Class.forName("punchymoves.client.render.PlayerModelRoot", false, PunchyPause.class.getClassLoader());
            return MethodHandles.publicLookup().findVirtual(c, "punchymoves$root", MethodType.methodType(ModelPart.class))
                    .asType(MethodType.methodType(ModelPart.class, Object.class));
        } catch (Throwable t) {
            return null;
        }
    }

    public static boolean enabled() {
        return AT_STATION != null || FiguraAnims.enabled();
    }

    private static boolean atStation(Player p) {
        if (AT_STATION == null) {
            return false;
        }
        try {
            return AT_STATION.invoke(p.getId()) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    private static ModelPart[] parts(PlayerModel<?> m) {
        return new ModelPart[]{m.head, m.hat, m.body, m.jacket, m.rightArm, m.rightSleeve, m.leftArm, m.leftSleeve,
                m.rightLeg, m.rightPants, m.leftLeg, m.leftPants};
    }

    /** Antes de Punchy: guarda la pose que dejo el juego (o Cinematic). */
    public static void save(PlayerModel<?> model, Player player) {
        boolean station = atStation(player);
        boolean figura = !station && FiguraAnims.playing(player);
        boolean pause = station || figura;
        State st = STATES.get(player);
        if (!pause && st == null) {
            return;
        }
        if (st == null) {
            st = new State();
            STATES.put(player, st);
        }
        st.paused = pause;
        st.saved = true;
        if (pause) {
            st.figura = figura;
        }
        if (!pause) {
            return; // saliendo: se funde desde la ultima pose guardada
        }
        ModelPart[] ps = parts(model);
        for (int i = 0; i < ps.length; i++) {
            ModelPart p = ps[i];
            float[] v = st.pose[i];
            v[0] = p.xRot;
            v[1] = p.yRot;
            v[2] = p.zRot;
            v[3] = p.x;
            v[4] = p.y;
            v[5] = p.z;
            st.visible[i] = p.visible;
        }
    }

    /** Despues de Punchy: devuelve la pose guardada. */
    public static void restore(PlayerModel<?> model, Player player, float ageInTicks) {
        State st = STATES.get(player);
        if (st == null || !st.saved) {
            return;
        }
        st.saved = false;
        float dt = Float.isNaN(st.lastAge) ? 0.0f : Math.max(0.0f, Math.min(5.0f, ageInTicks - st.lastAge));
        st.lastAge = ageInTicks;
        st.weight = Math.max(0.0f, Math.min(1.0f, st.weight + (st.paused ? dt : -dt) / FADE_TICKS));
        if (!st.paused && st.weight <= 0.0f) {
            STATES.remove(player);
            return;
        }
        float w = st.weight * st.weight * (3 - 2 * st.weight);
        ModelPart[] ps = parts(model);
        for (int i = 0; i < ps.length; i++) {
            ModelPart p = ps[i];
            float[] v = st.pose[i];
            p.xRot += (v[0] - p.xRot) * w;
            p.yRot += (v[1] - p.yRot) * w;
            p.zRot += (v[2] - p.zRot) * w;
            p.x += (v[3] - p.x) * w;
            p.y += (v[4] - p.y) * w;
            p.z += (v[5] - p.z) * w;
            if (w > 0.5f) {
                p.visible = st.visible[i];
            }
        }
        if (st.figura) {
            // Figura: el avatar tambien deja de inclinarse y moverse con la raiz de Punchy
            neutralRoot(model, w);
        }
        // la segunda capa sigue al cuerpo: Cinematic mueve la cabeza (fogata, echado hacia atras) despues
        // de que el juego copio su posicion al sombrero, y el sombrero quedaba despegado adelante
        model.hat.copyFrom(model.head);
        model.jacket.copyFrom(model.body);
        model.rightSleeve.copyFrom(model.rightArm);
        model.leftSleeve.copyFrom(model.leftArm);
        model.rightPants.copyFrom(model.rightLeg);
        model.leftPants.copyFrom(model.leftLeg);
    }

    private static void neutralRoot(PlayerModel<?> model, float w) {
        if (ROOT == null) {
            return;
        }
        try {
            ModelPart r = (ModelPart) ROOT.invoke((Object) model);
            if (r == null) {
                return;
            }
            PartPose init = r.getInitialPose();
            r.x += (init.x - r.x) * w;
            r.y += (init.y - r.y) * w;
            r.z += (init.z - r.z) * w;
            r.xRot += (init.xRot - r.xRot) * w;
            r.yRot += (init.yRot - r.yRot) * w;
            r.zRot += (init.zRot - r.zRot) * w;
            r.xScale += (1 - r.xScale) * w;
            r.yScale += (1 - r.yScale) * w;
            r.zScale += (1 - r.zScale) * w;
        } catch (Throwable ignored) {
            // Punchy sin raiz
        }
    }
}
