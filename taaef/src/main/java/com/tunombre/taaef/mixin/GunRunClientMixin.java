package com.tunombre.taaef.mixin;

import net.minecraftforge.client.event.RenderPlayerEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Correr con el arma de TAA inclina la matriz del jugador al girar/frenar. Con Epic Fight esa inclinacion se quedaba
 * pegada al modelo (el cuerpo torcido aunque ya estuvieras quieto). La animacion la pone ahora Epic Fight: se anula.
 */
@Pseudo
@Mixin(targets = "com.locknar.taczammocheck.tactical.client.GunRunAnimationClient", remap = false)
public abstract class GunRunClientMixin {
    @Inject(method = "onRenderPlayerPre", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void taaef$pre(RenderPlayerEvent.Pre e, CallbackInfo ci) { ci.cancel(); }

    @Inject(method = "onRenderPlayerPost", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void taaef$post(RenderPlayerEvent.Post e, CallbackInfo ci) { ci.cancel(); }
}
