package com.starlevi.starframes.mixin;

import com.starlevi.starframes.client.AudioTap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.watermedia.api.player.SFXEngine;

import java.nio.ByteBuffer;

/** Copia (solo lee) el sonido que WaterMedia manda a los parlantes, para que otros mods (StarFest) lo analicen. */
@Mixin(value = SFXEngine.class, remap = false)
public abstract class SFXEngineMixin {
    @Inject(method = "upload", at = @At("HEAD"), require = 0)
    private void starframes$tap(ByteBuffer data, CallbackInfoReturnable<Boolean> cir) {
        AudioTap.feed((SFXEngine) (Object) this, data);
    }
}
