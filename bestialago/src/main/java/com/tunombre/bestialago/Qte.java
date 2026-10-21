package com.tunombre.bestialago;

import com.cotii.api.QuickTimeEvent;
import com.cotii.api.QuickTimeEventsServerApi;
import com.cotii.api.ServerQuickTimeListener;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cadena de quick time events del mod QuickTime Events (dependencia obligatoria).
 * Cada fase es un quick time guardado en el servidor (ver {@link QteDefs}); si se gana una pasa a la siguiente y si se
 * pierde cualquiera termina todo. El cliente dibuja y juega cada fase; aqui solo se arranca, se escucha y se encadena.
 */
public final class Qte {
    /** lo que le importa al que lanza la cadena */
    public interface Run {
        /** una pulsacion valida en la fase {@code phase} (0 = la primera) */
        default void press(int phase, int presses, int required) { }

        /** fase terminada y ganada (no la ultima); se llama antes de arrancar la siguiente */
        default void phaseWon(int phase) { }

        /** fin de la cadena: ganada entera o perdida (reason: timeout, missed, pressed, death...) */
        void end(boolean won, String reason);
    }

    private static final class Active {
        final List<String> phases;
        final Run run;
        int idx;

        Active(List<String> phases, Run run) {
            this.phases = phases;
            this.run = run;
        }

        String current() { return phases.get(idx); }
    }

    private static final Map<UUID, Active> ACTIVE = new ConcurrentHashMap<>();
    private static boolean registered;

    private Qte() { }

    /** engancha el oyente del mod QuickTime Events (una sola vez) */
    public static synchronized void init() {
        if (registered) return;
        registered = true;
        QuickTimeEventsServerApi.addListener(new ServerQuickTimeListener() {
            @Override
            public void onPress(ServerPlayer p, QuickTimeEvent e) {
                Active a = ACTIVE.get(p.getUUID());
                if (a == null || !a.current().equalsIgnoreCase(e.saved())) return;
                a.run.press(a.idx, e.presses(), e.required());
            }

            @Override
            public void onFinish(ServerPlayer p, QuickTimeEvent e) {
                Active a = ACTIVE.get(p.getUUID());
                if (a == null || !a.current().equalsIgnoreCase(e.saved())) return;
                p.server.execute(() -> advance(p, a, e.success(), e.reason()));
            }
        });
    }

    private static void advance(ServerPlayer p, Active a, boolean won, String reason) {
        if (ACTIVE.get(p.getUUID()) != a) return;
        if (won && a.idx + 1 < a.phases.size()) {
            a.run.phaseWon(a.idx);
            if (ACTIVE.get(p.getUUID()) != a) return;     // el que escucha pudo cancelar la cadena
            a.idx++;
            if (!QuickTimeEventsServerApi.startSaved(p, a.current())) {
                ACTIVE.remove(p.getUUID(), a);
                a.run.end(false, "no_start");
            }
            return;
        }
        ACTIVE.remove(p.getUUID(), a);
        a.run.end(won, reason == null ? "" : reason);
    }

    /** el servidor de QuickTime Events esta activo, el jugador tiene el mod y estan todas las fases guardadas */
    public static boolean available(ServerPlayer p, List<String> phases) {
        try {
            if (!QuickTimeEventsServerApi.isEnabled() || !QuickTimeEventsServerApi.hasMod(p)) return false;
            List<String> have = QuickTimeEventsServerApi.savedNames();
            for (String ph : phases) if (have.stream().noneMatch(ph::equalsIgnoreCase)) return false;
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** arranca la cadena; false si no se pudo (no queda nada en marcha) */
    public static boolean start(ServerPlayer p, List<String> phases, Run run) {
        init();
        cancel(p);
        Active a = new Active(phases, run);
        ACTIVE.put(p.getUUID(), a);
        if (QuickTimeEventsServerApi.startSaved(p, a.current())) return true;
        ACTIVE.remove(p.getUUID(), a);
        return false;
    }

    /** corta la cadena en curso de ese jugador sin avisar al que la lanzo */
    public static void cancel(ServerPlayer p) {
        Active a = ACTIVE.remove(p.getUUID());
        if (a == null) return;
        try { QuickTimeEventsServerApi.stop(p, a.current()); } catch (Throwable ignored) { }
    }

    public static boolean running(ServerPlayer p) { return ACTIVE.containsKey(p.getUUID()); }
}
