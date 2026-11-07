package com.starlevi.starfest.mixin;

import com.starlevi.starfest.client.JukeboxWatcher;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Tocadiscos: cuando empieza un disco, StarFest lee su .ogg para que las luces lo sigan. */
@Mixin(SoundManager.class)
public abstract class SoundManagerMixin {
    @Inject(method = "play", at = @At("HEAD"))
    private void starfest$play(SoundInstance sound, CallbackInfoReturnable<SoundEngine.PlayResult> cir) {
        try {
            JukeboxWatcher.onPlay(sound);
        } catch (Throwable ignored) {
            // nunca romper un sonido
        }
    }
}
