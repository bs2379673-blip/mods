package com.starlevi.starfest.client.live;

import com.sun.jna.Pointer;
import com.starlevi.starfest.StarFest;
import com.starlevi.starfest.client.Show;
import me.srrapero720.waterframes.common.block.entity.DisplayTile;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.watermedia.api.player.videolan.VideoPlayer;
import org.watermedia.videolan4j.player.base.MediaPlayer;
import org.watermedia.videolan4j.player.base.callback.AudioCallback;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.nio.ByteOrder;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * El sonido de una pantalla de WaterFrames: VLC nos entrega el audio en vez de mandarlo directo a
 * los parlantes; nosotros lo reproducimos igual (con el volumen y el silencio que pone WaterFrames)
 * y de paso lo analizamos para que las luces de StarFest cercanas sigan la musica del video.
 */
public final class ScreenAudio implements AudioCallback {
    private static final int RATE = 48000, CHANNELS = 2;
    private static final Map<Object, ScreenAudio> BY_DISPLAY = Collections.synchronizedMap(new WeakHashMap<>());
    private static int counter;

    private final String id;
    private final LiveAnalyzer analyzer = new LiveAnalyzer(RATE);
    private SourceDataLine line;
    private volatile float gain = 1;
    private volatile boolean muted;
    private byte[] bytes = new byte[0];
    private short[] pcm = new short[0];
    private final boolean bigEndian = ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN;

    private ScreenAudio(String id) {
        this.id = id;
    }

    /** Desde la pantalla, justo antes de que arranque el video. */
    public static void attach(Object display, VideoPlayer player, DisplayTile tile) {
        try {
            if (player == null || player.raw() == null || tile == null || tile.getLevel() == null) {
                return;
            }
            ScreenAudio sa = new ScreenAudio("pantalla#" + (++counter) + "@" + tile.getBlockPos().toShortString());
            if (!sa.openLine()) {
                return; // sin salida de sonido propia: que VLC suene como siempre
            }
            player.raw().mediaPlayer().audio().callback("S16N", RATE, CHANNELS, sa, true);
            BY_DISPLAY.put(display, sa);
            int range = Math.max(12, tile.data.maxVolumeDistance + 8);
            Vec3 c = Vec3.atCenterOf(tile.getBlockPos());
            Minecraft.getInstance().execute(() -> Show.addLive(sa.id, c, range, sa.analyzer::get));
        } catch (Throwable t) {
            StarFest.LOGGER.warn("StarFest: no se pudo tomar el audio de la pantalla: {}", t.toString());
        }
    }

    public static void detach(Object display) {
        ScreenAudio sa = BY_DISPLAY.remove(display);
        if (sa != null) {
            sa.close();
            Minecraft.getInstance().execute(() -> Show.removeLive(sa.id));
        }
    }

    private boolean openLine() {
        try {
            AudioFormat f = new AudioFormat(RATE, 16, CHANNELS, true, this.bigEndian);
            this.line = AudioSystem.getSourceDataLine(f);
            this.line.open(f, RATE * CHANNELS * 2 / 6); // ~160 ms
            this.line.start();
            return true;
        } catch (Throwable t) {
            StarFest.LOGGER.warn("StarFest: no hay salida de audio para las pantallas ({})", t.toString());
            return false;
        }
    }

    private void close() {
        SourceDataLine l = this.line;
        this.line = null;
        if (l != null) {
            try {
                l.stop();
                l.flush();
                l.close();
            } catch (Throwable ignored) {
                // ya cerrada
            }
        }
    }

    @Override
    public void play(MediaPlayer mp, Pointer samples, int sampleCount, long pts) {
        SourceDataLine l = this.line;
        if (l == null || sampleCount <= 0) {
            return;
        }
        int n = sampleCount * CHANNELS;
        if (this.bytes.length < n * 2) {
            this.bytes = new byte[n * 2];
            this.pcm = new short[n];
        }
        samples.read(0, this.bytes, 0, n * 2);
        for (int i = 0; i < n; i++) {
            int lo = this.bytes[i * 2] & 0xFF, hi = this.bytes[i * 2 + 1];
            this.pcm[i] = (short) (this.bigEndian ? (lo << 8 | this.bytes[i * 2 + 1] & 0xFF) : (hi << 8 | lo));
        }
        // la musica se analiza siempre (aunque esta pantalla este en silencio, sus luces siguen el video)
        long latency = (long) ((l.getBufferSize() - l.available()) / (double) (RATE * CHANNELS * 2) * 1e9);
        this.analyzer.feed(this.pcm, sampleCount, CHANNELS, latency);
        float g = this.muted ? 0 : this.gain;
        if (g != 1) {
            for (int i = 0; i < n; i++) {
                int v = Math.round(this.pcm[i] * g);
                v = Math.max(-32768, Math.min(32767, v));
                if (this.bigEndian) {
                    this.bytes[i * 2] = (byte) (v >> 8);
                    this.bytes[i * 2 + 1] = (byte) v;
                } else {
                    this.bytes[i * 2] = (byte) v;
                    this.bytes[i * 2 + 1] = (byte) (v >> 8);
                }
            }
        }
        l.write(this.bytes, 0, n * 2);
    }

    @Override
    public void pause(MediaPlayer mp, long pts) {
        SourceDataLine l = this.line;
        if (l != null) {
            l.stop();
        }
    }

    @Override
    public void resume(MediaPlayer mp, long pts) {
        SourceDataLine l = this.line;
        if (l != null) {
            l.start();
        }
    }

    @Override
    public void flush(MediaPlayer mp, long pts) {
        SourceDataLine l = this.line;
        if (l != null) {
            l.flush();
        }
    }

    @Override
    public void drain(MediaPlayer mp) {
        SourceDataLine l = this.line;
        if (l != null) {
            l.drain();
        }
    }

    @Override
    public void setVolume(float volume, boolean mute) {
        this.gain = Math.max(0, Math.min(2, volume));
        this.muted = mute;
    }
}
