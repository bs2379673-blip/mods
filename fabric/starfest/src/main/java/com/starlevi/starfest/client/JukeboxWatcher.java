package com.starlevi.starfest.client;

import com.starlevi.starfest.StarFest;
import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.sounds.SoundSource;

import java.io.InputStream;
import java.util.Optional;

/**
 * Tocadiscos: cuando empieza a sonar un disco (de Minecraft o de otro mod), se lee su .ogg de los
 * recursos y se analiza igual que las canciones del servidor; las luces cercanas lo siguen.
 */
public final class JukeboxWatcher {
    private JukeboxWatcher() {
    }

    /** Lo llama el mixin de SoundManager cada vez que empieza un sonido. */
    public static void onPlay(SoundInstance s) {
        if (s == null || s instanceof StreamSound || s.getSource() != SoundSource.RECORDS || s.isRelative()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Sound sound = s.getSound();
        if (sound == null) {
            s.resolve(mc.getSoundManager());
            sound = s.getSound();
        }
        if (sound == null || sound == net.minecraft.client.sounds.SoundManager.EMPTY_SOUND) {
            return;
        }
        Identifier file = sound.getPath();
        String name = s.getIdentifier().toString();
        Util.backgroundExecutor().execute(() -> {
            try {
                Optional<Resource> r = mc.getResourceManager().getResource(file);
                if (r.isEmpty()) {
                    return;
                }
                byte[] data;
                try (InputStream in = r.get().open()) {
                    data = in.readAllBytes();
                }
                mc.execute(() -> Show.addJukebox("disco:" + file, s, data, name));
            } catch (Exception ex) {
                StarFest.LOGGER.debug("StarFest: no se pudo leer el disco {}: {}", file, ex.toString());
            }
        });
    }
}
