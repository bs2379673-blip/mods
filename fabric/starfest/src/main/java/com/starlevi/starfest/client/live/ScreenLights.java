package com.starlevi.starfest.client.live;

import com.starlevi.starfest.StarFest;
import com.starlevi.starfest.client.Show;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Las pantallas de StarFrames: StarFrames nos pasa una copia del sonido de cada canal, lo analizamos en
 * vivo y las luces de StarFest cerca de esas pantallas siguen la musica del video.
 */
public final class ScreenLights {
    private static final class Ch {
        LiveAnalyzer analyzer;
        int rate;
        short[] pcm = new short[0];
        volatile long last;
        boolean added;
    }

    private static final Map<String, Ch> CHANNELS = new ConcurrentHashMap<>();
    private static MethodHandle screens;

    private ScreenLights() {
    }

    @SuppressWarnings("unchecked")
    public static void init() {
        if (!FabricLoader.getInstance().isModLoaded("starframes")) {
            return;
        }
        try {
            Class<?> tap = Class.forName("com.starlevi.starframes.client.AudioTap");
            List<Consumer<Object[]>> listeners = (List<Consumer<Object[]>>) tap.getField("LISTENERS").get(null);
            screens = MethodHandles.publicLookup().findStatic(tap, "screens", MethodType.methodType(List.class, String.class));
            listeners.add(ScreenLights::pcm);
            StarFest.LOGGER.info("StarFest: las luces siguen el audio de las pantallas de StarFrames");
        } catch (Throwable t) {
            StarFest.LOGGER.warn("StarFest: no pude escuchar las pantallas de StarFrames: {}", t.toString());
        }
    }

    /** Hilo del sonido: pasar a 16 bits y al analizador. */
    private static void pcm(Object[] m) {
        String id = (String) m[0];
        ByteBuffer b = ((ByteBuffer) m[1]).duplicate().order(ByteOrder.nativeOrder());
        String type = (String) m[2];
        int channels = Math.max(1, (Integer) m[3]);
        int rate = (Integer) m[4];
        if (rate <= 0) {
            return;
        }
        Ch c = CHANNELS.computeIfAbsent(id, k -> new Ch());
        synchronized (c) {
            if (c.analyzer == null || c.rate != rate) {
                c.analyzer = new LiveAnalyzer(rate);
                c.rate = rate;
            }
            int n;
            switch (type) {
                case "S16" -> {
                    n = b.remaining() / 2;
                    ensure(c, n);
                    for (int i = 0; i < n; i++) {
                        c.pcm[i] = b.getShort();
                    }
                }
                case "FLT" -> {
                    n = b.remaining() / 4;
                    ensure(c, n);
                    for (int i = 0; i < n; i++) {
                        c.pcm[i] = (short) Math.max(-32768, Math.min(32767, Math.round(b.getFloat() * 32767)));
                    }
                }
                case "S32" -> {
                    n = b.remaining() / 4;
                    ensure(c, n);
                    for (int i = 0; i < n; i++) {
                        c.pcm[i] = (short) (b.getInt() >> 16);
                    }
                }
                case "U8" -> {
                    n = b.remaining();
                    ensure(c, n);
                    for (int i = 0; i < n; i++) {
                        c.pcm[i] = (short) (((b.get() & 0xFF) - 128) << 8);
                    }
                }
                default -> {
                    return;
                }
            }
            // lo que se sube ahora suena un poco despues (lo que ya esta en cola en el parlante)
            c.analyzer.feed(c.pcm, n / channels, channels, 150_000_000L);
            c.last = System.nanoTime();
        }
    }

    private static void ensure(Ch c, int n) {
        if (c.pcm.length < n) {
            c.pcm = new short[n];
        }
    }

    /** Cada tick del cliente: las pantallas que suenan pasan a ser fuentes de musica para las luces. */
    @SuppressWarnings("unchecked")
    public static void tick() {
        if (CHANNELS.isEmpty() || screens == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        long now = System.nanoTime();
        for (Map.Entry<String, Ch> e : CHANNELS.entrySet()) {
            Ch c = e.getValue();
            String live = "pantalla:" + e.getKey();
            boolean active = now - c.last < 1_500_000_000L;
            if (!active) {
                if (c.added) {
                    Show.removeLive(live);
                    c.added = false;
                }
                continue;
            }
            Vec3 center = null;
            try {
                double best = Double.MAX_VALUE;
                for (double[] p : (List<double[]>) screens.invoke(e.getKey())) {
                    Vec3 v = new Vec3(p[0], p[1], p[2]);
                    double d = mc.player == null ? 0 : v.distanceToSqr(mc.player.position());
                    if (d < best) {
                        best = d;
                        center = v;
                    }
                }
            } catch (Throwable ignored) {
                // sin pantallas a la vista
            }
            if (center == null) {
                continue;
            }
            final LiveAnalyzer an = c.analyzer;
            Show.addLive(live, center, 40, an::get);
            c.added = true;
        }
    }

    public static void clear() {
        for (String id : CHANNELS.keySet()) {
            Show.removeLive("pantalla:" + id);
        }
        CHANNELS.clear();
    }
}
