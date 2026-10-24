package com.tunombre.tacui;

import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import com.mojang.blaze3d.audio.OggAudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;

/**
 * Sonido .ogg que llega por red desde el servidor. Usa el evento "tacui:custom" (un sonido en streaming casi vacio de sounds.json)
 * solo para que Minecraft lo reproduzca, pero el audio real se lee de los bytes recibidos.
 */
final class CustomSound extends AbstractSoundInstance {
    private final byte[] data;

    CustomSound(byte[] data, float volume) { this(data, volume, false); }

    CustomSound(byte[] data, float volume, boolean loop) {
        super(new ResourceLocation(TacUI.ID, "custom"), SoundSource.MASTER, RandomSource.create());
        this.data = data;
        this.volume = volume;
        this.pitch = 1.0f;
        this.looping = loop;
        this.delay = 0;
        this.relative = true;
        this.attenuation = SoundInstance.Attenuation.NONE;
    }

    @Override
    public CompletableFuture<AudioStream> getStream(SoundBufferLibrary lib, Sound sound, boolean loop) {
        try {
            if (looping) return CompletableFuture.completedFuture(new net.minecraft.client.sounds.LoopingAudioStream(OggAudioStream::new, new ByteArrayInputStream(data)));
            return CompletableFuture.completedFuture(new OggAudioStream(new ByteArrayInputStream(data)));
        } catch (IOException e) {
            TacUI.LOGGER.warn("tacui: ogg propio no valido: {}", e.toString());
            return CompletableFuture.failedFuture(e);
        }
    }
}
