package com.tunombre.taaef.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Patear puertas en tercera persona: el servidor de TAA comprueba que el jugador mire a la puerta trazando un rayo
 * desde sus ojos. En tercera persona (camara al hombro de Epic Arsenal) el cuerpo no apunta exactamente adonde
 * apunta la camara, el rayo fallaba y la puerta no se abria aunque la animacion si salia. Si la puerta esta delante
 * y cerca, se da por buena.
 */
@Pseudo
@Mixin(targets = "com.locknar.taczammocheck.tactical.server.ServerKickHandler", remap = false)
public abstract class KickServerMixin {
    @Inject(method = "isLookingAtDoor", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void taaef$look(ServerPlayer p, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        Vec3 eye = p.getEyePosition();
        Vec3 c = Vec3.atCenterOf(pos);
        Vec3 to = c.subtract(eye);
        double flat = Math.sqrt(to.x * to.x + to.z * to.z);
        if (flat > 3.2 || Math.abs(to.y) > 2.0) return;
        Vec3 look = Vec3.directionFromRotation(0, p.getYHeadRot());
        if (flat < 1.2 || (to.x * look.x + to.z * look.z) / Math.max(1e-4, flat) > 0.25) cir.setReturnValue(true);
    }
}
