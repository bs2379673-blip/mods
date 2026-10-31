package com.annis.compat.client;

import com.annis.compat.AnnisCompat;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.ModList;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Cinematic Interactions: mientras un jugador trabaja en una estacion (mesa, yunque, horno...),
 * Punchy Moves no le toca el cuerpo y se ven las animaciones propias de Cinematic.
 * Se guarda la pose justo antes de que Punchy anime y se devuelve justo despues, con un
 * fundido corto al entrar y al salir de la estacion.
 */
public final class CinematicModule {
    private static final boolean LOADED = ModList.get().isLoaded("cinematic");
    /** Fundido al entrar y salir de la estacion, en ticks. */
    private static final float FADE_TICKS = 4.0f;

    private static final Map<Player, State> STATES = Collections.synchronizedMap(new WeakHashMap<>());

    /** Para pruebas: fuerza una estacion en el jugador local (null = normal). */
    static String forced;
    static long forcedActionAt = -1;

    private static final class State {
        float weight;
        float lastAge = Float.NaN;
        boolean at;
        boolean captured;
        final float[][] pose = new float[12][6];
        final boolean[] visible = new boolean[12];
    }

    private CinematicModule() {
    }

    public static boolean active() {
        return LOADED || forced != null;
    }

    private static String stationOf(Player p) {
        if (forced != null && p == net.minecraft.client.Minecraft.getInstance().player) {
            return forced.isEmpty() ? null : forced;
        }
        return LOADED ? CinematicBridge.station(p.getId()) : null;
    }

    private static ModelPart[] parts(PlayerModel<?> m) {
        return new ModelPart[]{m.head, m.hat, m.body, m.jacket, m.rightArm, m.rightSleeve, m.leftArm, m.leftSleeve,
                m.rightLeg, m.rightPants, m.leftLeg, m.leftPants};
    }

    /** Antes de Punchy Moves: guarda la pose que dejo Cinematic. */
    public static void beforePunchy(PlayerModel<?> model, Player player) {
        String station = stationOf(player);
        State st = STATES.get(player);
        if (station == null && st == null) {
            return;
        }
        if (st == null) {
            st = new State();
            STATES.put(player, st);
            AnnisCompat.LOGGER.info("Annis Compat: {} entra a la estacion {} de Cinematic; Punchy en pausa", player.getName().getString(), station);
        }
        st.at = station != null;
        st.captured = true;
        if (!st.at) {
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

    /** Despues de Punchy Moves: devuelve la pose de Cinematic (mezclada mientras dura el fundido). */
    public static void afterPunchy(PlayerModel<?> model, Player player, float ageInTicks) {
        State st = STATES.get(player);
        if (st == null || !st.captured) {
            return;
        }
        st.captured = false;
        float dt = Float.isNaN(st.lastAge) ? 0.0f : Math.max(0.0f, Math.min(5.0f, ageInTicks - st.lastAge));
        st.lastAge = ageInTicks;
        st.weight = Math.max(0.0f, Math.min(1.0f, st.weight + (st.at ? dt : -dt) / FADE_TICKS));
        if (!st.at && st.weight <= 0.0f) {
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
