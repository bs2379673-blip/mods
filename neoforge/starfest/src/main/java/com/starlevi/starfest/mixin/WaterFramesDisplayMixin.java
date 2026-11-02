package com.starlevi.starfest.mixin;

import com.starlevi.starfest.client.live.ScreenAudio;
import me.srrapero720.waterframes.common.block.entity.DisplayTile;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.watermedia.api.player.videolan.VideoPlayer;

/**
 * WaterFrames: justo antes de que una pantalla arranque su video, StarFest toma su audio (para que
 * las luces lo sigan). Si WaterFrames no esta instalado, esto no hace nada.
 */
@Pseudo
@Mixin(targets = "me.srrapero720.waterframes.client.display.Display", remap = false)
public abstract class WaterFramesDisplayMixin {
    @Shadow(remap = false)
    private VideoPlayer mediaPlayer;

    @Shadow(remap = false)
    @Final
    private DisplayTile tile;

    @Inject(method = "<init>", at = @At(value = "INVOKE", target = "Lorg/watermedia/api/player/videolan/VideoPlayer;start(Ljava/net/URI;)V"),
            remap = false, require = 0)
    private void starfest$tapAudio(DisplayTile t, CallbackInfo ci) {
        ScreenAudio.attach(this, this.mediaPlayer, t);
    }

    @Inject(method = "release", at = @At("HEAD"), remap = false, require = 0)
    private void starfest$release(CallbackInfo ci) {
        ScreenAudio.detach(this);
    }
}
