package com.starlevi.starfest.client;

import com.starlevi.starfest.StarFest;
import net.fabricmc.fabric.api.client.sound.v1.FabricSoundInstance;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.JOrbisAudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

import javax.sound.sampled.AudioFormat;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;

/**
 * Cancion del servidor: el audio sale de los bytes recibidos (no de un paquete de recursos), con el
 * sonido vacio de Fabric para que el juego lo reproduzca. Puede empezar mas adelante (si entras a la
 * zona con la cancion ya empezada). Usa el volumen de "Tocadiscos/Bloques musicales".
 */
public final class StreamSound extends AbstractSoundInstance {
    private final byte[] data;
    private final float skipSeconds;

    public StreamSound(byte[] data, float volume, float skipSeconds) {
        super(FabricSoundInstance.EMPTY_SOUND, SoundSource.RECORDS, RandomSource.create());
        this.data = data;
        this.skipSeconds = skipSeconds;
        this.volume = volume;
        this.pitch = 1.0f;
        this.looping = false;
        this.delay = 0;
        this.relative = true;
        this.attenuation = SoundInstance.Attenuation.NONE;
    }

    @Override
    public CompletableFuture<AudioStream> getAudioStream(SoundBufferLibrary lib, Identifier id, boolean repeat) {
        try {
            return CompletableFuture.completedFuture(new Skip(new JOrbisAudioStream(new ByteArrayInputStream(this.data)), this.skipSeconds));
        } catch (IOException e) {
            StarFest.LOGGER.warn("StarFest: ogg no valido: {}", e.toString());
            return CompletableFuture.failedFuture(e);
        }
    }

    /** Saltea el principio de la cancion (decodifica y descarta). */
    private static final class Skip implements AudioStream {
        private final AudioStream in;
        private long skipBytes;
        private ByteBuffer pending;

        Skip(AudioStream in, float seconds) {
            this.in = in;
            AudioFormat f = in.getFormat();
            long frameBytes = (long) f.getChannels() * Math.max(1, f.getSampleSizeInBits() / 8);
            this.skipBytes = (long) Math.max(0, seconds * f.getSampleRate()) * frameBytes;
        }

        @Override
        public AudioFormat getFormat() {
            return this.in.getFormat();
        }

        @Override
        public ByteBuffer read(int size) throws IOException {
            while (this.skipBytes > 0) {
                ByteBuffer b = this.in.read(64 * 1024);
                if (b == null || !b.hasRemaining()) {
                    this.skipBytes = 0;
                    return b;
                }
                if (b.remaining() <= this.skipBytes) {
                    this.skipBytes -= b.remaining();
                } else {
                    b.position(b.position() + (int) this.skipBytes);
                    this.skipBytes = 0;
                    this.pending = b;
                }
            }
            if (this.pending != null) {
                ByteBuffer p = this.pending;
                this.pending = null;
                if (p.hasRemaining()) {
                    // el juego espera un buffer directo
                    ByteBuffer d = org.lwjgl.BufferUtils.createByteBuffer(p.remaining());
                    d.put(p).flip();
                    return d;
                }
            }
            return this.in.read(size);
        }

        @Override
        public void close() throws IOException {
            this.in.close();
        }
    }
}
