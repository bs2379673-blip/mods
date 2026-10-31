package com.annis.compat.client;

import com.annis.compat.AnnisCompat;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.ModList;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Cinematic Interactions: mientras un jugador trabaja en una estacion (mesa, yunque, horno...),
 * su cuerpo usa las animaciones de Punchy de Annis Compat en lugar de la pose fija de Cinematic
 * (que Punchy Moves pisa). Cada estacion tiene su animacion en bucle "annis_cine_<estacion>" y,
 * si la tiene, otra de accion "annis_cine_<estacion>_accion" que suena cada vez que Cinematic
 * marca una accion (martillazo, girar la piedra...).
 */
public final class CinematicModule {
    public static final String CLIP_PREFIX = "annis_cine_";
    private static final boolean LOADED = ModList.get().isLoaded("cinematic");
    /** Estaciones que comparten animacion con otra. */
    private static final Map<String, String> ALIAS = Map.of(
            "smoker", "furnace",
            "blast_furnace", "furnace",
            "brewing", "furnace",
            "beacon", "enchanting",
            "campfire_sentado", "campfire",
            "chiseled_bookshelf_leyendo", "chiseled_bookshelf");
    /** Fundido al entrar y salir de la estacion, en ticks. */
    private static final float FADE_TICKS = 5.0f;

    private static final Map<Player, State> STATES = Collections.synchronizedMap(new WeakHashMap<>());

    /** Para pruebas: fuerza una estacion en el jugador local (null = normal). */
    static String forced;
    static long forcedActionAt = -1;

    private static final class State {
        String station;
        float weight;
        float lastAge = Float.NaN;
        final PoseApplier.Sample idle = new PoseApplier.Sample();
        final PoseApplier.Sample action = new PoseApplier.Sample();
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

    private static double sinceAction(Player p) {
        if (forced != null && p == net.minecraft.client.Minecraft.getInstance().player) {
            return forcedActionAt < 0 ? 1e9 : (System.nanoTime() - forcedActionAt) / 1e9;
        }
        return LOADED ? CinematicBridge.sinceAction(p.getId()) : 1e9;
    }

    /** La animacion de la estacion: la propia, la compartida o la general. */
    private static Clip find(String station, String suffix) {
        String s = station;
        for (int i = 0; i < 3 && s != null; i++) {
            Clip c = ClipLibrary.get(CLIP_PREFIX + s + suffix);
            if (c != null) {
                return c;
            }
            s = ALIAS.get(s);
        }
        return suffix.isEmpty() ? ClipLibrary.get(CLIP_PREFIX + "default") : null;
    }

    /** Desde el final de PlayerModel.setupAnim (despues de Punchy Moves). */
    public static void afterSetupAnim(PlayerModel<?> model, Player player, float ageInTicks) {
        String station = stationOf(player);
        State st = STATES.get(player);
        if (station == null && st == null) {
            return;
        }
        if (st == null) {
            st = new State();
            STATES.put(player, st);
            AnnisCompat.LOGGER.info("Annis Compat: {} entra a la estacion {} de Cinematic", player.getName().getString(), station);
        }
        if (station != null && !station.equals(st.station)) {
            st.station = station;
        }
        float dt = Float.isNaN(st.lastAge) ? 0.0f : Math.max(0.0f, Math.min(5.0f, ageInTicks - st.lastAge));
        st.lastAge = ageInTicks;
        st.weight = Math.max(0.0f, Math.min(1.0f, st.weight + (station != null ? dt : -dt) / FADE_TICKS));
        if (station == null && st.weight <= 0.0f) {
            STATES.remove(player);
            return;
        }
        Clip idle = find(st.station, "");
        if (idle == null) {
            return;
        }
        float seconds = ageInTicks / 20.0f;
        st.idle.clear();
        st.idle.read(idle, idle.time(seconds));
        Clip action = find(st.station, "_accion");
        if (action != null && action.length > 0) {
            double since = sinceAction(player);
            if (since >= 0 && since < action.length) {
                float t = (float) since;
                // entra y sale suave de la animacion de accion
                float wa = Math.min(1.0f, Math.min(t / 0.12f, (action.length - t) / 0.2f));
                st.action.clear();
                st.action.read(action, t);
                st.idle.blend(st.action, smooth(Math.max(0.0f, wa)));
            }
        }
        PoseApplier.apply(model, st.idle, smooth(st.weight));
    }

    private static float smooth(float x) {
        return x * x * (3 - 2 * x);
    }
}
