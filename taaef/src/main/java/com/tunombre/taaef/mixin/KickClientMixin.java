package com.tunombre.taaef.mixin;

import com.tunombre.taaef.ClientDriver;
import net.minecraftforge.client.event.RenderPlayerEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Patada de TAA: (1) la nuestra empieza en el mismo instante que la suya (antes habia un fotograma sin Epic Fight);
 * (2) TAA gira la matriz del jugador para la patada hacia atras, y como Epic Fight dibuja con esa misma matriz el
 * cuerpo salia torcido. Con Epic Fight eso sobra: se anula.
 */
@Pseudo
@Mixin(targets = "com.locknar.taczammocheck.tactical.client.KickAnimationClient", remap = false)
public abstract class KickClientMixin {
    @Inject(method = "startForEntity(II)V", at = @At("HEAD"), remap = false, require = 0)
    private static void taaef$kick(int entityId, int mode, CallbackInfo ci) { ClientDriver.kickStarted(entityId); }

    @Inject(method = "onRenderPlayerPre", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void taaef$pre(RenderPlayerEvent.Pre e, CallbackInfo ci) { ci.cancel(); }

    @Inject(method = "onRenderPlayerPost", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void taaef$post(RenderPlayerEvent.Post e, CallbackInfo ci) { ci.cancel(); }
}
