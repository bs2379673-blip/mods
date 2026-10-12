package com.tunombre.taaef.mixin;

import com.tunombre.taaef.ClientDriver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Abrir la puerta con la mano (tecla F con arma): TAA solo lo dibuja en primera persona; aqui se avisa para que los
 *  demas (y tu en tercera persona) vean el empujon de la mano izquierda con Epic Fight. */
@Pseudo
@Mixin(targets = "com.locknar.taczammocheck.tactical.client.DoorOpenHandAnimationClient", remap = false)
public abstract class DoorHandClientMixin {
    @Inject(method = "trigger", at = @At("HEAD"), remap = false, require = 0)
    private static void taaef$trigger(CallbackInfo ci) { ClientDriver.localDoorPush(); }
}
