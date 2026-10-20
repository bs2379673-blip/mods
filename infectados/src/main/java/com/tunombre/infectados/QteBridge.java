package com.tunombre.infectados;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Puente opcional con el mod "QuickTime Events" (por reflexion: si no esta instalado, no pasa nada y se usa el quick time event propio).
 * El quick time lo dibuja y lo juega el cliente; aqui solo se arranca uno guardado con nombre y se escucha si salio bien o mal.
 */
public final class QteBridge {
    private record Pending(String name, Consumer<Boolean> done) { }

    private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();
    private static boolean tried, ok;
    private static Method mStart, mHas, mNames, mStop, mAdd, mSaved, mSuccess;

    private QteBridge() { }

    private static synchronized boolean init() {
        if (tried) return ok;
        tried = true;
        try {
            if (!ModList.get().isLoaded("quicktime_events")) return false;
            Class<?> api = Class.forName("com.cotii.api.QuickTimeEventsServerApi");
            Class<?> lis = Class.forName("com.cotii.api.ServerQuickTimeListener");
            Class<?> ev = Class.forName("com.cotii.api.QuickTimeEvent");
            mStart = api.getMethod("startSaved", ServerPlayer.class, String.class);
            mHas = api.getMethod("hasMod", ServerPlayer.class);
            mNames = api.getMethod("savedNames");
            mStop = api.getMethod("stop", ServerPlayer.class, String.class);
            mAdd = api.getMethod("addListener", lis);
            mSaved = ev.getMethod("saved");
            mSuccess = ev.getMethod("success");
            Object listener = Proxy.newProxyInstance(lis.getClassLoader(), new Class<?>[]{lis}, (proxy, m, args) -> {
                if (m.getName().equals("onFinish") && args != null && args.length == 2 && args[0] instanceof ServerPlayer p) finished(p, args[1]);
                return null;
            });
            mAdd.invoke(null, listener);
            ok = true;
        } catch (Throwable t) {
            ok = false;
        }
        return ok;
    }

    private static void finished(ServerPlayer p, Object event) {
        try {
            Pending pend = PENDING.get(p.getUUID());
            if (pend == null || !pend.name().equals(mSaved.invoke(event))) return;
            PENDING.remove(p.getUUID());
            boolean success = (Boolean) mSuccess.invoke(event);
            p.server.execute(() -> pend.done().accept(success));
        } catch (Throwable ignored) { }
    }

    /** true si habia un quick time guardado con ese nombre y se arranco (el jugador necesita el mod); done recibe si lo gano */
    public static boolean start(ServerPlayer p, String name, Consumer<Boolean> done) {
        if (name == null || name.isBlank() || !init()) return false;
        try {
            if (!(Boolean) mHas.invoke(null, p)) return false;
            if (!((List<?>) mNames.invoke(null)).contains(name)) return false;
            PENDING.put(p.getUUID(), new Pending(name, done));
            if (!(Boolean) mStart.invoke(null, p, name)) { PENDING.remove(p.getUUID()); return false; }
            return true;
        } catch (Throwable t) {
            PENDING.remove(p.getUUID());
            return false;
        }
    }

    /** cancela el quick time en curso de ese jugador (sin avisar al que lo pidio) */
    public static void stop(ServerPlayer p) {
        Pending pend = PENDING.remove(p.getUUID());
        if (pend == null || !init()) return;
        try { mStop.invoke(null, p, pend.name()); } catch (Throwable ignored) { }
    }
}
