package com.tunombre.taaef.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Cuerpo a tierra de TAA: tumba y desplaza la matriz del jugador para su modelo de vainilla. Con Epic Fight eso se
 * sumaba a nuestra animacion (que ya lo tumba) y el cuerpo quedaba girado, flotando y sin tocar el suelo.
 */
@Pseudo
@Mixin(targets = "com.locknar.taczammocheck.tactical.client.ProneTacticalAnimationClient", remap = false)
public abstract class ProneClientMixin {
    @Inject(method = "applyLockedStationaryProneRotations", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void taaef$rot(AbstractClientPlayer p, PoseStack ps, float pt, CallbackInfoReturnable<Boolean> cir) { cir.setReturnValue(false); }

    @Inject(method = "beginPlayerRendererRender", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void taaef$begin(AbstractClientPlayer p, float pt, PoseStack ps, CallbackInfo ci) { ci.cancel(); }

    @Inject(method = "endPlayerRendererRender", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void taaef$end(AbstractClientPlayer p, CallbackInfo ci) { ci.cancel(); }
}
