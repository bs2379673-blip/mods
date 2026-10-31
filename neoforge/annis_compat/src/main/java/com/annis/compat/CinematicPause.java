package com.annis.compat;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.ModList;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Cinematic Interactions + Punchy Moves: mientras un jugador esta en una estacion de Cinematic
 * (mesa, yunque, horno, fogata...), se guarda la pose que puso Cinematic justo antes de que Punchy
 * anime y se devuelve justo despues. Asi se ven las animaciones de Cinematic. Al entrar y salir
 * hay un fundido corto. Sin Cinematic instalado no hace nada.
 */
public final class CinematicPause {
    private static final float FADE_TICKS = 4.0f;
    private static final MethodHandle AT_STATION = find();
    private static final Map<Player, State> STATES = Collections.synchronizedMap(new WeakHashMap<>());

    private static final class State {
        boolean atStation;
        boolean saved;
        float weight;
        float lastAge = Float.NaN;
        final float[][] pose = new float[12][6];
        final boolean[] visible = new boolean[12];
    }

    private CinematicPause() {
    }

    private static MethodHandle find() {
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

    public static boolean enabled() {
        return AT_STATION != null;
    }

    private static boolean atStation(Player p) {
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

    /** Antes de Punchy: guarda la pose de Cinematic. */
    public static void save(PlayerModel<?> model, Player player) {
        boolean at = atStation(player);
        State st = STATES.get(player);
        if (!at && st == null) {
            return;
        }
        if (st == null) {
            st = new State();
            STATES.put(player, st);
        }
        st.atStation = at;
        st.saved = true;
        if (!at) {
            return; // saliendo: se funde desde la ultima pose de Cinematic
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

    /** Despues de Punchy: devuelve la pose de Cinematic. */
    public static void restore(PlayerModel<?> model, Player player, float ageInTicks) {
        State st = STATES.get(player);
        if (st == null || !st.saved) {
            return;
        }
        st.saved = false;
        float dt = Float.isNaN(st.lastAge) ? 0.0f : Math.max(0.0f, Math.min(5.0f, ageInTicks - st.lastAge));
        st.lastAge = ageInTicks;
        st.weight = Math.max(0.0f, Math.min(1.0f, st.weight + (st.atStation ? dt : -dt) / FADE_TICKS));
        if (!st.atStation && st.weight <= 0.0f) {
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
    }
}
