package com.starlevi.starframes.client;

import com.starlevi.starframes.block.ScreenBlockEntity;
import org.watermedia.api.player.SFXEngine;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * El audio de las pantallas para otros mods (StarFest hace que las luces sigan el video). Quien quiera
 * escuchar agrega un Consumer a LISTENERS; recibe Object[]{canal (String), audio (ByteBuffer de solo
 * lectura, muestras intercaladas), tipo ("S16", "FLT"...), canales (Integer), muestras por segundo (Integer)}.
 * Se llama desde el hilo del sonido: hay que copiar lo que se necesite y volver rapido.
 */
public final class AudioTap {
    public static final List<Consumer<Object[]>> LISTENERS = new CopyOnWriteArrayList<>();
    private static final Map<SFXEngine, String> ENGINES = Collections.synchronizedMap(new WeakHashMap<>());

    private AudioTap() {
    }

    static void engine(SFXEngine e, String channel) {
        if (e != null) {
            ENGINES.put(e, channel);
        }
    }

    public static void feed(SFXEngine e, ByteBuffer data) {
        if (LISTENERS.isEmpty() || data == null) {
            return;
        }
        String ch = ENGINES.get(e);
        if (ch == null || e.sampleType() == null) {
            return;
        }
        Object[] msg = {ch, data.asReadOnlyBuffer(), e.sampleType().name(), e.channels(), e.sampleRate()};
        for (Consumer<Object[]> l : LISTENERS) {
            try {
                l.accept(msg);
            } catch (Throwable ignored) {
                // un oyente roto no corta el sonido
            }
        }
    }

    /** Donde estan las pantallas de un canal (centros de los bloques), en el mundo del cliente. */
    public static List<double[]> screens(String channel) {
        List<double[]> out = new ArrayList<>();
        for (ScreenBlockEntity be : new ArrayList<>(ScreenBlockEntity.CLIENT)) {
            if (!be.isRemoved() && channel.equals(be.channel())) {
                out.add(new double[]{be.getBlockPos().getX() + 0.5, be.getBlockPos().getY() + 0.5, be.getBlockPos().getZ() + 0.5});
            }
        }
        return out;
    }
}
